package com.hawdietpro.app.domain.nutrition

import com.hawdietpro.app.domain.model.UserProfile
import com.hawdietpro.app.domain.model.WeightEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil

/** Satu hari dalam perhitungan perkiraan berat. */
data class DayBalance(
    val date: LocalDate,
    /** Kalori masuk tercatat (null = tidak ada catatan). */
    val intake: Double?,
    /** Kebutuhan energi hari itu (TDEE dengan berat perkiraan saat itu). */
    val tdee: Double,
    /** Surplus (+) / defisit (−) yang dihitung; 0 bila hari tidak dihitung. */
    val balance: Double,
    /** Berat perkiraan pada akhir hari. */
    val estimatedWeight: Double,
    /** false = tidak ada catatan sama sekali sehingga dianggap netral. */
    val counted: Boolean,
    /** Kalori aktif dari olahraga yang dicatat (ditambahkan ke energi keluar). */
    val exercise: Double = 0.0,
)

data class WeightProjection(
    val startDate: LocalDate,
    val startWeight: Double,
    val days: List<DayBalance>,
    /** Berat perkiraan hari ini (berdasarkan catatan sampai kemarin). */
    val estimatedToday: Double,
    val cumulativeBalance: Double,
    val estimatedChange: Double,
    val latestActual: WeightEntry?,
    /** Semua catatan timbangan (urut tanggal). */
    val actualWeights: List<WeightEntry>,
    /** Perubahan berat aktual sejak berat awal. */
    val actualChange: Double?,
    val countedDays: Int,
    val skippedDays: Int,
    /** Rata-rata surplus/defisit harian 14 hari terakhir yang dihitung. */
    val recentDailyBalance: Double?,
    /** Proyeksi berat per hari ke depan (28 hari) dengan rata-rata terbaru. */
    val forecast: List<Pair<LocalDate, Double>>,
    val targetWeight: Double?,
    /** Perkiraan tanggal mencapai berat target (null bila tidak searah / tanpa target). */
    val targetDate: LocalDate?,
)

/**
 * Perkiraan berat harian dari keseimbangan energi:
 * berat(besok) = berat(hari ini) + (kalori masuk − TDEE − kalori olahraga) / 7700.
 * TDEE dihitung ulang setiap hari memakai berat perkiraan (BMR ikut turun saat berat turun).
 * Setiap hari yang punya catatan dihitung apa adanya, berapapun jumlahnya (catatan pengguna dianggap valid).
 * Hanya hari tanpa catatan sama sekali yang dianggap netral (tidak menambah/mengurangi).
 */
object WeightProjector {

    const val KCAL_PER_KG = 7700.0
    const val FORECAST_DAYS = 28L
    private const val RECENT_WINDOW = 14

    fun project(
        profile: UserProfile,
        start: LocalDate,
        startWeight: Double,
        today: LocalDate,
        dailyIntake: Map<LocalDate, Double>,
        weights: List<WeightEntry>,
        dailyExercise: Map<LocalDate, Double> = emptyMap(),
        /** Koreksi TDEE dari data (lihat [AdaptiveTdeeEstimator]); 1.0 = rumus murni. */
        tdeeMultiplier: Double = 1.0,
    ): WeightProjection {
        val days = ArrayList<DayBalance>()
        var weight = startWeight
        var cumulative = 0.0
        var date = start
        // Hari ini belum selesai → hanya hari-hari sebelum hari ini yang dihitung.
        while (date.isBefore(today)) {
            val bmr = TdeeCalculator.bmr(profile.sex, weight, profile.heightCm, profile.age)
            val tdee = bmr * profile.activityLevel.factor * tdeeMultiplier
            val intake = dailyIntake[date]
            val exercise = dailyExercise[date] ?: 0.0
            // Hari tanpa catatan makan tetap netral walau ada olahraga: asupan hari itu tidak diketahui.
            val counted = intake != null
            val balance = if (intake != null) intake - tdee - exercise else 0.0
            weight += balance / KCAL_PER_KG
            cumulative += balance
            days += DayBalance(date, intake, tdee, balance, weight, counted, exercise)
            date = date.plusDays(1)
        }

        val sortedWeights = weights.sortedBy { it.epochDay }
        val latest = sortedWeights.lastOrNull()
        val recent = days.filter { it.counted }.takeLast(RECENT_WINDOW)
        val recentAvg = if (recent.size >= 3) recent.map { it.balance }.average() else null

        val forecast = ArrayList<Pair<LocalDate, Double>>()
        if (recentAvg != null) {
            var w = weight
            var d = today
            for (i in 0 until FORECAST_DAYS) {
                forecast += d to w
                // Defisit relatif terhadap TDEE ikut menyesuaikan perubahan berat; cukup linear untuk 4 minggu.
                w += recentAvg / KCAL_PER_KG
                d = d.plusDays(1)
            }
            forecast += d to w
        }

        val target = profile.targetWeightKg
        val targetDate = if (target != null && recentAvg != null && abs(recentAvg) > 25) {
            val diff = target - weight
            val perDay = recentAvg / KCAL_PER_KG
            if (diff == 0.0) today
            else if (diff * perDay > 0) {
                val daysNeeded = ceil(diff / perDay).toLong()
                if (daysNeeded in 1..(365L * 3)) today.plusDays(daysNeeded) else null
            } else null
        } else null

        return WeightProjection(
            startDate = start,
            startWeight = startWeight,
            days = days,
            estimatedToday = weight,
            cumulativeBalance = cumulative,
            estimatedChange = weight - startWeight,
            latestActual = latest,
            actualWeights = sortedWeights,
            actualChange = latest?.let { it.weightKg - startWeight },
            countedDays = days.count { it.counted },
            skippedDays = days.count { !it.counted },
            recentDailyBalance = recentAvg,
            forecast = forecast,
            targetWeight = target,
            targetDate = targetDate,
        )
    }

    /** Titik awal: catatan berat paling awal (atau berat profil) & tanggal paling awal dari makan/berat. */
    fun startPoint(profile: UserProfile, firstMealDate: LocalDate?, weights: List<WeightEntry>, today: LocalDate): Pair<LocalDate, Double> {
        val firstWeight = weights.minByOrNull { it.epochDay }
        val firstWeightDate = firstWeight?.let { LocalDate.ofEpochDay(it.epochDay) }
        val start = listOfNotNull(firstMealDate, firstWeightDate, today).minByOrNull { it.toEpochDay() } ?: today
        val startWeight = firstWeight?.weightKg ?: profile.weightKg
        return start to startWeight
    }

    fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)
}
