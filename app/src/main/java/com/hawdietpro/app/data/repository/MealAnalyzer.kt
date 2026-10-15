package com.hawdietpro.app.data.repository

import com.hawdietpro.app.core.ImageProcessor
import com.hawdietpro.app.data.gemini.GeminiClient
import com.hawdietpro.app.data.gemini.GeminiFoodResult
import com.hawdietpro.app.data.remote.RemoteConfigStore
import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.NutritionSource
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.nutrition.FoodMatcher
import com.hawdietpro.app.domain.nutrition.NutritionCalculator
import com.hawdietpro.app.domain.scoring.MealScorer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Menilai makan (grade, label, saran) memakai aturan remote terbaru & target pengguna. */
@Singleton
class MealEvaluator @Inject constructor(
    private val store: RemoteConfigStore,
    private val profiles: ProfileRepository,
) {
    suspend fun evaluate(meal: Meal): Meal = evaluate(meal, profiles.getTarget()?.calories)

    fun evaluate(meal: Meal, targetCalories: Int?): Meal {
        if (meal.items.isEmpty()) return meal.copy(grade = null, assessments = emptyList(), suggestions = emptyList())
        val analysis = MealScorer(store.insightRules.value).analyze(meal.items, meal.mealType, targetCalories)
        return meal.copy(grade = analysis.grade, assessments = analysis.labels, suggestions = analysis.suggestions)
    }
}

/**
 * Alur inti foto → hasil: kirim foto ke Gemini, petakan elemen, validasi silang dengan
 * database nutrisi lokal (gram × nilai per 100 g), lalu beri penilaian gizi.
 */
@Singleton
class MealAnalyzer @Inject constructor(
    private val gemini: GeminiClient,
    private val foods: FoodRepository,
    private val evaluator: MealEvaluator,
    private val images: ImageProcessor,
    private val store: RemoteConfigStore,
) {
    suspend fun analyze(photoPath: String, mealType: MealType, eatenAt: Long, userHint: String?): Meal {
        val jpeg = images.readBytes(photoPath)
        val matcher = foods.matcher()
        val time = Instant.ofEpochMilli(eatenAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH.mm"))
        val result = gemini.analyzeFood(
            jpeg,
            GeminiClient.PromptVars(
                mealType = mealType.label,
                time = time,
                userHint = userHint,
                // Kombinasi otomatis (PERKIRAAN) tidak dikirim agar prompt tetap ringkas; matcher tetap mengenalinya.
                referenceNames = foods.all().filter { it.source != "USER" && it.source != "PERKIRAAN" }.map { it.name },
            ),
        )
        val meal = Meal(
            eatenAt = eatenAt,
            mealType = mealType,
            menuName = result.menuName.ifBlank { "Makanan" },
            photoPath = photoPath,
            confidence = result.confidence.coerceIn(0.0, 1.0),
            items = toItems(result, matcher),
            aiNotes = result.notes?.takeIf { it.isNotBlank() },
        )
        return evaluator.evaluate(meal)
    }

    /** Diekspos agar bisa dipakai ulang (mis. saat mengganti nama elemen). */
    fun reconcile(item: FoodItem, matcher: FoodMatcher): FoodItem {
        val match = matcher.match(item.name, item.cookingMethod) ?: return item
        return NutritionCalculator.applyReference(item, match.reference)
    }

    private fun toItems(result: GeminiFoodResult, matcher: FoodMatcher): List<FoodItem> =
        result.elements
            .filter { it.name.isNotBlank() && it.grams > 0 }
            .map { e ->
                val ai = Nutrients(e.calories, e.protein, e.carbs, e.fat, e.fiber, e.sugar.coerceAtLeast(0.0), e.sodium.coerceAtLeast(0.0))
                val item = FoodItem(
                    name = e.name.trim().replaceFirstChar { it.uppercase() },
                    grams = e.grams,
                    nutrients = ai,
                    cookingMethod = e.cookingMethod?.trim()?.lowercase()?.takeIf { it.isNotBlank() },
                    source = NutritionSource.AI,
                    aiNutrients = ai,
                )
                reconcile(item, matcher)
            }

    fun maxImageSize(): Int = store.geminiPrompt.value.maxImageSize
    fun jpegQuality(): Int = store.geminiPrompt.value.jpegQuality
}
