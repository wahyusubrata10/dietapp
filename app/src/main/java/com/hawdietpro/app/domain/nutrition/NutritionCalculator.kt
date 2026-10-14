package com.hawdietpro.app.domain.nutrition

import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.FoodReference
import com.hawdietpro.app.domain.model.NutritionSource
import com.hawdietpro.app.domain.model.Nutrients
import kotlin.math.roundToInt

object NutritionCalculator {

    /** Gizi untuk [grams] gram dari referensi per 100 g. */
    fun fromReference(ref: FoodReference, grams: Double): Nutrients =
        ref.per100g * (grams.coerceAtLeast(0.0) / 100.0)

    /**
     * Menghitung ulang gizi elemen saat gram diubah.
     * - Sumber DB: dihitung dari referensi per 100 g.
     * - Sumber AI/manual: diskalakan proporsional dari angka sebelumnya.
     */
    fun rescale(item: FoodItem, newGrams: Double, ref: FoodReference?): FoodItem {
        val grams = newGrams.coerceAtLeast(0.0)
        val nutrients = when {
            ref != null && item.source == NutritionSource.DB -> fromReference(ref, grams)
            item.grams > 0 -> item.nutrients * (grams / item.grams)
            else -> item.nutrients
        }
        return item.copy(grams = grams, nutrients = nutrients)
    }

    /** Menerapkan referensi DB ke elemen (gram tetap, angka gizi diganti DB). */
    fun applyReference(item: FoodItem, ref: FoodReference): FoodItem = item.copy(
        nutrients = fromReference(ref, item.grams),
        source = NutritionSource.DB,
        foodRefId = ref.id,
        category = ref.category,
        aiNutrients = item.aiNutrients ?: if (item.source == NutritionSource.AI) item.nutrients else null,
    )

    fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0
}
