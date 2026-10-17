package com.hawdietpro.app.domain.exercise

import com.hawdietpro.app.domain.TestFixtures
import com.hawdietpro.app.domain.insight.PeriodStatsCalculator
import com.hawdietpro.app.domain.model.ActivityLevel
import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.Goal
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.model.Sex
import com.hawdietpro.app.domain.model.UserProfile
import com.hawdietpro.app.domain.nutrition.TdeeCalculator
import com.hawdietpro.app.domain.nutrition.WeightProjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ExerciseTest {

    @Test
    fun `estimasi kalori memakai MET dikurangi energi istirahat`() {
        // Lari santai 8,3 MET, 70 kg, 30 menit → (8,3 − 1) × 70 × 0,5 = 255,5 kkal
        assertEquals(255.5, ExerciseCatalog.estimate(8.3, 70.0, 30), 0.01)
        assertEquals(0.0, ExerciseCatalog.estimate(0.8, 70.0, 30), 0.0)
        assertEquals(0.0, ExerciseCatalog.estimate(5.0, 70.0, -10), 0.0)
    }

    @Test
    fun `katalog punya id unik dan MET wajar`() {
        val ids = ExerciseCatalog.activities.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ExerciseCatalog.activities.all { it.met in 2.0..12.0 })
        assertNotNull(ExerciseCatalog.find("futsal"))
    }

    @Test
    fun `olahraga menambah defisit pada perkiraan berat`() {
        val profile = UserProfile("Uji", 30, Sex.PRIA, 170.0, 80.0, ActivityLevel.SEDENTARI, Goal.TURUN, 75.0)
        val start = LocalDate.of(2026, 1, 1)
        val tdee = TdeeCalculator.bmr(Sex.PRIA, 80.0, 170.0, 30) * 1.2
        val intake = mapOf(start to tdee)
        val without = WeightProjector.project(profile, start, 80.0, start.plusDays(1), intake, emptyList())
        val with = WeightProjector.project(profile, start, 80.0, start.plusDays(1), intake, emptyList(), mapOf(start to 770.0))
        assertEquals(80.0, without.estimatedToday, 0.001)
        assertEquals(79.9, with.estimatedToday, 0.001)
        assertEquals(770.0, with.days.first().exercise, 0.0)
    }

    @Test
    fun `target harian dibandingkan dengan kalori bersih`() {
        val day = LocalDate.of(2026, 1, 1)
        val meal = Meal(
            eatenAt = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            mealType = MealType.MAKAN_SIANG, menuName = "Nasi", photoPath = null, confidence = null,
            items = listOf(FoodItem(name = "nasi", grams = 100.0, nutrients = Nutrients(2400.0, 50.0, 300.0, 80.0, 5.0))),
        )
        val calc = PeriodStatsCalculator(TestFixtures.insightRules, ZoneOffset.UTC)
        assertEquals(1, calc.compute(listOf(meal), day, day, 2000).daysOver)
        val withExercise = calc.compute(listOf(meal), day, day, 2000, exerciseByDay = mapOf(day to 400.0))
        assertEquals(0, withExercise.daysOver)
        assertEquals(1, withExercise.daysOnTarget)
        assertEquals(2000.0, withExercise.days.first().netCalories, 0.0)
        assertEquals(400.0, withExercise.metrics["totalExerciseKcal"]!!, 0.0)
    }
}
