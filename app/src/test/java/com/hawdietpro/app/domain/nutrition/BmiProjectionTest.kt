package com.hawdietpro.app.domain.nutrition

import com.hawdietpro.app.domain.model.ActivityLevel
import com.hawdietpro.app.domain.model.Goal
import com.hawdietpro.app.domain.model.Sex
import com.hawdietpro.app.domain.model.UserProfile
import com.hawdietpro.app.domain.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BmiProjectionTest {

    private val start = LocalDate.of(2026, 1, 1)
    private val today = start.plusDays(20)

    private fun profile(height: Double = 175.0, weight: Double = 87.6, target: Double? = 70.0, goal: Goal = Goal.TURUN) =
        UserProfile("Uji", 30, Sex.PRIA, height, weight, ActivityLevel.SEDENTARI, goal, target)

    private fun tdee(w: Double, h: Double) = TdeeCalculator.bmr(Sex.PRIA, w, h, 30) * 1.2

    /** 20 hari tercatat dengan selisih [delta] kkal/hari dari TDEE awal. */
    private fun projection(p: UserProfile, startWeight: Double, delta: Double, weights: List<WeightEntry> = emptyList()): WeightProjection {
        val intake = (0 until 20).associate { start.plusDays(it.toLong()) to tdee(startWeight, p.heightCm) + delta }
        return WeightProjector.project(p, start, startWeight, today, intake, weights)
    }

    @Test
    fun `kategori Asia-Pasifik di batasnya`() {
        assertEquals(BmiClass.KURANG, BmiClass.of(18.49))
        assertEquals(BmiClass.NORMAL, BmiClass.of(18.5))
        assertEquals(BmiClass.NORMAL, BmiClass.of(22.99))
        assertEquals(BmiClass.BERLEBIH, BmiClass.of(23.0))
        assertEquals(BmiClass.OBESITAS_1, BmiClass.of(25.0))
        assertEquals(BmiClass.OBESITAS_2, BmiClass.of(30.0))
        assertEquals("Obesitas I", TdeeCalculator.bmiCategory(27.0))
        assertEquals("-", TdeeCalculator.bmiCategory(0.0))
    }

    @Test
    fun `BMI mengikuti berat perkiraan dan rentang sehat sesuai tinggi`() {
        val p = profile()
        val b = BmiProjector.project(projection(p, 87.6, -700.0))!!
        val proj = projection(p, 87.6, -700.0)
        assertEquals(proj.estimatedToday / (1.75 * 1.75), b.current, 1e-9)
        assertEquals(87.6 / (1.75 * 1.75), b.start, 1e-9)
        // 1,75 m: BMI 18,5 = 56,66 kg → 56,7; BMI < 23 = < 70,44 kg → 70,4.
        assertEquals(56.7, b.healthyMinKg, 1e-9)
        assertEquals(70.4, b.healthyMaxKg, 1e-9)
        assertEquals(b.healthyMaxKg, b.milestones.last().weightKg, 1e-9)
        assertTrue("harus turun ke rentang sehat", b.kgToHealthy < 0)
        assertEquals(b.healthyMaxKg - proj.estimatedToday, b.kgToHealthy, 1e-9)
        // Proyeksi BMI = proyeksi berat / tinggi².
        assertEquals(proj.forecast.size, b.forecast.size)
        assertEquals(proj.forecast.last().second / (1.75 * 1.75), b.forecast.last().second, 1e-9)
        assertEquals(70.0 / (1.75 * 1.75), b.target!!, 1e-9)
        assertEquals(BmiClass.NORMAL, b.targetClass)
    }

    @Test
    fun `milestone turun berurutan dengan berat tepat di bawah batas kategori`() {
        val b = BmiProjector.project(projection(profile(), 87.6, -700.0))!!
        // BMI awal ±28,6 (Obesitas I) → batas Berlebih (<25) lalu Normal (<23).
        assertEquals(listOf(BmiClass.BERLEBIH, BmiClass.NORMAL), b.milestones.map { it.reachedClass })
        b.milestones.forEach { m ->
            assertTrue("BMI ${m.bmi} harus di bawah batas", m.bmi < m.reachedClass.upper)
            // 0,1 kg lebih berat sudah keluar dari kategori tersebut.
            assertTrue(TdeeCalculator.bmi(m.weightKg + 0.1, 175.0) >= m.reachedClass.upper)
            assertFalse(m.reached)
            assertNotNull("defisit konsisten → ada perkiraan tanggal", m.date)
            assertTrue(m.date!!.isAfter(today))
        }
        assertTrue(b.milestones[0].date!!.isBefore(b.milestones[1].date))
    }

    @Test
    fun `surplus tidak memberi tanggal menuju normal`() {
        val b = BmiProjector.project(projection(profile(), 87.6, +500.0))!!
        assertTrue(b.milestones.isNotEmpty())
        assertTrue(b.milestones.all { it.date == null })
    }

    @Test
    fun `milestone yang sudah dilewati ditandai tercapai`() {
        // Mulai di 77 kg (BMI 25,1 Obesitas I), defisit besar → sudah di bawah 25 setelah 20 hari.
        val b = BmiProjector.project(projection(profile(), 77.0, -1000.0))!!
        val berlebih = b.milestones.first { it.reachedClass == BmiClass.BERLEBIH }
        assertTrue(berlebih.reached)
        assertNull(berlebih.date)
        assertFalse(b.milestones.first { it.reachedClass == BmiClass.NORMAL }.reached)
    }

    @Test
    fun `berat kurang punya milestone naik ke normal`() {
        val p = profile(weight = 52.0, target = 58.0, goal = Goal.NAIK)
        val b = BmiProjector.project(projection(p, 52.0, +500.0))!!
        assertEquals(BmiClass.KURANG, b.currentClass)
        assertTrue(b.kgToHealthy > 0)
        val m = b.milestones.single()
        assertEquals(BmiClass.NORMAL, m.reachedClass)
        assertTrue(m.bmi >= 18.5)
        assertFalse(m.downward)
        assertTrue(TdeeCalculator.bmi(m.weightKg - 0.1, 175.0) < 18.5)
        assertNotNull(m.date)
    }

    @Test
    fun `sudah normal tanpa milestone dan timbangan terakhir dipakai`() {
        val w = listOf(WeightEntry(1, today.minusDays(1).toEpochDay(), 66.0))
        val b = BmiProjector.project(projection(profile(weight = 66.0, target = null), 66.0, 0.0, w))!!
        assertEquals(BmiClass.NORMAL, b.currentClass)
        assertEquals(0.0, b.kgToHealthy, 0.0)
        assertTrue(b.milestones.isEmpty())
        assertEquals(66.0 / (1.75 * 1.75), b.actual!!, 1e-9)
        assertNull(b.target)
    }

    @Test
    fun `tinggi tidak wajar tidak diproyeksikan`() {
        assertNull(BmiProjector.project(projection(profile(height = 0.0), 80.0, 0.0)))
    }
}
