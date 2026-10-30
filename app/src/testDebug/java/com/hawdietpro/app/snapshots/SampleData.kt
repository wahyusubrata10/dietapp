package com.hawdietpro.app.snapshots

import com.hawdietpro.app.domain.insight.Insight
import com.hawdietpro.app.domain.model.ActivityLevel
import com.hawdietpro.app.domain.model.Exercise
import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.Goal
import com.hawdietpro.app.domain.model.Grade
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.model.Sex
import com.hawdietpro.app.domain.model.UserProfile
import com.hawdietpro.app.domain.model.WeightEntry
import com.hawdietpro.app.domain.model.sum
import com.hawdietpro.app.domain.nutrition.AdaptiveTdeeEstimator
import com.hawdietpro.app.domain.nutrition.TdeeCalculator
import com.hawdietpro.app.domain.nutrition.WeightProjector
import com.hawdietpro.app.ui.feature.home.DayChip
import com.hawdietpro.app.ui.feature.home.HomeState
import java.time.LocalDate
import java.time.ZoneId

/** Data contoh realistis untuk snapshot tampilan. */
object SampleData {
    private val today: LocalDate = LocalDate.now()
    private val zone = ZoneId.systemDefault()
    private fun at(hour: Int, minute: Int = 0) = today.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    val profile = UserProfile("Wahyu Subrata", 30, Sex.PRIA, 175.0, 87.6, ActivityLevel.SEDENTARI, Goal.TURUN, 80.0)

    private fun item(name: String, g: Double, kcal: Double, p: Double, c: Double, f: Double, fiber: Double = 1.0) =
        FoodItem(name = name, grams = g, nutrients = Nutrients(kcal, p, c, f, fiber, 4.0, 300.0))

    val meals = listOf(
        Meal(
            id = 1, eatenAt = at(7, 30), mealType = MealType.SARAPAN, menuName = "Nasi uduk telur balado", photoPath = null, confidence = 0.8,
            items = listOf(item("Nasi uduk", 150.0, 280.0, 5.0, 52.0, 6.0), item("Telur balado", 60.0, 120.0, 8.0, 3.0, 8.0)),
            grade = Grade.C,
        ),
        Meal(
            id = 2, eatenAt = at(12, 40), mealType = MealType.MAKAN_SIANG, menuName = "Ayam suwir bumbu Bali + sayur", photoPath = null, confidence = 0.86,
            items = listOf(item("Nasi putih", 150.0, 195.0, 4.0, 43.0, 0.5), item("Ayam suwir bumbu Bali", 100.0, 210.0, 24.0, 5.0, 10.0), item("Tumis kangkung", 80.0, 60.0, 2.5, 4.0, 4.0, 2.5)),
            grade = Grade.B,
        ),
        Meal(
            id = 3, eatenAt = at(16, 5), mealType = MealType.CAMILAN, menuName = "Pisang & kopi susu", photoPath = null, confidence = 0.9,
            items = listOf(item("Pisang ambon", 100.0, 92.0, 1.0, 23.0, 0.3, 2.6), item("Kopi susu gula aren", 200.0, 150.0, 3.0, 24.0, 4.0)),
            grade = Grade.D,
        ),
    )

    private val exercises = listOf(
        Exercise(id = 1, epochDay = today.toEpochDay(), performedAt = at(6), activityId = "jalan_cepat", name = "Jalan cepat", durationMin = 40, calories = 230.0, source = Exercise.SOURCE_ESTIMATE),
        Exercise(id = 2, epochDay = today.toEpochDay(), performedAt = at(18), activityId = "sepeda_santai", name = "Sepeda santai", durationMin = 30, calories = 170.0, source = Exercise.SOURCE_ESTIMATE),
    )

    private val start = today.minusDays(17)
    private val weights = listOf(
        WeightEntry(1, start.toEpochDay(), 90.0),
        WeightEntry(2, start.plusDays(6).toEpochDay(), 88.9),
        WeightEntry(3, start.plusDays(11).toEpochDay(), 87.9),
        WeightEntry(4, start.plusDays(16).toEpochDay(), 87.6),
    )
    private val intake = (0 until 17).associate { start.plusDays(it.toLong()) to (1250.0 + (it % 4) * 120) }
    private val burned = (0 until 17 step 2).associate { start.plusDays(it.toLong()) to 250.0 }

    fun home(): HomeState {
        val adaptive = AdaptiveTdeeEstimator.estimate(profile, today, intake, weights, burned)
        val target = TdeeCalculator.calculate(profile, adaptive.tdee)
        val projection = WeightProjector.project(profile, start, 90.0, today, intake, weights, burned, adaptive.multiplier)
        val days = (0 until 14).map { i ->
            val d = today.minusDays((13 - i).toLong())
            DayChip(d, if (d == today) meals.sumOf { it.total.calories } else intake[d] ?: 0.0, burned[d] ?: 0.0)
        }
        return HomeState(
            loading = false,
            date = today,
            isToday = true,
            name = profile.name,
            target = target,
            meals = meals,
            total = meals.map { it.total }.sum(),
            byType = meals.groupBy { it.mealType },
            insights = listOf(
                Insight("protein", "Protein hari ini baru 55% target. Tambah lauk tinggi protein seperti tempe, ikan, atau dada ayam.", "warning", 1),
                Insight("streak", "Mantap! Kamu mencatat 17 hari berturut-turut.", "positive", 2),
            ),
            pendingCount = 1,
            tip = "Minum segelas air sebelum makan membantu merasa kenyang lebih cepat.",
            hasApiKey = true,
            projection = projection,
            days = days,
            previousDayByType = mapOf(MealType.MAKAN_MALAM to listOf(meals[1].copy(menuName = "Pecel lele + lalapan"))),
            exercises = exercises,
            adaptive = adaptive,
            streak = 17,
        )
    }

    fun homeEmpty(): HomeState {
        val target = TdeeCalculator.calculate(profile)
        val days = (0 until 14).map { i -> DayChip(today.minusDays((13 - i).toLong()), 0.0) }
        return HomeState(
            loading = false,
            date = today,
            isToday = true,
            name = profile.name,
            target = target,
            hasApiKey = false,
            days = days,
            adaptive = AdaptiveTdeeEstimator.estimate(profile, today, emptyMap(), emptyList()),
        )
    }
}
