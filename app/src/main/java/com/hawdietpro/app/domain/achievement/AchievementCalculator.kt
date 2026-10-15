package com.hawdietpro.app.domain.achievement

import com.hawdietpro.app.domain.model.Goal
import com.hawdietpro.app.domain.model.Grade
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.model.WeightEntry
import com.hawdietpro.app.domain.model.sum
import com.hawdietpro.app.domain.nutrition.DailyTarget
import com.hawdietpro.app.domain.nutrition.NutritionLimits
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

enum class AchievementGroup(val label: String) {
    KONSISTENSI("Konsistensi"),
    CATATAN("Catatan"),
    GIZI("Pola makan"),
    BERAT("Berat badan"),
}

data class Achievement(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val group: AchievementGroup,
    val progress: Double,
    val goal: Double,
    /** Satuan untuk teks progres ("hari", "kg", ...). */
    val unit: String,
) {
    val unlocked: Boolean get() = progress >= goal
    val fraction: Float get() = if (goal <= 0) 1f else (progress / goal).coerceIn(0.0, 1.0).toFloat()
}

/**
 * Menghitung badge dari seluruh riwayat. Semua badge diturunkan dari data (tidak disimpan),
 * sehingga tetap konsisten setelah restore backup. Streak memakai rekor terpanjang agar badge
 * tidak hilang saat streak putus.
 */
class AchievementCalculator(
    private val isVegetable: (com.hawdietpro.app.domain.model.FoodItem) -> Boolean = { false },
    private val targetTolerancePct: Double = 10.0,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    fun compute(
        meals: List<Meal>,
        weights: List<WeightEntry>,
        target: DailyTarget?,
        goal: Goal?,
    ): List<Achievement> {
        val byDay = meals.groupBy { Instant.ofEpochMilli(it.eatenAt).atZone(zone).toLocalDate() }
        val dayTotals: Map<LocalDate, Nutrients> = byDay.mapValues { (_, list) -> list.map { it.total }.sum() }
        val bestStreak = longestStreak(byDay.keys)
        val photos = meals.count { !it.photoPath.isNullOrBlank() }
        val gradeA = meals.count { it.grade == Grade.A }
        val breakfastDays = byDay.count { (_, list) -> list.any { it.mealType == MealType.SARAPAN } }
        val vegDays = byDay.count { (_, list) -> list.flatMap { it.items }.any(isVegetable) }
        // Hari "lengkap": minimal 2 kali makan, agar hari yang baru dicatat sebagian tidak terhitung.
        val fullDays = byDay.filterValues { it.size >= 2 }.keys
        val balancedDays = fullDays.count { d ->
            val n = dayTotals.getValue(d)
            n.sugar <= NutritionLimits.SUGAR_G && n.sodium <= NutritionLimits.SODIUM_MG
        }
        val proteinDays = target?.let { t -> dayTotals.count { (_, n) -> n.protein >= t.proteinG } } ?: 0
        val onTargetDays = target?.takeIf { it.calories > 0 }?.let { t ->
            val tol = targetTolerancePct / 100.0
            fullDays.count { d -> abs(dayTotals.getValue(d).calories - t.calories) <= t.calories * tol }
        } ?: 0

        val sortedWeights = weights.sortedBy { it.epochDay }
        val gaining = goal == Goal.NAIK
        val weightProgress = if (sortedWeights.size >= 2) {
            val delta = sortedWeights.last().weightKg - sortedWeights.first().weightKg
            (if (gaining) delta else -delta).coerceAtLeast(0.0)
        } else 0.0
        val verb = if (gaining) "Naik" else "Turun"

        return buildList {
            add(Achievement("first_meal", "🍽️", "Langkah pertama", "Catat makanan pertamamu", AchievementGroup.CATATAN, meals.size.toDouble().coerceAtMost(1.0), 1.0, "catatan"))
            add(Achievement("meals_50", "📒", "Pencatat rajin", "Catat 50 kali makan", AchievementGroup.CATATAN, meals.size.toDouble(), 50.0, "catatan"))
            add(Achievement("meals_200", "📚", "Jurnal lengkap", "Catat 200 kali makan", AchievementGroup.CATATAN, meals.size.toDouble(), 200.0, "catatan"))
            add(Achievement("photo_10", "📸", "Fotografer makanan", "Analisa 10 foto makanan", AchievementGroup.CATATAN, photos.toDouble(), 10.0, "foto"))
            add(Achievement("photo_50", "🎞️", "Galeri gizi", "Analisa 50 foto makanan", AchievementGroup.CATATAN, photos.toDouble(), 50.0, "foto"))

            STREAKS.forEach { (n, emoji, title) ->
                add(Achievement("streak_$n", emoji, title, "Mencatat $n hari berturut-turut", AchievementGroup.KONSISTENSI, bestStreak.toDouble(), n.toDouble(), "hari"))
            }
            add(Achievement("breakfast_7", "🌅", "Sarapan juara", "Sarapan tercatat di 7 hari berbeda", AchievementGroup.KONSISTENSI, breakfastDays.toDouble(), 7.0, "hari"))

            add(Achievement("grade_a_5", "🅰️", "Piring nilai A", "Dapatkan nilai A pada 5 kali makan", AchievementGroup.GIZI, gradeA.toDouble(), 5.0, "makan"))
            add(Achievement("veg_7", "🥦", "Sahabat sayur", "Ada sayur di 7 hari berbeda", AchievementGroup.GIZI, vegDays.toDouble(), 7.0, "hari"))
            if (target != null) {
                add(Achievement("protein_7", "💪", "Protein cukup", "Target protein tercapai di 7 hari", AchievementGroup.GIZI, proteinDays.toDouble(), 7.0, "hari"))
                add(Achievement("on_target_7", "🎯", "Tepat sasaran", "Kalori sesuai target (±${targetTolerancePct.toInt()}%) di 7 hari", AchievementGroup.GIZI, onTargetDays.toDouble(), 7.0, "hari"))
            }
            add(Achievement("balanced_7", "🧂", "Gula & garam terkendali", "Gula ≤ ${NutritionLimits.SUGAR_G.toInt()} g dan natrium ≤ ${NutritionLimits.SODIUM_MG.toInt()} mg di 7 hari", AchievementGroup.GIZI, balancedDays.toDouble(), 7.0, "hari"))

            add(Achievement("weigh_8", "⚖️", "Rajin menimbang", "Catat berat badan 8 kali", AchievementGroup.BERAT, weights.size.toDouble(), 8.0, "kali"))
            if (goal != Goal.JAGA) {
                WEIGHT_STEPS.forEach { (kg, emoji) ->
                    add(Achievement("weight_${kg.toInt()}", emoji, "$verb ${kg.toInt()} kg", "$verb ${kg.toInt()} kg dari catatan berat pertama", AchievementGroup.BERAT, weightProgress, kg, "kg"))
                }
            }
        }
    }

    private companion object {
        val STREAKS = listOf(
            Triple(3, "🔥", "Mulai panas"),
            Triple(7, "⭐", "Seminggu penuh"),
            Triple(14, "🌟", "Dua minggu konsisten"),
            Triple(30, "🏆", "Sebulan tanpa absen"),
        )
        val WEIGHT_STEPS = listOf(1.0 to "🥉", 3.0 to "🥈", 5.0 to "🥇")

        fun longestStreak(days: Set<LocalDate>): Int {
            var best = 0
            var run = 0
            var prev: LocalDate? = null
            days.sortedBy { it.toEpochDay() }.forEach { d ->
                run = if (prev != null && prev!!.plusDays(1) == d) run + 1 else 1
                best = maxOf(best, run)
                prev = d
            }
            return best
        }
    }
}
