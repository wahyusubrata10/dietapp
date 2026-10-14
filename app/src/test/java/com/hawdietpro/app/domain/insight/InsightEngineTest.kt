package com.hawdietpro.app.domain.insight

import com.hawdietpro.app.domain.TestFixtures
import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class InsightEngineTest {

    private val zone = ZoneOffset.UTC
    private val start = LocalDate.of(2026, 1, 5)
    private val calc = PeriodStatsCalculator(TestFixtures.insightRules, zone)
    private val engine = InsightEngine(TestFixtures.insightRules)

    private fun meal(day: Int, hour: Int, type: MealType, name: String, kcal: Double, itemName: String = name) = Meal(
        eatenAt = start.plusDays(day.toLong()).atTime(hour, 0).toInstant(zone).toEpochMilli(),
        mealType = type,
        menuName = name,
        photoPath = null,
        confidence = null,
        items = listOf(FoodItem(name = itemName, grams = 100.0, nutrients = Nutrients(kcal, kcal * 0.15 / 4, kcal * 0.5 / 4, kcal * 0.35 / 9, 2.0))),
    )

    private fun week(): List<Meal> = (0 until 7).flatMap { d ->
        listOf(
            meal(d, 7, MealType.SARAPAN, "Nasi uduk", 450.0),
            meal(d, 12, MealType.MAKAN_SIANG, "Nasi padang", 800.0),
            meal(d, 22, MealType.CAMILAN, "Martabak manis", 700.0, "Martabak manis"),
        )
    }

    @Test
    fun `statistik mingguan dihitung benar`() {
        val stats = calc.compute(week(), start, start.plusDays(6), 1800)
        assertEquals(7, stats.daysLogged)
        assertEquals(1950.0, stats.averagePerDay.calories, 0.01)
        assertEquals(0, stats.daysOver) // 1950 masih dalam toleransi 10% dari 1800 (≤1980)
        assertEquals(7, stats.daysOnTarget)
        assertEquals(700.0 / 1950 * 100, stats.metrics["mealTypeShare.CAMILAN"]!!, 0.01)
        assertEquals("Nasi padang", stats.topMenus.first().name)
        assertEquals(7, stats.currentStreak)
        assertEquals(7, stats.weekdayAverages.size) // jumlah sama, kalori terbesar menang
    }

    @Test
    fun `insight camilan malam muncul`() {
        val stats = calc.compute(week(), start, start.plusDays(6), 1800)
        val insights = engine.evaluate(stats, Period.WEEK)
        assertTrue(insights.any { it.id == "camilan_share" && it.message.contains("35,9") })
        assertTrue(insights.any { it.id == "malam_share" })
        assertTrue(insights.size <= TestFixtures.insightRules.maxInsights)
        assertTrue(insights.zipWithNext().all { (a, b) -> a.priority >= b.priority })
    }

    @Test
    fun `hari over target terhitung`() {
        val stats = calc.compute(week(), start, start.plusDays(6), 1500)
        assertEquals(7, stats.daysOver)
        val insights = engine.evaluate(stats, Period.WEEK)
        assertTrue(insights.any { it.id == "hari_over" })
    }

    @Test
    fun `tanpa data tidak ada insight`() {
        val stats = calc.compute(emptyList(), start, start.plusDays(6), 1800)
        assertTrue(engine.evaluate(stats, Period.WEEK).isEmpty())
        assertEquals(7, stats.days.size)
    }

    @Test
    fun `perubahan berat dihitung dari log dalam periode`() {
        val weights = listOf(
            WeightEntry(epochDay = start.toEpochDay(), weightKg = 80.0),
            WeightEntry(epochDay = start.plusDays(6).toEpochDay(), weightKg = 79.4),
        )
        val stats = calc.compute(week(), start, start.plusDays(6), 1800, weights)
        assertEquals(-0.6, stats.weightChangeKg!!, 0.001)
        val insights = engine.evaluate(stats, Period.WEEK)
        assertTrue(insights.any { it.id == "berat_turun" && it.message.contains("0,6") })
    }

    @Test
    fun `template dengan placeholder tak terisi dibuang`() {
        assertNull(engine.fill("Menu {topMenu}", 1.0, Period.WEEK, emptyMap()))
        assertEquals("Nilai 12 minggu ini", engine.fill("Nilai {value} {period}", 12.0, Period.WEEK, emptyMap()))
    }
}
