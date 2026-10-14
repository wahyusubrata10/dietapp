package com.hawdietpro.app.domain.model

import kotlinx.serialization.Serializable

/** Kandungan gizi. Satuan: kkal untuk kalori, gram untuk makro. */
@Serializable
data class Nutrients(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
) {
    operator fun plus(other: Nutrients) = Nutrients(
        calories + other.calories,
        protein + other.protein,
        carbs + other.carbs,
        fat + other.fat,
        fiber + other.fiber,
    )

    operator fun times(factor: Double) = Nutrients(
        calories * factor,
        protein * factor,
        carbs * factor,
        fat * factor,
        fiber * factor,
    )

    /** Persentase energi dari tiap makro (protein & karbo 4 kkal/g, lemak 9 kkal/g). */
    val macroEnergy: MacroSplit
        get() {
            val p = protein * 4
            val c = carbs * 4
            val f = fat * 9
            val total = p + c + f
            if (total <= 0.0) return MacroSplit(0.0, 0.0, 0.0)
            return MacroSplit(p / total * 100, c / total * 100, f / total * 100)
        }

    companion object {
        val ZERO = Nutrients()
    }
}

data class MacroSplit(val proteinPct: Double, val carbsPct: Double, val fatPct: Double)

fun Iterable<Nutrients>.sum(): Nutrients = fold(Nutrients.ZERO) { acc, n -> acc + n }
