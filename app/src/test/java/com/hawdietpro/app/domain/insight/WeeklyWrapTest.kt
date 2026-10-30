package com.hawdietpro.app.domain.insight

import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.Grade
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.model.WeightEntry
import com.hawdietpro.app.domain.nutrition.DayStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class WeeklyWrapTest {
    private val zone = ZoneOffset.UTC
    private val monday = LocalDate.of(2026, 10, 19)

    private fun meal(day: LocalDate, name: String, kcal: Double, protein: Double = 20.0, grade: Grade? = null, photo: String? = null) = Meal(
        eatenAt = day.atTime(12, 0).toInstant(zone).toEpochMilli(),
        mealType = MealType.MAKAN_SIANG,
        menuName = name,
        photoPath = photo,
        confidence = null,
        items = listOf(FoodItem(name = name, grams = 100.0, nutrients = Nutrients(kcal, protein, 10.0, 5.0, 3.0))),
        grade = grade,
    )

    @Test
    fun `minggu lalu dimulai senin sebelumnya`() {
        assertEquals(LocalDate.of(2026, 10, 19), WeeklyWrapCalculator.lastWeekStart(LocalDate.of(2026, 10, 30)))
        assertEquals(LocalDate.of(2026, 10, 19), WeeklyWrapCalculator.lastWeekStart(LocalDate.of(2026, 10, 26)))
    }

    @Test
    fun `rekap menghitung defisit, status, menu favorit, berat`() {
        val meals = listOf(
            meal(monday, "Nasi pecel", 1500.0, grade = Grade.B, photo = "a.jpg"),
            meal(monday.plusDays(1), "Nasi pecel", 2600.0, grade = Grade.D),
            meal(monday.plusDays(2), "Soto ayam", 1900.0, grade = Grade.A, photo = "b.jpg"),
            // Di luar minggu: diabaikan.
            meal(monday.plusDays(7), "Bakso", 3000.0),
        )
        val weights = listOf(WeightEntry(1, monday.toEpochDay(), 80.0), WeightEntry(2, monday.plusDays(6).toEpochDay(), 79.4))
        val w = WeeklyWrapCalculator.compute(
            monday, meals, budgetFor = { 2000 }, tdee = 2500, exerciseByDay = mapOf(monday to 200.0), weights = weights, proteinTarget = 120, zone = zone,
        )
        assertEquals(3, w.daysLogged)
        assertEquals(3, w.mealCount)
        assertEquals(2, w.photoCount)
        assertEquals(6000, w.totalIntake)
        // (1500−2500−200) + (2600−2500) + (1900−2500) = −1700
        assertEquals(-1700, w.energyBalance)
        assertEquals(listOf(DayStatus.KURANG, DayStatus.LEBIH, DayStatus.SESUAI), w.statuses.take(3))
        assertEquals(DayStatus.KOSONG, w.statuses[3])
        assertEquals("Nasi pecel", w.topMenu)
        assertEquals(2, w.topMenuCount)
        assertEquals(2, w.goodGradeMeals)
        assertEquals(monday.plusDays(2), w.bestDay)
        assertEquals(-0.6, w.weightChange!!, 1e-9)
        assertEquals(2000, w.avgCalories)
    }

    @Test
    fun `minggu kosong`() {
        val w = WeeklyWrapCalculator.compute(monday, emptyList(), { 2000 }, 2500, emptyMap(), emptyList(), null, zone)
        assertFalse(w.hasData)
        assertNull(w.topMenu)
        assertNull(w.bestDay)
        assertEquals(0, w.avgCalories)
    }
}
