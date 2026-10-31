package com.hawdietpro.app.domain.insight

import com.hawdietpro.app.domain.TestFixtures
import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Pengguna lama dengan ratusan catatan: statistik tetap benar & cepat dihitung. */
class PeriodStatsScaleTest {
    private val zone = ZoneId.of("Asia/Jakarta")
    private val end = LocalDate.of(2026, 10, 31)
    private val start = end.minusDays(179)

    private fun meals(): List<Meal> = (0 until 180).flatMap { day ->
        val date = start.plusDays(day.toLong())
        MealType.entries.mapIndexed { i, type ->
            Meal(
                id = day * 10L + i,
                eatenAt = date.atTime(7 + i * 4, 15).atZone(zone).toInstant().toEpochMilli(),
                mealType = type,
                // Nama sangat panjang ikut diuji agar peringkat menu tidak bermasalah.
                menuName = if (i == 1) "Nasi campur ayam betutu sambal matah lawar sayur urap telur pindang" else "Menu ${i + 1}",
                photoPath = null,
                confidence = 0.8,
                items = listOf(FoodItem(name = "Elemen", grams = 200.0, nutrients = Nutrients(450.0, 20.0, 55.0, 15.0, 4.0, 6.0, 400.0))),
            )
        }
    }

    @Test
    fun `720 catatan dihitung benar`() {
        val all = meals()
        assertEquals(720, all.size)
        val stats = PeriodStatsCalculator(TestFixtures.insightRules, zone).compute(all, start, end, 1900)
        assertEquals(180, stats.daysLogged)
        assertEquals(720 * 450.0, stats.totalCalories, 0.01)
        assertEquals(1800.0, stats.averagePerDay.calories, 0.01)
        assertEquals(180, stats.daysOnTarget)
        assertEquals(180 * 450.0, stats.mealTypeCalories[MealType.MAKAN_SIANG] ?: 0.0, 0.01)
        assertTrue(stats.topMenus.isNotEmpty())
    }

    @Test
    fun `perhitungan 720 catatan tetap cepat`() {
        val all = meals()
        val calc = PeriodStatsCalculator(TestFixtures.insightRules, zone)
        calc.compute(all, start, end, 1900) // pemanasan JIT
        val t0 = System.nanoTime()
        repeat(5) { calc.compute(all, start, end, 1900) }
        val perRunMs = (System.nanoTime() - t0) / 5 / 1_000_000.0
        // Jauh di bawah satu frame-budget layar terbuka; ambang longgar agar stabil di CI.
        assertTrue("terlalu lambat: $perRunMs ms", perRunMs < 250.0)
    }
}
