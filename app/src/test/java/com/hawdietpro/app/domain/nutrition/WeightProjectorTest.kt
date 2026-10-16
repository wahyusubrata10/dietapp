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

class WeightProjectorTest {

    private val profile = UserProfile("Uji", 30, Sex.PRIA, 170.0, 80.0, ActivityLevel.SEDENTARI, Goal.TURUN, 75.0)
    private val start = LocalDate.of(2026, 1, 1)

    private fun tdee(w: Double) = TdeeCalculator.bmr(Sex.PRIA, w, 170.0, 30) * 1.2

    @Test
    fun `makan sesuai TDEE berat tetap`() {
        // Makan persis TDEE berat awal setiap hari selama 10 hari.
        val intake = (0 until 10).associate { start.plusDays(it.toLong()) to tdee(80.0) }
        val p = WeightProjector.project(profile, start, 80.0, start.plusDays(10), intake, emptyList())
        assertEquals(80.0, p.estimatedToday, 0.01)
        assertEquals(10, p.countedDays)
    }

    @Test
    fun `defisit 770 kkal per hari selama 10 hari turun sekitar 1 kg`() {
        val intake = (0 until 10).associate { start.plusDays(it.toLong()) to tdee(80.0) - 770 }
        val p = WeightProjector.project(profile, start, 80.0, start.plusDays(10), intake, emptyList())
        // TDEE ikut turun sedikit saat berat turun, jadi turunnya sedikit < 1 kg.
        assertTrue("perkiraan ${p.estimatedToday}", p.estimatedToday in 79.0..79.05)
        assertTrue(p.cumulativeBalance < -7600)
    }

    @Test
    fun `hari tanpa catatan dianggap netral, catatan sedikit tetap dihitung`() {
        val intake = mapOf(
            start to tdee(80.0) - 770, // catatan kecil tetap dihitung apa adanya
            start.plusDays(2) to tdee(80.0) + 770,
        )
        val p = WeightProjector.project(profile, start, 80.0, start.plusDays(3), intake, emptyList())
        assertEquals(2, p.countedDays)
        assertEquals(1, p.skippedDays)
        assertEquals(80.0, p.estimatedToday, 0.01)
    }

    @Test
    fun `asupan sangat rendah tetap valid`() {
        val intake = mapOf(start to 300.0)
        val p = WeightProjector.project(profile, start, 80.0, start.plusDays(1), intake, emptyList())
        assertEquals(1, p.countedDays)
        assertEquals(80.0 + (300.0 - tdee(80.0)) / WeightProjector.KCAL_PER_KG, p.estimatedToday, 0.001)
    }

    @Test
    fun `hari ini belum dihitung`() {
        val intake = mapOf(start to 500.0 + tdee(80.0))
        val p = WeightProjector.project(profile, start, 80.0, start, intake, emptyList())
        assertTrue(p.days.isEmpty())
        assertEquals(80.0, p.estimatedToday, 0.0)
    }

    @Test
    fun `proyeksi dan tanggal target dihitung dari rata-rata terbaru`() {
        val intake = (0 until 14).associate { start.plusDays(it.toLong()) to tdee(80.0) - 550 }
        val today = start.plusDays(14)
        val p = WeightProjector.project(profile, start, 80.0, today, intake, emptyList())
        assertNotNull(p.recentDailyBalance)
        assertEquals(29, p.forecast.size)
        assertTrue(p.forecast.last().second < p.estimatedToday)
        val date = p.targetDate
        assertNotNull(date)
        // ±550 kkal/hari ≈ 0,5 kg/minggu; sisa ±4 kg → ±8 minggu
        assertTrue(WeightProjector.daysBetween(today, date!!) in 45L..70L)
    }

    @Test
    fun `target tidak searah tidak diberi tanggal`() {
        val intake = (0 until 14).associate { start.plusDays(it.toLong()) to tdee(80.0) + 400 }
        val p = WeightProjector.project(profile, start, 80.0, start.plusDays(14), intake, emptyList())
        assertNull(p.targetDate)
    }

    @Test
    fun `perubahan aktual dari log berat`() {
        val logs = listOf(WeightEntry(epochDay = start.toEpochDay(), weightKg = 80.0), WeightEntry(epochDay = start.plusDays(5).toEpochDay(), weightKg = 79.2))
        val p = WeightProjector.project(profile, start, 80.0, start.plusDays(6), emptyMap(), logs)
        assertEquals(-0.8, p.actualChange!!, 0.001)
    }

    @Test
    fun `titik awal memakai tanggal & berat paling awal`() {
        val logs = listOf(WeightEntry(epochDay = start.plusDays(3).toEpochDay(), weightKg = 81.0))
        val (s, w) = WeightProjector.startPoint(profile, start, logs, start.plusDays(10))
        assertEquals(start, s)
        assertEquals(81.0, w, 0.0)
        val (s2, w2) = WeightProjector.startPoint(profile, null, emptyList(), start)
        assertEquals(start, s2)
        assertEquals(80.0, w2, 0.0)
    }
}
