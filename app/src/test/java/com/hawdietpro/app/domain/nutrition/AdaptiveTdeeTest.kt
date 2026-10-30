package com.hawdietpro.app.domain.nutrition

import com.hawdietpro.app.domain.model.ActivityLevel
import com.hawdietpro.app.domain.model.Goal
import com.hawdietpro.app.domain.model.Sex
import com.hawdietpro.app.domain.model.UserProfile
import com.hawdietpro.app.domain.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AdaptiveTdeeTest {

    private val profile = UserProfile("Uji", 30, Sex.PRIA, 170.0, 80.0, ActivityLevel.SEDENTARI, Goal.TURUN, 75.0)
    private val today = LocalDate.of(2026, 3, 1)
    private fun day(daysAgo: Int) = today.minusDays(daysAgo.toLong())
    private fun w(daysAgo: Int, kg: Double) = WeightEntry(epochDay = day(daysAgo).toEpochDay(), weightKg = kg)

    /** Asupan tetap tiap hari selama [days] hari ke belakang. */
    private fun intake(kcal: Double, days: Int = 30) = (1..days).associate { day(it) to kcal }

    @Test
    fun `tanpa timbangan cukup memakai rumus`() {
        val r = AdaptiveTdeeEstimator.estimate(profile, today, intake(2000.0), listOf(w(3, 80.0)))
        assertEquals(AdaptiveTdee.Status.BELUM_CUKUP, r.status)
        assertEquals(r.formulaTdee, r.tdee)
        assertEquals(1.0, r.multiplier, 0.0)
        assertNull(r.observedTdee)
        assertNotNull(r.hint)
    }

    @Test
    fun `jarak timbangan kurang dari seminggu belum dihitung`() {
        val r = AdaptiveTdeeEstimator.estimate(profile, today, intake(2000.0), listOf(w(5, 80.0), w(1, 79.5)))
        assertEquals(AdaptiveTdee.Status.BELUM_CUKUP, r.status)
    }

    @Test
    fun `berat stabil maka kebutuhan sama dengan asupan`() {
        // 4 minggu data lengkap, berat tetap → kebutuhan sebenarnya = asupan rata-rata.
        val weights = listOf(w(28, 80.0), w(21, 80.0), w(14, 80.0), w(7, 80.0), w(1, 80.0))
        val r = AdaptiveTdeeEstimator.estimate(profile, today, intake(2400.0, 40), weights, firstLogDate = day(60))
        assertEquals(AdaptiveTdee.Status.AKURAT, r.status)
        assertEquals(2400.0, r.observedTdee!!.toDouble(), 30.0)
        assertTrue("tdee ${r.tdee}", r.tdee in 2350..2450)
    }

    @Test
    fun `turun setengah kilo per minggu berarti defisit sekitar 550 kkal`() {
        val weights = (0..4).map { w(28 - it * 7, 80.0 - it * 0.5) }
        val r = AdaptiveTdeeEstimator.estimate(profile, today, intake(1900.0, 40), weights, firstLogDate = day(60))
        assertEquals(-0.5, r.trendKgPerWeek!!, 0.01)
        // 0,5 kg × 7700 / 7 = 550 kkal/hari di atas asupan.
        assertEquals(2450.0, r.observedTdee!!.toDouble(), 40.0)
    }

    @Test
    fun `olahraga tercatat tidak terhitung dua kali`() {
        val weights = listOf(w(28, 80.0), w(21, 80.0), w(14, 80.0), w(7, 80.0), w(1, 80.0))
        val exercise = (1..30).associate { day(it) to 300.0 }
        val r = AdaptiveTdeeEstimator.estimate(profile, today, intake(2400.0, 40), weights, exercise, firstLogDate = day(60))
        // Makan 2400 + olahraga 300 dengan berat stabil → kebutuhan dasar 2100 (olahraga ditambah terpisah).
        assertEquals(2100.0, r.observedTdee!!.toDouble(), 30.0)
    }

    @Test
    fun `data sedikit hanya sedikit menggeser rumus`() {
        val weights = listOf(w(9, 80.0), w(1, 79.0))
        val r = AdaptiveTdeeEstimator.estimate(profile, today, intake(1500.0, 20), weights, firstLogDate = day(12))
        assertEquals(AdaptiveTdee.Status.BELAJAR, r.status)
        assertTrue(r.confidence < 0.3)
        val shift = r.tdee - r.formulaTdee
        assertTrue("geser $shift", shift > 0 && shift < 300)
    }

    @Test
    fun `hasil janggal dibatasi ke rentang fisiologis`() {
        // Turun 5 kg dalam seminggu (air) dengan asupan 1000 → dibatasi maks 2,2 × BMR.
        val weights = listOf(w(8, 85.0), w(1, 80.0))
        val r = AdaptiveTdeeEstimator.estimate(profile, today, intake(1000.0, 10), weights)
        val bmr = TdeeCalculator.bmr(Sex.PRIA, 82.5, 170.0, 30)
        assertTrue(r.observedTdee!! <= (bmr * 2.2 * 1.01).toInt())
    }

    @Test
    fun `catatan makan jarang tidak dipakai`() {
        val weights = listOf(w(28, 80.0), w(14, 79.5), w(1, 79.0))
        val sparse = (1..28 step 4).associate { day(it) to 1800.0 }
        val r = AdaptiveTdeeEstimator.estimate(profile, today, sparse, weights)
        assertEquals(AdaptiveTdee.Status.BELUM_CUKUP, r.status)
    }

    @Test
    fun `target harian memakai tdee adaptif`() {
        val t = TdeeCalculator.calculate(profile, 2600)
        assertEquals(2600, t.tdee)
        assertTrue(t.adaptive)
        assertEquals(2100, t.calories) // defisit maks 500
    }

    @Test
    fun `turun cepat minggu pertama (air) diabaikan bila data sesudahnya cukup`() {
        // Hari 1–6: turun 2 kg (air). Sesudahnya stabil dengan asupan 2400 → kebutuhan ±2400, bukan jauh lebih tinggi.
        val first = day(30)
        val weights = listOf(w(30, 82.0), w(25, 80.0), w(20, 80.0), w(14, 80.0), w(7, 80.0), w(1, 80.0))
        val r = AdaptiveTdeeEstimator.estimate(profile, today, intake(2400.0, 30), weights, firstLogDate = first)
        assertEquals(2400.0, r.observedTdee!!.toDouble(), 40.0)
    }

    @Test
    fun `regresi linear`() {
        val slope = AdaptiveTdeeEstimator.slopePerDay(listOf(0L to 80.0, 7L to 79.3, 14L to 78.6))
        assertEquals(-0.1, slope, 1e-9)
    }
}
