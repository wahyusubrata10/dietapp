package com.hawdietpro.app.domain.product

import com.hawdietpro.app.domain.model.Nutrients
import kotlin.math.abs

/** Produk kemasan yang dikenali dari barcode. Semua angka gizi per 100 g/ml. */
data class PackagedProduct(
    val barcode: String,
    val name: String,
    val brand: String? = null,
    val per100: Nutrients,
    /** Takaran saji (g/ml) dari label, bila ada. */
    val servingG: Double? = null,
    /** Isi bersih kemasan (g/ml), bila ada. */
    val packageG: Double? = null,
    val source: String,
    /** false = data kalori/makro utama tidak lengkap. */
    val complete: Boolean = true,
) {
    val displayName: String
        get() = brand?.takeIf { it.isNotBlank() && !name.contains(it, ignoreCase = true) }?.let { "$it $name" } ?: name

    /** Kalori tidak cocok dengan makronya (salah ketik di database / label terbaca keliru). */
    val suspicious: Boolean
        get() {
            val atwater = 4 * per100.protein + 4 * per100.carbs + 9 * per100.fat
            if (per100.calories <= 0 && atwater <= 0) return false
            return abs(per100.calories - atwater) > maxOf(40.0, per100.calories * 0.25)
        }

    fun nutrientsFor(grams: Double): Nutrients = per100 * (grams.coerceAtLeast(0.0) / 100.0)

    companion object {
        const val SOURCE_OFF = "OPEN_FOOD_FACTS"
        const val SOURCE_LABEL = "LABEL_AI"
        const val SOURCE_MANUAL = "MANUAL"

        fun sourceLabel(source: String) = when (source) {
            SOURCE_OFF -> "Open Food Facts"
            SOURCE_LABEL -> "Label (dibaca AI)"
            else -> "Diisi sendiri"
        }

        /** Barcode produk ritel yang didukung: EAN-8, EAN-13, UPC-A (12), GTIN-14. */
        fun isValidBarcode(code: String): Boolean = code.length in setOf(8, 12, 13, 14) && code.all(Char::isDigit)

        /** Mengubah angka "per sajian" di label menjadi per 100 g. Null bila takaran saji tidak diketahui. */
        fun per100FromServing(perServing: Nutrients, servingG: Double?): Nutrients? =
            servingG?.takeIf { it > 0 }?.let { perServing * (100.0 / it) }
    }
}
