package com.hawdietpro.app.domain.scoring

import com.hawdietpro.app.domain.config.InsightRulesFile
import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.Grade
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.sum
import kotlin.math.roundToInt

data class MealAnalysis(
    val score: Int,
    val grade: Grade,
    /** Label penilaian, contoh: "Tinggi karbo", "Kurang sayur", atau "Seimbang". */
    val labels: List<String>,
    val suggestions: List<String>,
    val metrics: Map<String, Double>,
)

/**
 * Penilaian per foto: skor 0–100 → grade A–E, label & saran.
 * Seluruh ambang batas berasal dari insight_rules.json (bisa diubah lewat remote config).
 */
class MealScorer(private val rules: InsightRulesFile) {

    private val classifier = FoodClassifier(rules)

    fun metrics(items: List<FoodItem>, dailyTargetCalories: Int?): Map<String, Double> {
        val total = items.map { it.nutrients }.sum()
        val split = total.macroEnergy
        return mapOf(
            "calories" to total.calories,
            "proteinG" to total.protein,
            "carbsG" to total.carbs,
            "fatG" to total.fat,
            "fiberG" to total.fiber,
            "sugarG" to total.sugar,
            "sodiumMg" to total.sodium,
            "proteinPct" to split.proteinPct,
            "carbsPct" to split.carbsPct,
            "fatPct" to split.fatPct,
            "vegGrams" to items.filter(classifier::isVegetable).sumOf { it.grams },
            "friedCount" to items.count(classifier::isFried).toDouble(),
            "santanCount" to items.count(classifier::isSantan).toDouble(),
            "sweetDrinkCount" to items.count(classifier::isSweetDrink).toDouble(),
            "itemCount" to items.size.toDouble(),
            "targetShare" to if (dailyTargetCalories != null && dailyTargetCalories > 0) {
                total.calories / dailyTargetCalories * 100
            } else 0.0,
        )
    }

    fun analyze(items: List<FoodItem>, mealType: MealType, dailyTargetCalories: Int?): MealAnalysis {
        val config = rules.mealScoring
        val m = metrics(items, dailyTargetCalories)
        val calories = m["calories"] ?: 0.0

        var score = config.base
        val negatives = mutableListOf<Pair<String, String?>>()
        val positives = mutableListOf<String>()

        for (rule in config.rules) {
            if (rule.mealTypes.isNotEmpty() && mealType.name !in rule.mealTypes) continue
            if (rule.minCalories != null && calories < rule.minCalories) continue
            val actual = m[rule.metric] ?: continue
            if (!RuleOps.evaluate(actual, rule.op, rule.value)) continue
            score += rule.points
            if (rule.points < 0) negatives += rule.label to rule.suggestion else positives += rule.label
        }

        val finalScore = score.coerceIn(0.0, 100.0).roundToInt()
        val grade = config.grades
            .sortedByDescending { it.min }
            .firstOrNull { finalScore >= it.min }
            ?.let { Grade.parse(it.grade) }
            ?: Grade.E

        val labels = if (negatives.isEmpty()) listOf(config.balancedLabel) + positives else negatives.map { it.first } + positives
        val suggestions = negatives.mapNotNull { it.second }.distinct().take(config.maxSuggestions)

        return MealAnalysis(finalScore, grade, labels.distinct(), suggestions, m)
    }
}
