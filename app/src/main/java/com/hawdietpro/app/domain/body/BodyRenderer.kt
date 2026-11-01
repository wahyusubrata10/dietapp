package com.hawdietpro.app.domain.body

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Perender perangkat lunak tanpa dependensi: putar (yaw/pitch), proyeksi perspektif ringan,
 * pencahayaan 3 titik (utama, isi, tepi) per verteks lalu diinterpolasi (Gouraud), dan z-buffer
 * per piksel sehingga perpotongan antar-bagian tubuh tetap rapi dari sudut mana pun.
 * Hasilnya larik piksel ARGB (latar transparan) yang langsung bisa dijadikan Bitmap.
 * Buffer dipakai ulang antar-frame agar tidak membebani GC saat diputar.
 */
class BodyRenderer(private val mesh: BodyMesh) {
    private val n = mesh.vertexCount
    private val sx = FloatArray(n)
    private val sy = FloatArray(n)
    private val sz = FloatArray(n)
    private val cr = FloatArray(n)
    private val cg = FloatArray(n)
    private val cb = FloatArray(n)

    var width = 0
        private set
    var height = 0
        private set

    /** Piksel ARGB hasil render terakhir (baris demi baris, lebar [width]). */
    var pixels = IntArray(0)
        private set
    private var depth = FloatArray(0)

    /** Titik tertinggi mesh (cm) untuk menyamakan skala dua tubuh yang dibandingkan. */
    val topY: Float = run {
        var m = 0f
        for (i in 1 until mesh.positions.size step 3) m = max(m, mesh.positions[i])
        m
    }

    /**
     * @param yaw putaran kiri-kanan (radian), @param pitch kemiringan (radian)
     * @param pxPerCm skala, @param floorY posisi lantai (piksel dari atas), @param base warna dasar ARGB
     */
    fun render(width: Int, height: Int, yaw: Float, pitch: Float, pxPerCm: Float, floorY: Float, base: Int): IntArray {
        if (width != this.width || height != this.height) {
            this.width = width
            this.height = height
            pixels = IntArray(width * height)
            depth = FloatArray(width * height)
        }
        java.util.Arrays.fill(pixels, 0)
        java.util.Arrays.fill(depth, Float.NEGATIVE_INFINITY)
        transform(yaw, pitch, pxPerCm, width / 2f, floorY, base)
        val idx = mesh.indices
        for (t in 0 until mesh.triangleCount) raster(idx[t * 3], idx[t * 3 + 1], idx[t * 3 + 2])
        return pixels
    }

    private fun transform(yaw: Float, pitch: Float, pxPerCm: Float, centerX: Float, floorY: Float, base: Int) {
        val p = mesh.positions
        val nm = mesh.normals
        val cy = cos(yaw); val sny = sin(yaw)
        val cp = cos(pitch); val snp = sin(pitch)
        val midY = topY / 2
        val camDist = topY * 4.5f
        val br = (base shr 16 and 0xFF) / 255f
        val bg = (base shr 8 and 0xFF) / 255f
        val bb = (base and 0xFF) / 255f
        // Arah cahaya dalam ruang kamera: cahaya tetap, tubuh yang berputar.
        val kx = -0.42f; val ky = 0.56f; val kz = 0.71f
        val fx = 0.80f; val fy = 0.10f; val fz = 0.59f
        val hl = sqrt(kx * kx + ky * ky + (kz + 1f) * (kz + 1f))
        val hx = kx / hl; val hy = ky / hl; val hz = (kz + 1f) / hl
        for (i in 0 until n) {
            val x = p[i * 3]; val y = p[i * 3 + 1] - midY; val z = p[i * 3 + 2]
            val x1 = x * cy + z * sny
            val z1 = -x * sny + z * cy
            val y2 = y * cp - z1 * snp
            val z2 = y * snp + z1 * cp
            val persp = camDist / (camDist - z2)
            sx[i] = centerX + x1 * pxPerCm * persp
            sy[i] = floorY - (y2 + midY) * pxPerCm * persp
            sz[i] = z2

            val nx0 = nm[i * 3]; val ny0 = nm[i * 3 + 1]; val nz0 = nm[i * 3 + 2]
            val nx = nx0 * cy + nz0 * sny
            val nz1 = -nx0 * sny + nz0 * cy
            val ny = ny0 * cp - nz1 * snp
            val nz = ny0 * snp + nz1 * cp
            val key = max(0f, nx * kx + ny * ky + nz * kz)
            val fill = max(0f, nx * fx + ny * fy + nz * fz)
            val rim = (1f - max(0f, nz)).pow(3) * 0.3f
            val spec = max(0f, nx * hx + ny * hy + nz * hz).pow(24) * 0.2f
            val light = 0.3f + 0.62f * key + 0.16f * fill
            cr[i] = br * light + rim + spec
            cg[i] = bg * light + rim + spec
            cb[i] = bb * light + rim + spec
        }
    }

    private fun raster(a: Int, b: Int, c: Int) {
        val ax = sx[a]; val ay = sy[a]; val bx = sx[b]; val by = sy[b]; val cx = sx[c]; val cy = sy[c]
        val area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        // Sumbu y layar terbalik → segitiga yang menghadap kamera punya luas negatif; sisanya dibuang.
        if (area > -1e-6f) return
        val minX = max(0, floor(min(ax, min(bx, cx))).toInt())
        val maxX = min(width - 1, ceil(max(ax, max(bx, cx))).toInt())
        val minY = max(0, floor(min(ay, min(by, cy))).toInt())
        val maxY = min(height - 1, ceil(max(ay, max(by, cy))).toInt())
        if (minX > maxX || minY > maxY) return
        val inv = 1f / area
        // Fungsi tepi inkremental: w0 (bobot a), w1 (bobot b), w2 (bobot c).
        val w0dx = (by - cy) * inv
        val w1dx = (cy - ay) * inv
        val za = sz[a]; val zb = sz[b]; val zc = sz[c]
        val ra = cr[a]; val rb = cr[b]; val rc = cr[c]
        val ga = cg[a]; val gb = cg[b]; val gc = cg[c]
        val ba = cb[a]; val bbv = cb[b]; val bc = cb[c]
        for (y in minY..maxY) {
            val py = y + 0.5f
            val px0 = minX + 0.5f
            var w0 = ((bx - px0) * (cy - py) - (by - py) * (cx - px0)) * inv
            var w1 = ((cx - px0) * (ay - py) - (cy - py) * (ax - px0)) * inv
            var o = y * width + minX
            for (x in minX..maxX) {
                val w2 = 1f - w0 - w1
                if (w0 >= 0f && w1 >= 0f && w2 >= 0f) {
                    val z = w0 * za + w1 * zb + w2 * zc
                    if (z > depth[o]) {
                        depth[o] = z
                        pixels[o] = argb(w0 * ra + w1 * rb + w2 * rc, w0 * ga + w1 * gb + w2 * gc, w0 * ba + w1 * bbv + w2 * bc)
                    }
                }
                w0 += w0dx
                w1 += w1dx
                o++
            }
        }
    }

    private fun argb(r: Float, g: Float, b: Float): Int {
        val ri = (min(1f, max(0f, r)) * 255f + 0.5f).toInt()
        val gi = (min(1f, max(0f, g)) * 255f + 0.5f).toInt()
        val bi = (min(1f, max(0f, b)) * 255f + 0.5f).toInt()
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }
}
