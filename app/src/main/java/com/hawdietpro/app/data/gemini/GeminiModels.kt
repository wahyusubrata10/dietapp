package com.hawdietpro.app.data.gemini

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---------- Request ----------

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null,
)

@Serializable
data class Content(
    val role: String? = null,
    val parts: List<Part>,
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null,
)

@Serializable
data class InlineData(val mimeType: String, val data: String)

@Serializable
data class GenerationConfig(
    val temperature: Double? = null,
    val responseMimeType: String? = null,
    val responseSchema: JsonObject? = null,
    val thinkingConfig: ThinkingConfig? = null,
)

/** Mengatur "thinking" model. Untuk mengenali makanan cukup minimal agar jauh lebih cepat. */
@Serializable
data class ThinkingConfig(
    val thinkingBudget: Int? = null,
    val thinkingLevel: String? = null,
)

// ---------- Response ----------

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList(),
    val promptFeedback: PromptFeedback? = null,
)

@Serializable
data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
)

@Serializable
data class PromptFeedback(val blockReason: String? = null)

@Serializable
data class ApiErrorEnvelope(val error: ApiError? = null)

@Serializable
data class ApiError(
    val code: Int = 0,
    val message: String = "",
    val status: String = "",
    val details: List<JsonObject> = emptyList(),
)

// ---------- Hasil analisa (sesuai responseSchema di gemini_prompt.json) ----------

@Serializable
data class GeminiFoodResult(
    @SerialName("adalah_makanan") val isFood: Boolean = true,
    @SerialName("nama_menu") val menuName: String = "",
    val confidence: Double = 0.0,
    @SerialName("elemen") val elements: List<GeminiElement> = emptyList(),
    @SerialName("total_kalori") val totalCalories: Double = 0.0,
    @SerialName("total_protein_g") val totalProtein: Double = 0.0,
    @SerialName("total_karbo_g") val totalCarbs: Double = 0.0,
    @SerialName("total_lemak_g") val totalFat: Double = 0.0,
    @SerialName("total_serat_g") val totalFiber: Double = 0.0,
    @SerialName("total_gula_g") val totalSugar: Double = 0.0,
    @SerialName("total_natrium_mg") val totalSodium: Double = 0.0,
    @SerialName("catatan") val notes: String? = null,
)

@Serializable
data class GeminiElement(
    @SerialName("nama") val name: String = "",
    @SerialName("estimasi_gram") val grams: Double = 0.0,
    @SerialName("kalori") val calories: Double = 0.0,
    @SerialName("protein_g") val protein: Double = 0.0,
    @SerialName("karbo_g") val carbs: Double = 0.0,
    @SerialName("lemak_g") val fat: Double = 0.0,
    @SerialName("serat_g") val fiber: Double = 0.0,
    @SerialName("gula_g") val sugar: Double = 0.0,
    @SerialName("natrium_mg") val sodium: Double = 0.0,
    @SerialName("metode_masak") val cookingMethod: String? = null,
)

// ---------- Analisa mendalam periode (sesuai reviewSchema) ----------

@Serializable
data class GeminiReview(
    @SerialName("ringkasan") val summary: String = "",
    @SerialName("nilai_periode") val periodScore: String? = null,
    @SerialName("hal_baik") val positives: List<String> = emptyList(),
    @SerialName("perlu_diperbaiki") val improvements: List<String> = emptyList(),
    @SerialName("rekomendasi") val recommendations: List<GeminiRecommendation> = emptyList(),
    @SerialName("contoh_menu") val sampleMenu: List<String> = emptyList(),
    @SerialName("fokus_berikutnya") val nextFocus: String? = null,
)

@Serializable
data class GeminiRecommendation(
    @SerialName("judul") val title: String = "",
    @SerialName("detail") val detail: String = "",
)

// ---------- Saran menu dari sisa kalori (sesuai suggestSchema) ----------

@Serializable
data class GeminiSuggestion(
    @SerialName("pembuka") val intro: String = "",
    @SerialName("opsi") val options: List<GeminiMenuOption> = emptyList(),
    @SerialName("hindari") val avoid: List<String> = emptyList(),
)

@Serializable
data class GeminiMenuOption(
    @SerialName("nama") val name: String = "",
    @SerialName("perkiraan_kkal") val calories: Double = 0.0,
    @SerialName("protein_g") val protein: Double = 0.0,
    @SerialName("alasan") val reason: String = "",
    @SerialName("tips") val tips: String = "",
)

// ---------- Daftar model (models.list) ----------

@Serializable
data class ModelListResponse(
    val models: List<ModelInfo> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class ModelInfo(
    val name: String = "",
    val supportedGenerationMethods: List<String> = emptyList(),
)
