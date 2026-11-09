package com.hawdietpro.app.domain.nutrition

import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.FoodReference
import com.hawdietpro.app.domain.model.Sex
import kotlin.math.min

/**
 * Magnesium: kebutuhan harian (AKG 2019, Permenkes No. 28/2019) dan perkiraan kandungan
 * bila tidak ada data dari database makanan maupun AI.
 */
object Magnesium {

    /** Dipakai bila target pribadi belum diketahui (rata-rata AKG dewasa). */
    const val DEFAULT_TARGET_MG = 350

    /** Angka Kecukupan Gizi magnesium (mg/hari) untuk dewasa. */
    fun dailyTargetMg(sex: Sex, age: Int): Int = when (sex) {
        Sex.PRIA -> when {
            age < 19 -> 270
            age < 65 -> 360
            else -> 350
        }
        Sex.WANITA -> when {
            age < 19 -> 230
            age < 30 -> 330
            age < 65 -> 340
            else -> 320
        }
    }

    /** Dasar mg/100 g per kategori database (sejalan dengan perkiraan di food_db.json). */
    private val categoryBase = mapOf(
        "karbo" to 15.0, "hidangan" to 22.0, "gorengan" to 25.0, "lauk_nabati" to 40.0, "camilan" to 25.0,
        "lauk_hewani" to 25.0, "sayur" to 22.0, "kuah" to 12.0, "buah" to 15.0, "sambal_saus" to 20.0,
        "minyak_lemak" to 1.0, "susu_olahan" to 12.0, "minuman" to 5.0,
    )

    /**
     * Perkiraan mg/100 g dari kategori + kepadatan serat & protein (makanan tinggi serat/protein
     * seperti kacang, biji-bijian, sayur hijau cenderung tinggi magnesium). Tanpa kategori
     * (produk kemasan, isian manual) dasarnya mengikuti kepadatan energi, sehingga minuman encer
     * tidak dinilai setinggi camilan padat.
     */
    fun estimatePer100g(category: String?, kcalPer100g: Double, fiberPer100g: Double, proteinPer100g: Double): Double {
        if (category == "minyak_lemak") return if (proteinPer100g < 1) 0.0 else 2.0
        val base = categoryBase[category] ?: (3.0 + 0.03 * kcalPer100g.coerceAtLeast(0.0))
        return min(base + 5 * fiberPer100g.coerceAtLeast(0.0) + 0.5 * proteinPer100g.coerceAtLeast(0.0), 120.0)
    }

    /**
     * Magnesium (mg) untuk satu elemen: dari referensi DB bila tersedia, selain itu perkiraan
     * berdasarkan kategori & komposisi elemen itu sendiri.
     */
    fun forItem(item: FoodItem, ref: FoodReference?): Double {
        if (item.grams <= 0) return 0.0
        if (ref != null && ref.hasMagnesium) return NutritionCalculator.round1(ref.per100g.magnesium * item.grams / 100.0)
        val factor = 100.0 / item.grams
        val n = item.nutrients
        val per100 = estimatePer100g(item.category ?: ref?.category, n.calories * factor, n.fiber * factor, n.protein * factor)
        return NutritionCalculator.round1(per100 * item.grams / 100.0)
    }

    /** Mengisi magnesium elemen yang belum punya angka (0). */
    fun fill(item: FoodItem, ref: FoodReference?): FoodItem =
        if (item.nutrients.magnesium > 0) item else item.copy(nutrients = item.nutrients.copy(magnesium = forItem(item, ref)))
}
