package com.hawdietpro.app.domain.body

import com.hawdietpro.app.domain.model.ActivityLevel
import com.hawdietpro.app.domain.model.Goal
import com.hawdietpro.app.domain.model.Sex
import com.hawdietpro.app.domain.model.UserProfile
import com.hawdietpro.app.domain.model.WeightEntry
import com.hawdietpro.app.domain.nutrition.TdeeCalculator
import com.hawdietpro.app.domain.nutrition.WeightProjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class BodyModelTest {

    /** Lebar (x) mesh pada ketinggian [yCm] ± 1 cm, hanya bagian badan (|x| < 24, lengan tidak ikut). */
    private fun widthAt(mesh: BodyMesh, yCm: Double): Double {
        var maxX = 0.0
        for (i in 0 until mesh.vertexCount) {
            val x = mesh.positions[i * 3].toDouble()
            val y = mesh.positions[i * 3 + 1].toDouble()
            if (abs(y - yCm) < 1.0 && abs(x) < 24) maxX = maxOf(maxX, abs(x))
        }
        return maxX * 2
    }

    private fun depthFront(mesh: BodyMesh, yCm: Double): Double {
        var maxZ = 0.0
        for (i in 0 until mesh.vertexCount) {
            val x = mesh.positions[i * 3].toDouble()
            val y = mesh.positions[i * 3 + 1].toDouble()
            if (abs(y - yCm) < 1.0 && abs(x) < 8) maxZ = maxOf(maxZ, mesh.positions[i * 3 + 2].toDouble())
        }
        return maxZ
    }

    @Test
    fun `mesh valid, muat indeks 16-bit, tinggi sesuai`() {
        val m = BodyMeshBuilder.build(BodyParams(175.0, 75.0, Sex.PRIA))
        assertTrue(m.vertexCount in 1000 until Short.MAX_VALUE)
        assertTrue(m.positions.none { it.isNaN() } && m.normals.none { it.isNaN() })
        assertTrue(m.indices.all { it in 0 until m.vertexCount })
        val top = (1 until m.positions.size step 3).maxOf { m.positions[it] }
        val bottom = (1 until m.positions.size step 3).minOf { m.positions[it] }
        assertEquals(175.0, top.toDouble(), 3.0)
        assertTrue(bottom > -1f && bottom < 2f)
    }

    @Test
    fun `makin berat makin lebar, perut pria paling peka`() {
        val thin = BodyMeshBuilder.build(BodyParams(175.0, 60.0, Sex.PRIA))
        val heavy = BodyMeshBuilder.build(BodyParams(175.0, 100.0, Sex.PRIA))
        val waistY = 0.60 * 175
        val headY = 0.93 * 175
        assertTrue(widthAt(heavy, waistY) > widthAt(thin, waistY) * 1.2)
        assertTrue(depthFront(heavy, waistY) > depthFront(thin, waistY) * 1.3)
        // Kepala hampir tidak berubah.
        assertEquals(widthAt(thin, headY), widthAt(heavy, headY), 0.5)
    }

    @Test
    fun `wanita lebih lebar di pinggul relatif terhadap pinggang`() {
        val m = BodyMeshBuilder.build(BodyParams(165.0, 65.0, Sex.PRIA))
        val f = BodyMeshBuilder.build(BodyParams(165.0, 65.0, Sex.WANITA))
        val ratio = { mesh: BodyMesh -> widthAt(mesh, 0.53 * 165) / widthAt(mesh, 0.60 * 165) }
        assertTrue(ratio(f) > ratio(m))
    }

    @Test
    fun `render menghasilkan siluet simetris dari depan dan lebih ramping dari samping`() {
        val r = BodyRenderer(BodyMeshBuilder.build(BodyParams(175.0, 80.0, Sex.PRIA)))
        val w = 200; val h = 400
        fun silhouette(yaw: Float): Pair<Int, IntArray> {
            val px = r.render(w, h, yaw, 0f, h * 0.9f / 180f, h * 0.97f, 0xFF7FA6D9.toInt())
            val cols = IntArray(w)
            var filled = 0
            for (y in 0 until h) for (x in 0 until w) if (px[y * w + x] != 0) { filled++; cols[x]++ }
            return filled to cols
        }
        val (front, cols) = silhouette(0f)
        assertTrue("tubuh tergambar", front > w * h / 20)
        // Kiri-kanan simetris (toleransi piksel tepi).
        val left = cols.take(w / 2).sum()
        val right = cols.drop(w / 2).sum()
        assertEquals(1.0, left.toDouble() / right, 0.06)
        val (side, _) = silhouette((Math.PI / 2).toFloat())
        assertTrue("dari samping lebih ramping", side < front)
    }

    private val profile = UserProfile("Uji", 30, Sex.PRIA, 175.0, 90.0, ActivityLevel.SEDENTARI, Goal.TURUN, 75.0)
    private val start = LocalDate.of(2026, 1, 1)
    private val today = start.plusDays(10)

    @Test
    fun `berat per tanggal memakai timbangan, lalu perkiraan, lalu proyeksi`() {
        val intake = (0 until 10).associate { start.plusDays(it.toLong()) to TdeeCalculator.bmr(Sex.PRIA, 90.0, 175.0, 30) * 1.2 - 700 }
        val weights = listOf(WeightEntry(1, start.plusDays(4).toEpochDay(), 89.2))
        val p = WeightProjector.project(profile, start, 90.0, today, intake, weights)

        assertEquals(BodySnapshot.Source.PERKIRAAN, BodyTimeline.at(p, start)!!.source)
        assertEquals(90.0, BodyTimeline.at(p, start)!!.weightKg, 1e-9)
        val scale = BodyTimeline.at(p, start.plusDays(4))!!
        assertEquals(BodySnapshot.Source.TIMBANGAN, scale.source)
        assertEquals(89.2, scale.weightKg, 1e-9)
        // Perkiraan tanggal 3 = akhir hari ke-2.
        assertEquals(p.days[1].estimatedWeight, BodyTimeline.at(p, start.plusDays(2))!!.weightKg, 1e-9)
        assertEquals(p.estimatedToday, BodyTimeline.at(p, today)!!.weightKg, 1e-9)
        val future = BodyTimeline.at(p, today.plusDays(14))!!
        assertEquals(BodySnapshot.Source.PROYEKSI, future.source)
        assertTrue(future.weightKg < p.estimatedToday)
        assertNull(BodyTimeline.at(p, start.minusDays(1)))
        assertNull(BodyTimeline.at(p, BodyTimeline.lastDate(p).plusDays(1)))
        assertNotNull(BodyTimeline.target(p))
        assertEquals(Sex.PRIA, p.sex)
    }

    @Test
    fun `perkiraan pinggang naik bersama BMI`() {
        val a = BodyTimeline.waistCm(70.0, 175.0, Sex.PRIA)
        val b = BodyTimeline.waistCm(90.0, 175.0, Sex.PRIA)
        assertTrue(b - a > 10)
        assertTrue(a in 75.0..90.0)
    }
}
