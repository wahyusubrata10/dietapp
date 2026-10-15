package com.hawdietpro.app.domain.achievement

import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.Goal
import com.hawdietpro.app.domain.model.Grade
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.model.WeightEntry
import com.hawdietpro.app.domain.nutrition.DailyTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AchievementCalculatorTest {

    private val zone = ZoneOffset.UTC
    private val calc = AchievementCalculator(isVegetable = { it.name.contains("sayur") }, zone = zone)
    private val start = LocalDate.of(2026, 1, 1)

    private val target = DailyTarget(
        bmr = 1500, tdee = 2000, calories = 1800, proteinG = 90, carbsG = 200, fatG = 60, fiberG = 25,
        adjustment = -200, bmi = 24.0, bmiCategory = "Normal", weeklyChangeKg = -0.2, weeksToTarget = null, warnings = emptyList(),
    )

    private fun meal(
        day: LocalDate,
        type: MealType = MealType.MAKAN_SIANG,
        kcal: Double = 600.0,
        protein: Double = 30.0,
        name: String = "nasi",
        photo: String? = null,
        grade: Grade? = null,
        sodium: Double = 500.0,
    ) = Meal(
        eatenAt = day.atTime(12, 0).toInstant(zone).toEpochMilli(),
        mealType = type,
        menuName = name,
        photoPath = photo,
        confidence = null,
        items = listOf(FoodItem(name = name, grams = 100.0, nutrients = Nutrients(kcal, protein, 50.0, 10.0, 3.0, 5.0, sodium))),
        grade = grade,
    )

    private fun List<Achievement>.find(id: String) = first { it.id == id }

    @Test
    fun `empty history unlocks nothing`() {
        val result = calc.compute(emptyList(), emptyList(), target, Goal.TURUN)
        assertTrue(result.none { it.unlocked })
    }

    @Test
    fun `streak uses longest run even if broken later`() {
        val meals = (0L until 7L).map { meal(start.plusDays(it)) } + meal(start.plusDays(10))
        val result = calc.compute(meals, emptyList(), null, null)
        assertTrue(result.find("streak_7").unlocked)
        assertFalse(result.find("streak_14").unlocked)
        assertEquals(7.0, result.find("streak_14").progress, 0.0)
        assertTrue(result.find("first_meal").unlocked)
    }

    @Test
    fun `photo, grade, breakfast and veg counters`() {
        val meals = (0L until 10L).map { meal(start.plusDays(it), photo = "p$it.jpg", grade = if (it < 5) Grade.A else Grade.C) } +
            (0L until 7L).map { meal(start.plusDays(it), type = MealType.SARAPAN, name = "sayur bayam") }
        val result = calc.compute(meals, emptyList(), null, null)
        assertTrue(result.find("photo_10").unlocked)
        assertTrue(result.find("grade_a_5").unlocked)
        assertTrue(result.find("breakfast_7").unlocked)
        assertTrue(result.find("veg_7").unlocked)
    }

    @Test
    fun `target-based badges need full days within tolerance`() {
        val meals = (0L until 7L).flatMap { d ->
            listOf(meal(start.plusDays(d), kcal = 900.0, protein = 45.0), meal(start.plusDays(d), type = MealType.MAKAN_MALAM, kcal = 900.0, protein = 45.0))
        }
        val result = calc.compute(meals, emptyList(), target, Goal.TURUN)
        assertTrue(result.find("on_target_7").unlocked)
        assertTrue(result.find("protein_7").unlocked)
        assertTrue(result.find("balanced_7").unlocked)

        val noTarget = calc.compute(meals, emptyList(), null, null)
        assertNull(noTarget.firstOrNull { it.id == "on_target_7" })
    }

    @Test
    fun `salty days do not count as balanced`() {
        val meals = (0L until 7L).flatMap { d ->
            listOf(meal(start.plusDays(d), sodium = 1500.0), meal(start.plusDays(d), type = MealType.MAKAN_MALAM, sodium = 1500.0))
        }
        assertEquals(0.0, calc.compute(meals, emptyList(), target, Goal.TURUN).find("balanced_7").progress, 0.0)
    }

    @Test
    fun `weight badges follow goal direction`() {
        val weights = listOf(
            WeightEntry(epochDay = start.toEpochDay(), weightKg = 80.0),
            WeightEntry(epochDay = start.plusDays(30).toEpochDay(), weightKg = 76.5),
        )
        val losing = calc.compute(emptyList(), weights, target, Goal.TURUN)
        assertTrue(losing.find("weight_1").unlocked)
        assertTrue(losing.find("weight_3").unlocked)
        assertFalse(losing.find("weight_5").unlocked)

        val gaining = calc.compute(emptyList(), weights, target, Goal.NAIK)
        assertFalse(gaining.find("weight_1").unlocked)

        val maintaining = calc.compute(emptyList(), weights, target, Goal.JAGA)
        assertNull(maintaining.firstOrNull { it.id == "weight_1" })
    }
}
