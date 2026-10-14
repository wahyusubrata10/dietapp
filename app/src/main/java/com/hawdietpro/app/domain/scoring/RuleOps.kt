package com.hawdietpro.app.domain.scoring

/** Operator perbandingan yang dipakai aturan JSON. Operator tak dikenal → false. */
object RuleOps {
    val SUPPORTED = setOf(">", ">=", "<", "<=", "==", "!=")

    fun evaluate(actual: Double, op: String, expected: Double): Boolean = when (op) {
        ">" -> actual > expected
        ">=" -> actual >= expected
        "<" -> actual < expected
        "<=" -> actual <= expected
        "==" -> actual == expected
        "!=" -> actual != expected
        else -> false
    }
}
