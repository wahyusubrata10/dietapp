package com.hawdietpro.app.domain.body

import com.hawdietpro.app.domain.model.Sex
import com.hawdietpro.app.domain.nutrition.TdeeCalculator
import com.hawdietpro.app.domain.nutrition.WeightProjection
import java.time.LocalDate

/** Berat pada satu titik waktu beserta asal angkanya. */
data class BodySnapshot(val date: LocalDate?, val weightKg: Double, val source: Source) {
    enum class Source(val label: String) {
        TIMBANGAN("Timbangan"),
        PERKIRAAN("Perkiraan"),
        PROYEKSI("Proyeksi"),
        TARGET("Target"),
    }
}

/** Mengambil berat pada tanggal tertentu dari proyeksi berat (timbangan > perkiraan > proyeksi). */
object BodyTimeline {

    fun today(p: WeightProjection): LocalDate =
        p.forecast.firstOrNull()?.first ?: p.days.lastOrNull()?.date?.plusDays(1) ?: p.startDate

    /** Tanggal terakhir yang bisa dipilih (ujung proyeksi 4 minggu, atau hari ini). */
    fun lastDate(p: WeightProjection): LocalDate = p.forecast.lastOrNull()?.first ?: today(p)

    fun at(p: WeightProjection, date: LocalDate): BodySnapshot? {
        if (date.isBefore(p.startDate) || date.isAfter(lastDate(p))) return null
        p.actualWeights.lastOrNull { it.epochDay == date.toEpochDay() }?.let {
            return BodySnapshot(date, it.weightKg, BodySnapshot.Source.TIMBANGAN)
        }
        val today = today(p)
        if (date.isAfter(today)) {
            val w = p.forecast.firstOrNull { it.first == date }?.second ?: return null
            return BodySnapshot(date, w, BodySnapshot.Source.PROYEKSI)
        }
        // Berat perkiraan di awal hari = akhir hari sebelumnya.
        val w = if (date == p.startDate) p.startWeight else p.days.firstOrNull { it.date == date.minusDays(1) }?.estimatedWeight ?: p.estimatedToday
        return BodySnapshot(date, w, BodySnapshot.Source.PERKIRAAN)
    }

    fun target(p: WeightProjection): BodySnapshot? = p.targetWeight?.let { BodySnapshot(null, it, BodySnapshot.Source.TARGET) }

    /**
     * Perkiraan lingkar pinggang (cm) dari BMI. Rumus regresi kasar dari data populasi dewasa;
     * hanya untuk gambaran — ukur langsung dengan meteran untuk angka sebenarnya.
     */
    fun waistCm(weightKg: Double, heightCm: Double, sex: Sex): Double {
        val bmi = TdeeCalculator.bmi(weightKg, heightCm)
        return if (sex == Sex.WANITA) 2.15 * bmi + 22.0 else 2.37 * bmi + 26.0
    }
}
