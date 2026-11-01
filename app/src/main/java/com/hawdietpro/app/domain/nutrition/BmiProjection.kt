package com.hawdietpro.app.domain.nutrition

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Kategori BMI untuk orang Asia (WHO Asia-Pasifik, 2000) — batas lebih rendah dari standar global. */
enum class BmiClass(val label: String, val lower: Double, val upper: Double) {
    KURANG("Berat badan kurang", 0.0, 18.5),
    NORMAL("Normal", 18.5, 23.0),
    BERLEBIH("Berlebih", 23.0, 25.0),
    OBESITAS_1("Obesitas I", 25.0, 30.0),
    OBESITAS_2("Obesitas II", 30.0, Double.POSITIVE_INFINITY);

    companion object {
        fun of(bmi: Double): BmiClass = entries.firstOrNull { bmi < it.upper } ?: OBESITAS_2
    }
}

/** Titik pencapaian: berat yang membuat BMI masuk ke [reachedClass]. */
data class BmiMilestone(
    val reachedClass: BmiClass,
    /** Berat (kg, 1 desimal) yang menandai masuk ke kategori ini. */
    val weightKg: Double,
    /** BMI pada berat tersebut. */
    val bmi: Double,
    /** Sudah tercapai menurut berat perkiraan hari ini. */
    val reached: Boolean,
    /** Perkiraan tanggal tercapai dengan pola makan saat ini (null bila belum searah / terlalu jauh). */
    val date: LocalDate?,
)

data class BmiProjection(
    val heightCm: Double,
    /** BMI dari berat perkiraan hari ini. */
    val current: Double,
    val currentClass: BmiClass,
    /** BMI pada titik awal perhitungan. */
    val start: Double,
    /** BMI dari timbangan terakhir (null bila belum pernah menimbang). */
    val actual: Double?,
    /** Rentang berat normal untuk tinggi badan ini (BMI 18,5–22,9). */
    val healthyMinKg: Double,
    val healthyMaxKg: Double,
    /** Perubahan berat agar masuk rentang normal: negatif = turun, positif = naik, 0 = sudah normal. */
    val kgToHealthy: Double,
    /** Proyeksi BMI harian ke depan (mengikuti proyeksi berat). */
    val forecast: List<Pair<LocalDate, Double>>,
    /** BMI pada berat target profil. */
    val target: Double?,
    val targetClass: BmiClass?,
    /** Batas kategori menuju rentang normal, urut dari yang terdekat dengan titik awal. */
    val milestones: List<BmiMilestone>,
)

/**
 * Proyeksi BMI = proyeksi berat ([WeightProjector]) dibagi kuadrat tinggi badan.
 * Tidak ada asumsi tambahan: BMI naik-turun persis mengikuti berat perkiraan dari catatan kalori.
 */
object BmiProjector {
    const val HEALTHY_MIN = 18.5
    const val HEALTHY_MAX = 22.9
    private const val MIN_RATE_KCAL = 25.0
    private const val MAX_DAYS = 365L * 3

    fun weightFor(bmi: Double, heightCm: Double): Double {
        val m = heightCm / 100.0
        return bmi * m * m
    }

    /** null bila tinggi badan tidak diketahui / tidak wajar. */
    fun project(p: WeightProjection): BmiProjection? {
        val h = p.heightCm
        if (h !in 100.0..250.0) return null
        fun bmi(w: Double) = TdeeCalculator.bmi(w, h)

        val now = p.estimatedToday
        val current = bmi(now)
        val start = bmi(p.startWeight)
        val healthyMin = weightFor(HEALTHY_MIN, h)
        val healthyMax = weightFor(HEALTHY_MAX, h)
        val kgToHealthy = when {
            now > healthyMax -> healthyMax - now
            now < healthyMin -> healthyMin - now
            else -> 0.0
        }

        // Laju harian dari rata-rata surplus/defisit terbaru (sama seperti proyeksi berat).
        val perDay = p.recentDailyBalance?.takeIf { abs(it) > MIN_RATE_KCAL }?.div(WeightProjector.KCAL_PER_KG)
        val today = p.forecast.firstOrNull()?.first
        fun dateFor(weight: Double): LocalDate? {
            if (perDay == null || today == null) return null
            val diff = weight - now
            if (diff * perDay <= 0) return null
            val days = ceil(diff / perDay).toLong()
            return if (days in 1..MAX_DAYS) today.plusDays(days) else null
        }

        val currentClass = BmiClass.of(current)
        val startClass = BmiClass.of(start)
        val milestones = ArrayList<BmiMilestone>()
        val highest = maxOf(currentClass, startClass)
        if (highest > BmiClass.NORMAL) {
            // Turun: setiap batas atas kategori di bawah kategori tertinggi, sampai masuk Normal.
            for (i in highest.ordinal - 1 downTo BmiClass.NORMAL.ordinal) {
                val cls = BmiClass.entries[i]
                var w = floor(weightFor(cls.upper, h) * 10) / 10
                if (bmi(w) >= cls.upper) w -= 0.1
                val reached = now <= w
                milestones += BmiMilestone(cls, w, bmi(w), reached, if (reached) null else dateFor(w))
            }
        } else if (minOf(currentClass, startClass) == BmiClass.KURANG) {
            // Naik: masuk rentang normal dari bawah.
            var w = ceil(weightFor(HEALTHY_MIN, h) * 10) / 10
            if (bmi(w) < HEALTHY_MIN) w += 0.1
            val reached = now >= w
            milestones += BmiMilestone(BmiClass.NORMAL, w, bmi(w), reached, if (reached) null else dateFor(w))
        }

        val target = p.targetWeight?.let(::bmi)
        return BmiProjection(
            heightCm = h,
            current = current,
            currentClass = currentClass,
            start = start,
            actual = p.latestActual?.let { bmi(it.weightKg) },
            healthyMinKg = healthyMin,
            healthyMaxKg = healthyMax,
            kgToHealthy = kgToHealthy,
            forecast = p.forecast.map { (d, w) -> d to bmi(w) },
            target = target,
            targetClass = target?.let(BmiClass::of),
            milestones = milestones,
        )
    }
}
