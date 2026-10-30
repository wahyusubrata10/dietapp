package com.hawdietpro.app.domain.nutrition

/** Status satu hari terhadap jatah kalori (target + olahraga), dipakai kalender heatmap & rekap. */
enum class DayStatus(val label: String) {
    KOSONG("Tidak ada catatan"),
    KURANG("Kurang"),
    SESUAI("Sesuai"),
    LEBIH("Lebih");

    companion object {
        /** Toleransi ±10% dari jatah dianggap sesuai. */
        const val TOLERANCE = 0.10

        fun of(calories: Double?, budget: Int?): DayStatus = when {
            calories == null || calories <= 0.0 -> KOSONG
            budget == null || budget <= 0 -> SESUAI
            calories > budget * (1 + TOLERANCE) -> LEBIH
            calories < budget * (1 - TOLERANCE) -> KURANG
            else -> SESUAI
        }
    }
}
