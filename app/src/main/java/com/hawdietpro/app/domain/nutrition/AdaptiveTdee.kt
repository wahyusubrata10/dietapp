package com.hawdietpro.app.domain.nutrition

import com.hawdietpro.app.domain.model.UserProfile
import com.hawdietpro.app.domain.model.WeightEntry
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Hasil perkiraan kebutuhan kalori (TDEE) yang dipelajari dari catatan makan + timbangan. */
data class AdaptiveTdee(
    /** TDEE dari rumus (Mifflin-St Jeor × tingkat aktivitas) dengan berat terbaru. */
    val formulaTdee: Int,
    /** TDEE yang dipakai aplikasi: rumus yang dikoreksi data sesuai tingkat keyakinan. */
    val tdee: Int,
    /** TDEE murni dari data (tanpa campuran rumus); null bila data belum cukup. */
    val observedTdee: Int?,
    /** Pengali terhadap rumus (tdee / formulaTdee), dipakai juga untuk perkiraan berat. */
    val multiplier: Double,
    /** 0..1: seberapa yakin hasil data dipakai. */
    val confidence: Double,
    val status: Status,
    /** Hari antara timbangan pertama & terakhir dalam jendela hitung. */
    val spanDays: Int,
    val weighIns: Int,
    /** Hari dengan catatan makan di rentang timbangan. */
    val loggedDays: Int,
    /** Tren berat dari timbangan (kg/minggu), null bila belum bisa dihitung. */
    val trendKgPerWeek: Double?,
    /** Rata-rata kalori masuk per hari tercatat di rentang hitung. */
    val avgIntake: Int?,
    /** Langkah berikutnya agar perkiraan makin akurat (null bila sudah akurat). */
    val hint: String?,
) {
    enum class Status(val label: String) {
        BELUM_CUKUP("Belum cukup data"),
        BELAJAR("Sedang belajar"),
        AKURAT("Akurat"),
    }

    /** Selisih data vs rumus (kkal), positif = tubuhmu membakar lebih banyak dari rumus. */
    val differenceFromFormula: Int get() = tdee - formulaTdee
}

/**
 * Kebutuhan kalori adaptif: membandingkan rata-rata kalori masuk dengan tren timbangan.
 *
 * energi keluar = rata-rata kalori masuk − (perubahan berat per hari × 7700)
 * kebutuhan dasar = energi keluar − rata-rata kalori olahraga yang dicatat
 *
 * Olahraga dikurangkan agar tidak terhitung dua kali (olahraga tetap menambah jatah di hari itu).
 * Hasil dicampur dengan rumus sesuai keyakinan (lama data, jumlah timbangan, kelengkapan catatan),
 * dan dibatasi ke rentang fisiologis 1,0–2,2 × BMR agar data yang janggal tidak membuat target ekstrem.
 */
object AdaptiveTdeeEstimator {

    const val WINDOW_DAYS = 35L
    private const val KCAL_PER_KG = 7700.0
    private const val MIN_SPAN_DAYS = 7
    private const val FULL_SPAN_DAYS = 21.0
    private const val FULL_WEIGH_INS = 4.0
    private const val MIN_COVERAGE = 0.5
    private const val FULL_COVERAGE = 0.85
    private const val WATER_PHASE_DAYS = 7

    fun estimate(
        profile: UserProfile,
        today: LocalDate,
        dailyIntake: Map<LocalDate, Double>,
        weights: List<WeightEntry>,
        dailyExercise: Map<LocalDate, Double> = emptyMap(),
        firstLogDate: LocalDate? = dailyIntake.keys.minOrNull(),
    ): AdaptiveTdee {
        val latestWeight = weights.maxByOrNull { it.epochDay }?.weightKg ?: profile.weightKg
        val formula = TdeeCalculator.bmr(profile.sex, latestWeight, profile.heightCm, profile.age) * profile.activityLevel.factor

        fun insufficient(hint: String, span: Int = 0, count: Int = 0, logged: Int = 0, trend: Double? = null, avg: Int? = null) = AdaptiveTdee(
            formulaTdee = formula.roundToInt(),
            tdee = formula.roundToInt(),
            observedTdee = null,
            multiplier = 1.0,
            confidence = 0.0,
            status = AdaptiveTdee.Status.BELUM_CUKUP,
            spanDays = span,
            weighIns = count,
            loggedDays = logged,
            trendKgPerWeek = trend,
            avgIntake = avg,
            hint = hint,
        )

        val windowStart = today.minusDays(WINDOW_DAYS)
        // Satu timbangan per hari (yang terakhir dicatat), hanya di jendela hitung & bukan masa depan.
        val allPoints = weights
            .filter { it.epochDay in windowStart.toEpochDay()..today.toEpochDay() }
            .groupBy { it.epochDay }
            .map { (day, list) -> day to list.last().weightKg }
            .sortedBy { it.first }
        // Minggu pertama diet: turunnya sebagian besar air & glikogen. Abaikan bila data sesudahnya sudah cukup.
        val settled = firstLogDate?.let { f -> allPoints.filter { it.first >= f.toEpochDay() + WATER_PHASE_DAYS } }
        val points = if (settled != null && settled.size >= 2 && settled.last().first - settled.first().first >= MIN_SPAN_DAYS) settled else allPoints
        if (points.size < 2) {
            return insufficient("Timbang badan minimal 2 kali dengan jarak ±1 minggu (idealnya 2–3x seminggu, pagi hari).", count = points.size)
        }
        val first = points.first().first
        val last = points.last().first
        val span = (last - first).toInt()
        if (span < MIN_SPAN_DAYS) {
            return insufficient("Timbangan pertama & terakhir baru berjarak $span hari. Butuh minimal $MIN_SPAN_DAYS hari.", span, points.size)
        }

        val slope = slopePerDay(points)
        val trendPerWeek = slope * 7
        // Kalori di hari-hari antara timbangan pertama (inklusif) & terakhir (eksklusif) yang menjelaskan perubahan berat.
        val days = (0 until span).map { LocalDate.ofEpochDay(first + it) }
        val logged = days.mapNotNull { dailyIntake[it] }
        val coverage = logged.size / span.toDouble()
        val avgIntake = if (logged.isEmpty()) null else logged.average()
        if (avgIntake == null || coverage < MIN_COVERAGE) {
            return insufficient(
                "Catatan makan di rentang timbangan baru ${logged.size} dari $span hari. Catat makan lebih rutin (min. ${(MIN_COVERAGE * 100).toInt()}% hari).",
                span, points.size, logged.size, trendPerWeek, avgIntake?.roundToInt(),
            )
        }
        val avgExercise = days.sumOf { dailyExercise[it] ?: 0.0 } / span

        val meanWeight = points.map { it.second }.average()
        val bmrMean = TdeeCalculator.bmr(profile.sex, meanWeight, profile.heightCm, profile.age)
        val formulaMean = bmrMean * profile.activityLevel.factor
        val rawObserved = avgIntake - slope * KCAL_PER_KG - avgExercise
        val observed = rawObserved.coerceIn(bmrMean * 1.0, bmrMean * 2.2)

        var confidence = min(1.0, span / FULL_SPAN_DAYS) *
            min(1.0, (points.size - 1) / (FULL_WEIGH_INS - 1)) *
            min(1.0, coverage / FULL_COVERAGE)
        // Minggu-minggu awal diet: penurunan cepat sebagian besar air & glikogen, bukan lemak.
        val earlyPhase = firstLogDate != null && first - firstLogDate.toEpochDay() < WATER_PHASE_DAYS
        if (earlyPhase) confidence *= 0.6
        confidence = confidence.coerceIn(0.0, 1.0)

        val ratio = observed / formulaMean
        val multiplier = 1 + confidence * (ratio - 1)
        val tdee = formula * multiplier

        val status = if (confidence >= 0.6) AdaptiveTdee.Status.AKURAT else AdaptiveTdee.Status.BELAJAR
        val hint = when {
            status == AdaptiveTdee.Status.AKURAT -> null
            points.size < FULL_WEIGH_INS -> "Timbang lagi (2–3x seminggu, pagi setelah ke toilet) agar perkiraan makin akurat."
            span < FULL_SPAN_DAYS -> "Butuh data ±${(FULL_SPAN_DAYS - span).toInt()} hari lagi."
            coverage < FULL_COVERAGE -> "Catat semua makan setiap hari agar hasilnya tepat."
            earlyPhase -> "Awal diet, penurunan cepat sebagian adalah air; perkiraan diperhalus dulu."
            else -> null
        }

        return AdaptiveTdee(
            formulaTdee = formula.roundToInt(),
            tdee = tdee.roundToInt(),
            observedTdee = (observed * formula / formulaMean).roundToInt(),
            multiplier = multiplier,
            confidence = confidence,
            status = status,
            spanDays = span,
            weighIns = points.size,
            loggedDays = logged.size,
            trendKgPerWeek = trendPerWeek,
            avgIntake = avgIntake.roundToInt(),
            hint = hint,
        )
    }

    /** Kemiringan regresi linear (kg/hari) dari titik (epochDay, kg). */
    fun slopePerDay(points: List<Pair<Long, Double>>): Double {
        if (points.size < 2) return 0.0
        val x0 = points.first().first
        val xs = points.map { (it.first - x0).toDouble() }
        val ys = points.map { it.second }
        val mx = xs.average()
        val my = ys.average()
        val num = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        val den = xs.sumOf { (it - mx) * (it - mx) }
        return if (den == 0.0) 0.0 else num / max(den, 1e-9)
    }
}
