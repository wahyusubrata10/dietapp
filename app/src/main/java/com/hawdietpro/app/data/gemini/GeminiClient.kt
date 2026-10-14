package com.hawdietpro.app.data.gemini

import android.util.Base64
import com.hawdietpro.app.data.prefs.AnalysisMode
import com.hawdietpro.app.data.prefs.AppPreferences
import com.hawdietpro.app.data.prefs.SecureStorage
import com.hawdietpro.app.data.remote.RemoteConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Klien REST Gemini (generateContent) dengan output JSON terstruktur (responseSchema).
 * Menangani limit free tier: retry + backoff untuk 429/5xx dan fallback model saat 404.
 */
@Singleton
class GeminiClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
    private val store: RemoteConfigStore,
    private val secure: SecureStorage,
    private val prefs: AppPreferences,
) {
    data class PromptVars(
        val mealType: String,
        val time: String,
        val userHint: String?,
        val referenceNames: List<String>,
    )

    suspend fun analyzeFood(jpeg: ByteArray, vars: PromptVars): GeminiFoodResult {
        val apiKey = secure.getApiKey()?.takeIf { it.isNotBlank() } ?: throw GeminiError.MissingApiKey()
        val config = store.geminiPrompt.value
        val userPrompt = config.userPrompt
            .replace("{meal_type}", vars.mealType)
            .replace("{time}", vars.time)
            .replace("{user_hint}", vars.userHint?.takeIf { it.isNotBlank() }?.let { "Catatan pengguna: $it" } ?: "")
            .replace("{reference_names}", vars.referenceNames.joinToString(", "))

        val settings = prefs.current()
        val fast = settings.analysisMode == AnalysisMode.CEPAT
        // Mode Teliti: thinking bawaan model (kualitas maksimal). Mode Cepat: thinking diperkecil sesuai remote config.
        val thinking = if (fast && (config.fastThinkingBudget != null || config.fastThinkingLevel != null)) {
            ThinkingConfig(thinkingBudget = config.fastThinkingBudget, thinkingLevel = config.fastThinkingLevel)
        } else null
        val request = GenerateContentRequest(
            systemInstruction = Content(parts = listOf(Part(text = config.systemPrompt))),
            contents = listOf(
                Content(
                    role = "user",
                    parts = listOf(
                        Part(inlineData = InlineData("image/jpeg", Base64.encodeToString(jpeg, Base64.NO_WRAP))),
                        Part(text = userPrompt),
                    ),
                ),
            ),
            generationConfig = GenerationConfig(
                temperature = config.temperature,
                responseMimeType = "application/json",
                responseSchema = config.responseSchema,
                thinkingConfig = thinking,
            ),
        )
        val body = json.encodeToString(GenerateContentRequest.serializer(), request)
        val bodyWithoutThinking = if (thinking == null) body else json.encodeToString(
            GenerateContentRequest.serializer(),
            request.copy(generationConfig = request.generationConfig?.copy(thinkingConfig = null)),
        )

        val override = settings.geminiModelOverride
        // Mode Teliti tidak pernah turun ke model "lite" yang kurang teliti.
        val fallbacks = if (fast) config.fallbackModels else config.fallbackModels.filterNot { it.contains("lite", ignoreCase = true) }
        val models = (listOfNotNull(override) + config.model + fallbacks).distinct()

        var lastError: GeminiError? = null
        // Model sibuk (503), kuota per-model habis (429), atau tidak tersedia (404) → coba model cadangan.
        for (model in models) {
            try {
                val text = try {
                    callWithRetry(model, apiKey, body)
                } catch (e: GeminiError.UnsupportedThinking) {
                    callWithRetry(model, apiKey, bodyWithoutThinking)
                }
                return parseResult(text)
            } catch (e: GeminiError.ModelNotFound) {
                if (lastError == null) lastError = e
            } catch (e: GeminiError.Server) {
                lastError = e
            } catch (e: GeminiError.RateLimited) {
                lastError = e
            }
        }
        throw lastError ?: GeminiError.Other("tidak ada model")
    }

    /** Cek cepat apakah API key valid (dipakai di Pengaturan). */
    suspend fun verifyApiKey(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE_URL/models?pageSize=1")
                .header("x-goog-api-key", apiKey.trim())
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw mapHttpError(resp.code, resp.body?.string().orEmpty(), "-")
            }
        }.recoverCatching { e ->
            throw if (e is IOException) GeminiError.Network() else e
        }
    }

    private suspend fun callWithRetry(model: String, apiKey: String, body: String): String {
        var attempt = 0
        while (true) {
            try {
                return call(model, apiKey, body)
            } catch (e: GeminiError) {
                val canRetry = attempt < MAX_RETRIES && (e is GeminiError.RateLimited || e is GeminiError.Server)
                if (!canRetry) throw e
                val retryAfter = (e as? GeminiError.RateLimited)?.retryAfterSeconds
                // Bila Google meminta menunggu terlalu lama (kuota harian), langsung gagal agar bisa diantrikan.
                if (retryAfter != null && retryAfter > MAX_WAIT_SECONDS) throw e
                val waitSeconds = retryAfter ?: backoff(attempt)
                delay(waitSeconds * 1000)
                attempt++
            }
        }
    }

    private fun backoff(attempt: Int): Long = min(1L shl attempt, 4L) // 1, 2 detik; selebihnya pindah ke model cadangan

    private suspend fun call(model: String, apiKey: String, body: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE_URL/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        val (code, text) = try {
            http.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw GeminiError.Network()
        }
        if (code !in 200..299) throw mapHttpError(code, text, model)

        val response = runCatching { json.decodeFromString(GenerateContentResponse.serializer(), text) }
            .getOrElse { throw GeminiError.BadResponse("format respons") }
        response.promptFeedback?.blockReason?.let { throw GeminiError.Blocked(it) }
        val candidate = response.candidates.firstOrNull() ?: throw GeminiError.BadResponse("tanpa kandidat")
        if (candidate.finishReason == "SAFETY" || candidate.finishReason == "PROHIBITED_CONTENT") {
            throw GeminiError.Blocked(candidate.finishReason)
        }
        candidate.content?.parts?.mapNotNull { it.text }?.joinToString("")?.takeIf { it.isNotBlank() }
            ?: throw GeminiError.BadResponse("teks kosong (${candidate.finishReason ?: "-"})")
    }

    private fun parseResult(text: String): GeminiFoodResult {
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val result = runCatching { json.decodeFromString(GeminiFoodResult.serializer(), cleaned) }
            .getOrElse { throw GeminiError.BadResponse("JSON hasil") }
        if (!result.isFood) throw GeminiError.NotFood()
        return result
    }

    private fun mapHttpError(code: Int, body: String, model: String): GeminiError {
        val error = runCatching { json.decodeFromString(ApiErrorEnvelope.serializer(), body).error }.getOrNull()
        val message = error?.message.orEmpty()
        return when {
            code == 429 -> GeminiError.RateLimited(retryDelaySeconds(error))
            code == 404 -> GeminiError.ModelNotFound(model)
            code == 400 && message.contains("thinking", ignoreCase = true) -> GeminiError.UnsupportedThinking()
            code == 401 || code == 403 -> GeminiError.InvalidApiKey(message.take(80).ifBlank { "HTTP $code" })
            code == 400 && (message.contains("API key", ignoreCase = true) || body.contains("API_KEY_INVALID")) ->
                GeminiError.InvalidApiKey(message.take(80))
            code >= 500 -> GeminiError.Server(code)
            else -> GeminiError.Other(message.take(120).ifBlank { "HTTP $code" })
        }
    }

    /** Membaca RetryInfo.retryDelay (contoh "23s") dari detail error Google. */
    private fun retryDelaySeconds(error: ApiError?): Long? = error?.details
        ?.firstNotNullOfOrNull { detail -> detail["retryDelay"]?.jsonPrimitive?.content }
        ?.removeSuffix("s")
        ?.toDoubleOrNull()
        ?.let { kotlin.math.ceil(it).toLong() }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        const val MAX_RETRIES = 1
        const val MAX_WAIT_SECONDS = 10L
    }
}
