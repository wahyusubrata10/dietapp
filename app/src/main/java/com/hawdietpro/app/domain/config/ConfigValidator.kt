package com.hawdietpro.app.domain.config

import com.hawdietpro.app.domain.scoring.RuleOps

/**
 * Validasi semantik file remote config (setelah JSON berhasil di-parse).
 * Mengembalikan daftar masalah; kosong = valid.
 */
object ConfigValidator {

    fun validate(config: VersionedConfig): List<String> = when (config) {
        is AppConfig -> buildList {
            if (config.version < 1) add("version harus ≥ 1")
            if (config.updateCheckIntervalHours !in 1..168) add("updateCheckIntervalHours di luar 1–168")
        }
        is GeminiPromptConfig -> buildList {
            if (config.model.isBlank()) add("model kosong")
            if (config.systemPrompt.isBlank()) add("systemPrompt kosong")
            if (config.userPrompt.isBlank()) add("userPrompt kosong")
            if (!config.responseSchema.containsKey("properties")) add("responseSchema tanpa properties")
            if (config.maxImageSize !in 256..2048) add("maxImageSize di luar 256–2048")
            if (config.jpegQuality !in 40..100) add("jpegQuality di luar 40–100")
            if (config.temperature !in 0.0..2.0) add("temperature di luar 0–2")
            if (config.attemptTimeoutSeconds !in 10..120) add("attemptTimeoutSeconds di luar 10–120")
            if (config.hedgeAfterSeconds !in 0..90) add("hedgeAfterSeconds di luar 0–90")
            if (config.totalTimeoutSeconds !in 20..300) add("totalTimeoutSeconds di luar 20–300")
        }
        is FoodDbFile -> buildList {
            if (config.foods.isEmpty()) add("foods kosong")
            val ids = config.foods.map { it.id }
            if (ids.size != ids.toSet().size) add("id makanan duplikat")
            config.foods.forEach { f ->
                if (f.id.isBlank() || f.name.isBlank()) add("makanan tanpa id/nama")
                if (f.kcal < 0 || f.protein < 0 || f.carbs < 0 || f.fat < 0 || f.fiber < 0) add("${f.id}: nilai negatif")
                if (f.kcal > 950) add("${f.id}: kalori per 100 g tidak wajar")
                if (f.servingG <= 0) add("${f.id}: porsi tidak valid")
            }
        }
        is InsightRulesFile -> buildList {
            if (config.mealScoring.grades.isEmpty()) add("grades kosong")
            config.mealScoring.rules.forEach { if (it.op !in RuleOps.SUPPORTED) add("operator ${it.op} tidak dikenal") }
            config.insights.forEach { if (it.op !in RuleOps.SUPPORTED) add("operator ${it.op} tidak dikenal") }
        }
        else -> emptyList()
    }
}
