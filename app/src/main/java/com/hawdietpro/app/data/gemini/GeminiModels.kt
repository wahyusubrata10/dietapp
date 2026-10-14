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
    @SerialName("metode_masak") val cookingMethod: String? = null,
)
