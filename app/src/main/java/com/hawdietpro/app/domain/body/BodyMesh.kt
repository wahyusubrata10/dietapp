package com.hawdietpro.app.domain.body

import com.hawdietpro.app.domain.model.Sex
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mesh segitiga sederhana: [positions] & [normals] berisi x,y,z per verteks (cm; y ke atas, z ke depan),
 * [indices] berisi tiga indeks verteks per segitiga (urutan berlawanan jarum jam dilihat dari luar).
 */
class BodyMesh(val positions: FloatArray, val normals: FloatArray, val indices: IntArray) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3
}

/** Masukan bentuk tubuh. */
data class BodyParams(val heightCm: Double, val weightKg: Double, val sex: Sex) {
    val bmi: Double get() = weightKg / ((heightCm / 100) * (heightCm / 100))
}

/**
 * Manekin parametrik: proporsi tulang mengikuti tinggi badan (rasio antropometri standar),
 * sedangkan ketebalan jaringan lunak tiap bagian mengikuti BMI dengan kepekaan berbeda:
 * perut paling peka (terutama pria), lalu pinggul/paha (terutama wanita), lengan, dan paling
 * sedikit di kepala, tangan & kaki. Ini perkiraan visual, bukan hasil pindai tubuh.
 */
object BodyMeshBuilder {
    private const val REF_HEIGHT = 175.0
    private const val REF_BMI = 22.0
    internal const val SEGMENTS = 28

    /** Pengali tebal jaringan untuk bagian dengan kepekaan [s] terhadap BMI. */
    internal fun girth(bmi: Double, s: Double): Double = max(0.62, 1 + s * 0.026 * (bmi.coerceIn(13.0, 60.0) - REF_BMI))

    fun build(p: BodyParams): BodyMesh {
        val b = Builder()
        val k = p.heightCm / REF_HEIGHT
        val bmi = p.bmi
        val female = p.sex == Sex.WANITA
        fun y(frac: Double) = frac * REF_HEIGHT * k

        // ── Badan: penampang elips (lebar, depan, belakang, geser depan) dari selangkangan ke leher.
        val bellyS = if (female) 1.05 else 1.35
        val hipS = if (female) 1.25 else 1.1
        val shoulderW = if (female) 0.9 else 1.0
        val waistW = if (female) 0.88 else 1.0
        val hipW = if (female) 1.1 else 1.0
        val torso = listOf(
            // yFrac, halfWidth, front, back, zShift, sensitivity
            Ring(0.432, 4.0, 3.5, 4.0, -0.5, 0.5),
            Ring(0.448, 12.5 * hipW, 7.5, 9.0, -0.5, hipS),
            Ring(0.468, 16.6 * hipW, 9.0, 10.8, -0.3, hipS),
            Ring(0.50, 17.0 * hipW, 9.5, 11.8, 0.0, hipS),
            Ring(0.53, 17.3 * hipW, 9.5, 11.0, 0.3, hipS),
            Ring(0.565, 15.8 * (hipW + waistW) / 2, 9.8, 9.5, 0.5, (hipS + bellyS) / 2),
            Ring(0.60, 14.6 * waistW, 10.5, 8.8, 0.6, bellyS),
            Ring(0.64, 14.6 * waistW, 10.3, 8.8, 0.6, bellyS * 0.95),
            Ring(0.68, 15.4 * (waistW + shoulderW) / 2, 10.0, 9.2, 0.4, 0.85),
            Ring(0.72, 16.6 * shoulderW, if (female) 12.5 else 11.0, 9.8, 0.5, if (female) 0.95 else 0.7),
            Ring(0.765, 17.8 * shoulderW, 10.0, 10.0, 0.0, 0.5),
            Ring(0.79, 18.2 * shoulderW, 8.0, 8.6, -0.6, 0.4),
            Ring(0.806, 16.0 * shoulderW, 6.6, 7.2, -1.0, 0.3),
            Ring(0.82, 11.5 * shoulderW, 5.8, 6.4, -1.0, 0.25),
            Ring(0.834, 6.2 * (if (female) 0.88 else 1.0), 5.4, 5.6, -0.6, 0.2),
            Ring(0.858, 5.3 * (if (female) 0.88 else 1.0), 5.0, 5.0, 0.0, 0.15),
            Ring(0.878, 4.2 * (if (female) 0.9 else 1.0), 4.2, 4.2, 0.4, 0.1),
        ).map { r ->
            val g = girth(bmi, r.s)
            // Lemak perut pria menonjol ke depan (bukan hanya melebar).
            val frontExtra = if (!female && r.s >= 1.2) 1 + 0.45 * (g - 1) else 1.0
            RingPoint(
                center = V3(0.0, y(r.f), r.z * k * (if (g > 1) g else 1.0)),
                halfU = r.w * k * g,
                front = r.front * k * g * frontExtra,
                back = r.back * k * g,
            )
        }
        b.tube(torso, axisU = V3(1.0, 0.0, 0.0), closeStart = true, closeEnd = true, smooth = 3)

        // ── Kepala: elipsoid dengan wajah sedikit ke depan.
        val headC = V3(0.0, y(0.932), 1.0 * k)
        val headRings = (0..16).map { i ->
            val phi = -PI / 2 + PI * i / 16
            val c = cos(phi)
            val jaw = if (phi < 0) 0.86 + 0.14 * (1 + sin(phi)) else 1.0
            RingPoint(
                center = V3(0.0, headC.y + 11.8 * k * sin(phi), headC.z + 0.8 * k * c),
                halfU = 8.3 * k * c * jaw,
                front = 9.6 * k * c * jaw,
                back = 9.2 * k * c,
            )
        }
        b.tube(headRings, axisU = V3(1.0, 0.0, 0.0), closeStart = false, closeEnd = false, smooth = 1)

        // ── Kaki (sedikit terbuka seperti pose-A).
        for (side in listOf(-1.0, 1.0)) {
            val top = V3(side * 7.9 * k * hipW, y(0.53), 0.0)
            val ankle = V3(side * 11.5 * k, y(0.045), -1.0 * k)
            val legKeys = listOf(
                // Pangkal paha dimulai di dalam pinggul agar sambungannya tidak terlihat.
                LimbKey(0.0, 6.5, 6.5, 0.0, if (female) 1.2 else 1.0),
                LimbKey(0.08, 9.2, 9.4, 0.0, if (female) 1.25 else 1.1),
                LimbKey(0.16, 9.0, 9.0, 0.3, if (female) 1.3 else 1.1),
                LimbKey(0.32, 7.6, 7.6, 0.3, if (female) 1.15 else 0.95),
                LimbKey(0.44, 5.6, 5.8, 0.4, 0.55),
                LimbKey(0.5, 5.3, 5.5, 0.3, 0.45),
                LimbKey(0.6, 5.6, 6.0, -0.6, 0.6),
                LimbKey(0.68, 5.7, 6.3, -1.0, 0.65),
                LimbKey(0.85, 4.0, 4.2, -0.4, 0.35),
                LimbKey(1.0, 3.3, 3.6, 0.0, 0.15),
            )
            b.tube(limbRings(top, ankle, legKeys, k, bmi), axisU = V3(1.0, 0.0, 0.0), closeStart = true, closeEnd = true, smooth = 3)

            // Telapak kaki: elipsoid memanjang ke depan.
            val footC = V3(ankle.x * 1.02, y(0.022), ankle.z + 5.5 * k)
            val footRings = (0..12).map { i ->
                val phi = -PI / 2 + PI * i / 12
                val c = cos(phi)
                RingPoint(V3(footC.x, footC.y, footC.z + 12.5 * k * sin(phi)), 4.4 * k * c * girth(bmi, 0.1), 3.6 * k * c, 3.6 * k * c)
            }
            b.tube(footRings, axisU = V3(1.0, 0.0, 0.0), closeStart = false, closeEnd = false, smooth = 1, alongZ = true)
        }

        // ── Lengan pose-A: dari sendi bahu, 28° dari vertikal.
        for (side in listOf(-1.0, 1.0)) {
            val shoulder = V3(side * 14.2 * k * shoulderW, y(0.795), -0.8 * k)
            val angle = 28.0 * PI / 180
            val dir = V3(side * sin(angle), -cos(angle), 0.0)
            val length = (0.186 + 0.146 + 0.108) * REF_HEIGHT * k
            val end = shoulder + dir * length
            val armS = if (female) 1.15 else 1.0
            val armW = if (female) 0.9 else 1.0
            val upper = 0.186 / 0.44
            val fore = (0.186 + 0.146) / 0.44
            val armKeys = listOf(
                LimbKey(0.0, 5.0 * armW, 5.0 * armW, 0.0, 0.7),
                LimbKey(upper * 0.18, 5.8 * armW, 5.6 * armW, 0.0, 0.8),
                LimbKey(upper * 0.45, 5.2 * armW, 5.0 * armW, 0.0, armS),
                LimbKey(upper * 0.8, 4.4 * armW, 4.2 * armW, 0.0, armS * 0.8),
                LimbKey(upper, 3.6 * armW, 3.5 * armW, 0.0, 0.4),
                LimbKey(upper + (fore - upper) * 0.3, 4.0 * armW, 3.9 * armW, 0.0, 0.55),
                LimbKey(fore, 2.6 * armW, 2.9 * armW, 0.0, 0.2),
                // Telapak tangan: pipih (tipis ke samping, lebar ke depan-belakang).
                LimbKey(fore + 0.04, 1.6, 3.6, 0.0, 0.15),
                LimbKey(fore + 0.12, 1.5, 4.2, 0.2, 0.15),
                LimbKey(0.96, 1.3, 3.2, 0.3, 0.1),
                LimbKey(1.0, 0.9, 1.9, 0.3, 0.1),
            )
            b.tube(limbRings(shoulder, end, armKeys, k, bmi), axisU = dir.cross(V3(0.0, 0.0, 1.0)).normalized(), closeStart = true, closeEnd = true, smooth = 3)
        }
        return b.finish()
    }

    private data class Ring(val f: Double, val w: Double, val front: Double, val back: Double, val z: Double, val s: Double)
    private data class LimbKey(val t: Double, val u: Double, val zr: Double, val zShift: Double, val s: Double)

    private fun limbRings(start: V3, end: V3, keys: List<LimbKey>, k: Double, bmi: Double): List<RingPoint> =
        keys.map { key ->
            val g = girth(bmi, key.s)
            val c = start + (end - start) * key.t
            RingPoint(V3(c.x, c.y, c.z + key.zShift * k), key.u * k * g, key.zr * k * g, key.zr * k * g)
        }
}

internal data class V3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = V3(x * s, y * s, z * s)
    fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(x * x + y * y + z * z)
    fun normalized(): V3 = length().let { if (it < 1e-9) this else this * (1 / it) }
}

/** Satu penampang: pusat, setengah lebar (sumbu U), kedalaman depan & belakang (sumbu Z). */
internal data class RingPoint(val center: V3, val halfU: Double, val front: Double, val back: Double)

private class Builder {
    private val pos = ArrayList<Double>()
    private val idx = ArrayList<Int>()
    private val segments = BodyMeshBuilder.SEGMENTS

    /**
     * Menyambung penampang menjadi tabung. [smooth] = jumlah sisipan halus (Catmull-Rom) antar penampang.
     * [alongZ] = tabung membentang ke depan (telapak kaki): sumbu kedalaman diganti sumbu Y.
     */
    fun tube(rings: List<RingPoint>, axisU: V3, closeStart: Boolean, closeEnd: Boolean, smooth: Int, alongZ: Boolean = false) {
        val dense = densify(rings, smooth)
        val base = pos.size / 3
        val depthAxis = if (alongZ) V3(0.0, 1.0, 0.0) else V3(0.0, 0.0, 1.0)
        for (r in dense) {
            for (j in 0 until segments) {
                val th = 2 * PI * j / segments
                val s = sin(th)
                val d = if (s >= 0) r.front else r.back
                val p = r.center + axisU * (r.halfU * cos(th)) + depthAxis * (d * s)
                pos += p.x; pos += p.y; pos += p.z
            }
        }
        val n = dense.size
        for (i in 0 until n - 1) {
            val a = base + i * segments
            val c = base + (i + 1) * segments
            val mid = (dense[i].center + dense[i + 1].center) * 0.5
            for (j in 0 until segments) {
                val j1 = (j + 1) % segments
                // Arah luar = dari sumbu tabung ke permukaan; segitiga dibalik bila perlu.
                val out = vertex(a + j) + vertex(c + j1) - mid * 2.0
                tri(a + j, a + j1, c + j1, out)
                tri(a + j, c + j1, c + j, out)
            }
        }
        if (closeStart) cap(base, dense.first().center, dense.first().center - dense[1].center)
        if (closeEnd) cap(base + (n - 1) * segments, dense.last().center, dense.last().center - dense[n - 2].center)
    }

    private fun cap(ringStart: Int, center: V3, out: V3) {
        val ci = pos.size / 3
        pos += center.x; pos += center.y; pos += center.z
        for (j in 0 until segments) tri(ci, ringStart + j, ringStart + (j + 1) % segments, out)
    }

    private fun vertex(i: Int) = V3(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2])

    /** Menambah segitiga dengan normal searah [out] (agar sisi depan menghadap keluar). */
    private fun tri(a: Int, b: Int, c: Int, out: V3) {
        val pa = vertex(a)
        val n = (vertex(b) - pa).cross(vertex(c) - pa)
        val dot = n.x * out.x + n.y * out.y + n.z * out.z
        idx += a
        if (dot >= 0) { idx += b; idx += c } else { idx += c; idx += b }
    }

    private fun densify(r: List<RingPoint>, steps: Int): List<RingPoint> {
        if (steps <= 1 || r.size < 3) return r
        val out = ArrayList<RingPoint>()
        for (i in 0 until r.size - 1) {
            val p0 = r[maxOf(i - 1, 0)]
            val p1 = r[i]
            val p2 = r[i + 1]
            val p3 = r[minOf(i + 2, r.size - 1)]
            for (s in 0 until steps) {
                val t = s.toDouble() / steps
                out += RingPoint(
                    V3(cr(p0.center.x, p1.center.x, p2.center.x, p3.center.x, t), cr(p0.center.y, p1.center.y, p2.center.y, p3.center.y, t), cr(p0.center.z, p1.center.z, p2.center.z, p3.center.z, t)),
                    max(0.05, cr(p0.halfU, p1.halfU, p2.halfU, p3.halfU, t)),
                    max(0.05, cr(p0.front, p1.front, p2.front, p3.front, t)),
                    max(0.05, cr(p0.back, p1.back, p2.back, p3.back, t)),
                )
            }
        }
        out += r.last()
        return out
    }

    private fun cr(p0: Double, p1: Double, p2: Double, p3: Double, t: Double): Double {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
    }

    fun finish(): BodyMesh {
        val positions = FloatArray(pos.size) { pos[it].toFloat() }
        val indices = idx.toIntArray()
        val normals = FloatArray(positions.size)
        // Normal halus: rata-rata normal segitiga di sekitar tiap verteks.
        for (t in indices.indices step 3) {
            val a = indices[t] * 3
            val b = indices[t + 1] * 3
            val c = indices[t + 2] * 3
            val ux = positions[b] - positions[a]; val uy = positions[b + 1] - positions[a + 1]; val uz = positions[b + 2] - positions[a + 2]
            val vx = positions[c] - positions[a]; val vy = positions[c + 1] - positions[a + 1]; val vz = positions[c + 2] - positions[a + 2]
            val nx = uy * vz - uz * vy
            val ny = uz * vx - ux * vz
            val nz = ux * vy - uy * vx
            for (v in intArrayOf(a, b, c)) {
                normals[v] += nx; normals[v + 1] += ny; normals[v + 2] += nz
            }
        }
        for (v in normals.indices step 3) {
            val l = sqrt(normals[v] * normals[v] + normals[v + 1] * normals[v + 1] + normals[v + 2] * normals[v + 2])
            if (l > 1e-6f) {
                normals[v] /= l; normals[v + 1] /= l; normals[v + 2] /= l
            }
        }
        return BodyMesh(positions, normals, indices)
    }
}
