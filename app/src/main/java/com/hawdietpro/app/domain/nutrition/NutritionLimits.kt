package com.hawdietpro.app.domain.nutrition

/** Batas konsumsi harian Gula-Garam-Lemak (Permenkes No. 30/2013). */
object NutritionLimits {
    /** Gula maksimal per hari (g) ≈ 4 sendok makan. */
    const val SUGAR_G = 50.0

    /** Natrium maksimal per hari (mg) ≈ 5 g garam (1 sendok teh). */
    const val SODIUM_MG = 2000.0

    fun saltGramsFromSodium(sodiumMg: Double): Double = sodiumMg * 2.5 / 1000
}
