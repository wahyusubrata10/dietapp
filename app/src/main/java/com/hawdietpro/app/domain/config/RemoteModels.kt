package com.hawdietpro.app.domain.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Kontrak minimum semua file remote config. */
interface VersionedConfig {
    val version: Int
    /** Versi aplikasi minimum yang memahami format file ini. */
    val minAppVersionCode: Int
}

@Serializable
data class AppConfig(
    override val version: Int,
    override val minAppVersionCode: Int = 1,
    /** Versi aplikasi di bawah ini diminta update (Lapis 1). */
    val minSupportedVersionCode: Int = 1,
    val features: Map<String, Boolean> = emptyMap(),
    val announcement: Announcement? = null,
    val updateCheckIntervalHours: Int = 6,
    val tips: List<String> = emptyList(),
    val texts: Map<String, String> = emptyMap(),
) : VersionedConfig {
    fun isEnabled(flag: String, default: Boolean = true): Boolean = features[flag] ?: default
    fun text(key: String, default: String): String = texts[key]?.takeIf { it.isNotBlank() } ?: default
}

@Serializable
data class Announcement(
    val id: String,
    val title: String,
    val message: String,
    /** info, warning, success */
    val level: String = "info",
    val url: String? = null,
)

@Serializable
data class GeminiPromptConfig(
    override val version: Int,
    override val minAppVersionCode: Int = 1,
    val model: String,
    val fallbackModels: List<String> = emptyList(),
    val temperature: Double = 0.2,
    val maxImageSize: Int = 1024,
    val jpegQuality: Int = 80,
    val systemPrompt: String,
    val userPrompt: String,
    val responseSchema: JsonObject,
) : VersionedConfig

@Serializable
data class FoodDbFile(
    override val version: Int,
    override val minAppVersionCode: Int = 1,
    val note: String? = null,
    val categories: Map<String, String> = emptyMap(),
    val foods: List<FoodDbItem>,
) : VersionedConfig

@Serializable
data class FoodDbItem(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val category: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double = 0.0,
    val servingG: Double = 100.0,
    val servingLabel: String? = null,
    val source: String = "POPULER",
)

@Serializable
data class InsightRulesFile(
    override val version: Int,
    override val minAppVersionCode: Int = 1,
    val vegetableCategories: List<String> = listOf("sayur"),
    val vegetableKeywords: List<String> = emptyList(),
    val friedKeywords: List<String> = emptyList(),
    val santanKeywords: List<String> = emptyList(),
    val sweetDrinkKeywords: List<String> = emptyList(),
    val targetTolerancePct: Double = 10.0,
    val lateNightHour: Int = 21,
    val mealScoring: MealScoringConfig,
    val insights: List<InsightRule> = emptyList(),
    val maxInsights: Int = 5,
) : VersionedConfig

@Serializable
data class MealScoringConfig(
    val base: Double = 100.0,
    val rules: List<ScoreRule>,
    val grades: List<GradeThreshold>,
    val balancedLabel: String = "Seimbang",
    val maxSuggestions: Int = 3,
)

@Serializable
data class ScoreRule(
    val id: String,
    val metric: String,
    val op: String,
    val value: Double,
    val points: Double,
    val label: String,
    val suggestion: String? = null,
    /** Aturan hanya berlaku jika kalori makan ≥ nilai ini. */
    val minCalories: Double? = null,
    /** Kosong = semua waktu makan. */
    val mealTypes: List<String> = emptyList(),
)

@Serializable
data class GradeThreshold(val grade: String, val min: Double)

@Serializable
data class InsightRule(
    val id: String,
    val metric: String,
    val op: String,
    val value: Double,
    /** DAY, WEEK, MONTH */
    val periods: List<String> = listOf("WEEK", "MONTH"),
    /** positive, info, warning */
    val severity: String = "info",
    val priority: Int = 50,
    val template: String,
    /** Minimal hari tercatat agar insight dianggap bermakna. */
    val minDaysLogged: Int = 1,
)
