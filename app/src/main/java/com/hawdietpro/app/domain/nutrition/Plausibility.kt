package com.hawdietpro.app.domain.nutrition

import com.hawdietpro.app.domain.model.FoodItem

/**
 * Pemeriksaan kewajaran angka per elemen (hanya untuk penanda "periksa angka ini" di layar review;
 * nilai tidak diubah otomatis).
 */
object Plausibility {
    /** Energi maksimum secara fisik ±9 kkal/g (lemak murni); diberi sedikit toleransi. */
    private const val MAX_KCAL_PER_GRAM = 9.5
    private const val MAX_GRAMS = 1500.0
    private const val MAX_KCAL = 2500.0

    /** null = wajar; selain itu alasan singkat untuk ditampilkan. */
    fun check(item: FoodItem): String? {
        val kcal = item.nutrients.calories
        return when {
            item.grams > MAX_GRAMS -> "Berat ${item.grams.toInt()} g tampak terlalu besar"
            kcal > MAX_KCAL -> "Kalori tampak terlalu tinggi"
            item.grams > 0 && kcal / item.grams > MAX_KCAL_PER_GRAM -> "Kalori per gram tidak wajar"
            else -> null
        }
    }
}
