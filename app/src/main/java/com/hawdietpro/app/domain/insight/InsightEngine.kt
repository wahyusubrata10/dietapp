package com.hawdietpro.app.domain.insight

import com.hawdietpro.app.domain.config.InsightRulesFile
import com.hawdietpro.app.domain.scoring.RuleOps
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class Insight(
    val id: String,
    val message: String,
    /** positive, info, warning */
    val severity: String,
    val priority: Int,
)

/** Mengevaluasi aturan insight dari insight_rules.json terhadap statistik periode. */
class InsightEngine(private val rules: InsightRulesFile) {

    fun evaluate(stats: PeriodStats, period: Period): List<Insight> {
        val daysLogged = stats.metrics["daysLogged"]?.toInt() ?: 0
        if (daysLogged == 0) return emptyList()
        return rules.insights
            .asSequence()
            .filter { period.name in it.periods }
            .filter { daysLogged >= it.minDaysLogged }
            .mapNotNull { rule ->
                val actual = stats.metrics[rule.metric] ?: return@mapNotNull null
                if (!RuleOps.evaluate(actual, rule.op, rule.value)) return@mapNotNull null
                val message = fill(rule.template, actual, period, stats.texts) ?: return@mapNotNull null
                Insight(rule.id, message, rule.severity, rule.priority)
            }
            .sortedByDescending { it.priority }
            .take(rules.maxInsights)
            .toList()
    }

    /** Mengisi placeholder {value}, {absValue}, {period}, dan teks lain. Null bila ada placeholder tak terisi. */
    internal fun fill(template: String, value: Double, period: Period, texts: Map<String, String>): String? {
        var out = template
            .replace("{value}", format(value))
            .replace("{absValue}", format(abs(value)))
            .replace("{period}", period.label)
        texts.forEach { (k, v) -> out = out.replace("{$k}", v) }
        return if (PLACEHOLDER.containsMatchIn(out)) null else out
    }

    private fun format(value: Double): String {
        val rounded1 = (value * 10).roundToInt() / 10.0
        return if (rounded1 == rounded1.toInt().toDouble()) rounded1.toInt().toString()
        else String.format(Locale.forLanguageTag("id-ID"), "%.1f", rounded1)
    }

    private companion object {
        val PLACEHOLDER = Regex("\\{[a-zA-Z]+\\}")
    }
}
