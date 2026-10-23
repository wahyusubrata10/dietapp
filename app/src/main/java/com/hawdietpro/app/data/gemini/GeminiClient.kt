package com.hawdietpro.app.data.gemini

import android.util.Base64
import com.hawdietpro.app.data.prefs.AnalysisMode
import com.hawdietpro.app.data.prefs.AppPreferences
import com.hawdietpro.app.data.prefs.SecureStorage
import com.hawdietpro.app.data.remote.RemoteConfigStore
import com.hawdietpro.app.domain.config.ModelChain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
    private val callLog: AiCallLog,
) {
    data class PromptVars(
        val mealType: String,
        val time: String,
        val userHint: String?,
        val referenceNames: List<String>,
    )

    suspend fun analyzeFood(jpeg: ByteArray, vars: PromptVars): GeminiFoodResult {
        val config = store.geminiPrompt.value
        val userPrompt = config.userPrompt
            .replace("{meal_type}", vars.mealType)
            .replace("{time}", vars.time)
            .replace("{user_hint}", vars.userHint?.takeIf { it.isNotBlank() }?.let { "Catatan pengguna: $it" } ?: "")
            .replace("{reference_names}", vars.referenceNames.joinToString(", "))
        val text = generate(
            kind = "Analisa foto",
            systemPrompt = config.systemPrompt,
            parts = listOf(
                Part(inlineData = InlineData("image/jpeg", Base64.encodeToString(jpeg, Base64.NO_WRAP))),
                Part(text = userPrompt),
            ),
            schema = config.responseSchema,
            temperature = config.temperature,
        )
        return parseResult(text)
    }

    /** true bila remote config menyediakan prompt analisa mendalam. */
    fun reviewAvailable(): Boolean = store.geminiPrompt.value.let {
        it.reviewSystemPrompt != null && it.reviewPrompt != null && it.reviewSchema != null
    }

    /** Analisa mendalam periode (teks saja, tanpa foto) dari ringkasan statistik [statsJson]. */
    suspend fun generateReview(statsJson: String): GeminiReview {
        val config = store.geminiPrompt.value
        val system = config.reviewSystemPrompt ?: throw GeminiError.Other("fitur belum tersedia")
        val prompt = (config.reviewPrompt ?: "{stats}").replace("{stats}", statsJson)
        val schema = config.reviewSchema ?: throw GeminiError.Other("fitur belum tersedia")
        val text = generate("Analisa mendalam", system, listOf(Part(text = prompt)), schema, temperature = 0.4)
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return runCatching { json.decodeFromString(GeminiReview.serializer(), cleaned) }
            .getOrElse { throw GeminiError.BadResponse("JSON analisa") }
    }

    fun suggestionAvailable(): Boolean = store.geminiPrompt.value.let {
        it.suggestSystemPrompt != null && it.suggestPrompt != null && it.suggestSchema != null
    }

    /** Saran menu untuk waktu makan berikutnya dari konteks hari ini (JSON). */
    suspend fun generateSuggestion(contextJson: String, mealTypeLabel: String): GeminiSuggestion {
        val config = store.geminiPrompt.value
        val system = config.suggestSystemPrompt ?: throw GeminiError.Other("fitur belum tersedia")
        val schema = config.suggestSchema ?: throw GeminiError.Other("fitur belum tersedia")
        val prompt = (config.suggestPrompt ?: "{context}")
            .replace("{context}", contextJson)
            .replace("{meal_type}", mealTypeLabel)
        val text = generate("Saran menu", system, listOf(Part(text = prompt)), schema, temperature = 0.6)
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return runCatching { json.decodeFromString(GeminiSuggestion.serializer(), cleaned) }
            .getOrElse { throw GeminiError.BadResponse("JSON saran") }
    }

    fun labelAvailable(): Boolean = store.geminiPrompt.value.let {
        it.labelSystemPrompt != null && it.labelPrompt != null && it.labelSchema != null
    }

    /** Membaca tabel Informasi Nilai Gizi dari foto label kemasan. */
    suspend fun readLabel(jpeg: ByteArray): GeminiLabel {
        val config = store.geminiPrompt.value
        val system = config.labelSystemPrompt ?: throw GeminiError.Other("fitur belum tersedia")
        val schema = config.labelSchema ?: throw GeminiError.Other("fitur belum tersedia")
        val text = generate(
            "Baca label gizi",
            system,
            listOf(
                Part(inlineData = InlineData("image/jpeg", Base64.encodeToString(jpeg, Base64.NO_WRAP))),
                Part(text = config.labelPrompt ?: "Baca label ini."),
            ),
            schema,
            temperature = 0.0,
        )
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return runCatching { json.decodeFromString(GeminiLabel.serializer(), cleaned) }
            .getOrElse { throw GeminiError.BadResponse("JSON label") }
    }

    /**
     * Memanggil generateContent dengan rantai model (override → utama → cadangan, hanya model yang masih aktif).
     * - Tiap percobaan dibatasi [GeminiPromptConfig.attemptTimeoutSeconds]; timeout/sibuk/kuota → pindah model.
     * - Bila model pertama belum menjawab setelah [GeminiPromptConfig.hedgeAfterSeconds], model cadangan
     *   dijalankan paralel dan jawaban yang datang lebih dulu dipakai (maksimal 2 permintaan bersamaan).
     * - Semua percobaan dicatat di [AiCallLog] untuk diagnosa.
     */
    private suspend fun generate(kind: String, systemPrompt: String, parts: List<Part>, schema: JsonObject, temperature: Double): String {
        val trace = AiCallTrace(kind)
        try {
            val text = generateTraced(trace, systemPrompt, parts, schema, temperature)
            callLog.add(trace.build(success = true, error = null))
            return text
        } catch (e: GeminiError) {
            callLog.add(trace.build(success = false, error = e))
            throw e
        }
    }

    private suspend fun generateTraced(
        trace: AiCallTrace,
        systemPrompt: String,
        parts: List<Part>,
        schema: JsonObject,
        temperature: Double,
    ): String {
        val apiKey = secure.getApiKey()?.takeIf { it.isNotBlank() } ?: throw GeminiError.MissingApiKey()
        val config = store.geminiPrompt.value
        val settings = prefs.current()
        val fast = settings.analysisMode == AnalysisMode.CEPAT
        // Tingkat "thinking" dari remote config (Teliti vs Cepat). Model yang tidak mendukung → diulang tanpa pengaturan ini.
        val thinking = when {
            fast && (config.fastThinkingBudget != null || config.fastThinkingLevel != null) ->
                ThinkingConfig(thinkingBudget = config.fastThinkingBudget, thinkingLevel = config.fastThinkingLevel)
            !fast && config.thinkingLevel != null -> ThinkingConfig(thinkingLevel = config.thinkingLevel)
            else -> null
        }
        val request = GenerateContentRequest(
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
            contents = listOf(Content(role = "user", parts = parts)),
            generationConfig = GenerationConfig(
                // Gemini 3: temperature dibiarkan bawaan (1.0) kecuali remote config memintanya.
                temperature = if (config.sendTemperature) temperature else null,
                responseMimeType = "application/json",
                responseSchema = schema,
                thinkingConfig = thinking,
                maxOutputTokens = config.maxOutputTokens,
            ),
        )
        val body = json.encodeToString(GenerateContentRequest.serializer(), request)
        val bodyWithoutThinking = if (thinking == null) body else json.encodeToString(
            GenerateContentRequest.serializer(),
            request.copy(generationConfig = request.generationConfig?.copy(thinkingConfig = null)),
        )

        val available = if (config.autoDiscoverModels) availableModels(apiKey, trace) else null
        // Mode Teliti: model "lite" hanya cadangan terakhir (mis. saat kuota Flash habis), bukan dilarang.
        val chain = ModelChain.build(settings.geminiModelOverride, config.model, config.fallbackModels, available, allowLite = fast)
        val now = System.currentTimeMillis()
        val ready = chain.filter { (cooldownUntil[it] ?: 0L) <= now }
        val models = ready.ifEmpty { chain.sortedBy { cooldownUntil[it] ?: 0L } }
        val skipped = chain - ready.toSet()
        if (skipped.isNotEmpty()) trace.note("dilewati sementara (kuota habis/tidak ada): ${skipped.joinToString()}")
        trace.note("urutan model: ${models.joinToString()}")
        cooldownMinutes = config.quotaCooldownMinutes

        val attemptTimeout = config.attemptTimeoutSeconds.toLong()
        val hedgeAfterMs = config.hedgeAfterSeconds * 1000L
        return withTimeoutOrNull(config.totalTimeoutSeconds * 1000L) {
            race(models, apiKey, body, bodyWithoutThinking, attemptTimeout, hedgeAfterMs, trace)
        } ?: run {
            trace.note("batas waktu total ${config.totalTimeoutSeconds}s habis")
            throw GeminiError.Timeout()
        }
    }

    /** Menjalankan model berurutan, dengan satu permintaan cadangan paralel bila model pertama lambat. */
    private suspend fun race(
        models: List<String>,
        apiKey: String,
        body: String,
        bodyWithoutThinking: String,
        attemptTimeoutSeconds: Long,
        hedgeAfterMs: Long,
        trace: AiCallTrace,
    ): String = coroutineScope {
        val results = Channel<Pair<String, Result<String>>>(Channel.UNLIMITED)
        val running = mutableMapOf<String, Job>()
        var next = 0
        var hedged = false
        var lastError: GeminiError? = null

        fun startNext(): Boolean {
            if (next >= models.size) return false
            val model = models[next++]
            running[model] = launch {
                val outcome = runCatching { attempt(model, apiKey, body, bodyWithoutThinking, attemptTimeoutSeconds, trace) }
                results.trySend(model to outcome)
            }
            return true
        }

        startNext()
        while (true) {
            val canHedge = !hedged && hedgeAfterMs > 0 && running.size == 1 && next < models.size
            val received = if (canHedge) withTimeoutOrNull(hedgeAfterMs) { results.receive() } else results.receive()
            if (received == null) {
                hedged = true
                trace.note("model pertama belum menjawab ${hedgeAfterMs / 1000}s → cadangan paralel")
                startNext()
                continue
            }
            val (model, outcome) = received
            running.remove(model)
            val text = outcome.getOrNull()
            if (text != null) {
                running.values.forEach { it.cancel() }
                return@coroutineScope text
            }
            val error = when (val e = outcome.exceptionOrNull()) {
                is GeminiError -> e
                is CancellationException -> throw e
                else -> GeminiError.Other(e?.message ?: "tidak diketahui")
            }
            if (!error.canFallback()) {
                running.values.forEach { it.cancel() }
                throw error
            }
            // 404 (model dimatikan) tidak menutupi error yang lebih bermakna seperti sibuk/kuota.
            if (lastError == null || error !is GeminiError.ModelNotFound) lastError = error
            if (running.isEmpty() && !startNext()) throw lastError ?: error
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException()
    }

    private fun GeminiError.canFallback(): Boolean =
        this !is GeminiError.MissingApiKey && this !is GeminiError.InvalidApiKey && this !is GeminiError.Blocked

    private suspend fun attempt(
        model: String,
        apiKey: String,
        body: String,
        bodyWithoutThinking: String,
        timeoutSeconds: Long,
        trace: AiCallTrace,
    ): String {
        val start = trace.now()
        try {
            val text = try {
                callWithRetry(model, apiKey, body, timeoutSeconds)
            } catch (e: GeminiError.UnsupportedThinking) {
                trace.note("$model: thinking tidak didukung, diulang tanpa thinking")
                callWithRetry(model, apiKey, bodyWithoutThinking, timeoutSeconds)
            }
            trace.attempt(model, start, AiCallTrace.OK)
            return text
        } catch (e: CancellationException) {
            trace.attempt(model, start, AiCallTrace.CANCELLED)
            throw e
        } catch (e: GeminiError) {
            trace.attempt(model, start, e.shortCode)
            // Kuota harian/menit habis atau model dimatikan: jangan dicoba lagi tiap foto (hemat waktu & kuota).
            val cooldownMs = when (e) {
                is GeminiError.RateLimited -> maxOf((e.retryAfterSeconds ?: 0L) * 1000, cooldownMinutes * 60_000L)
                is GeminiError.ModelNotFound -> 6 * 60 * 60_000L
                else -> 0L
            }
            if (cooldownMs > 0) cooldownUntil[model] = System.currentTimeMillis() + cooldownMs
            throw e
        }
    }

    private val cooldownUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile private var cooldownMinutes = 20

    /** Daftar model aktif (cache 6 jam). Null bila gagal dicek → rantai dari config dipakai apa adanya. */
    private suspend fun availableModels(apiKey: String, trace: AiCallTrace): Set<String>? {
        val cached = modelCache
        if (cached != null && System.currentTimeMillis() - cached.first < MODEL_CACHE_MS) return cached.second
        val fetched = withTimeoutOrNull(MODEL_LIST_TIMEOUT_MS) {
            runCatching {
                val req = Request.Builder()
                    .url("$BASE_URL/models?pageSize=1000")
                    .header("x-goog-api-key", apiKey)
                    .get()
                    .build()
                val (code, text) = execute(req, MODEL_LIST_TIMEOUT_MS / 1000)
                if (code !in 200..299) null
                else json.decodeFromString(ModelListResponse.serializer(), text).models
                    .filter { "generateContent" in it.supportedGenerationMethods }
                    .map { it.name.removePrefix("models/") }
                    .toSet()
                    .takeIf { it.isNotEmpty() }
            }.getOrNull()
        }
        if (fetched != null) modelCache = System.currentTimeMillis() to fetched
        else trace.note("daftar model tidak bisa dicek, pakai urutan bawaan")
        return fetched ?: cached?.second
    }

    @Volatile private var modelCache: Pair<Long, Set<String>>? = null

    /** Cek cepat apakah API key valid (dipakai di Pengaturan). */
    suspend fun verifyApiKey(apiKey: String): Result<Unit> = runCatching {
        val req = Request.Builder()
            .url("$BASE_URL/models?pageSize=1")
            .header("x-goog-api-key", apiKey.trim())
            .get()
            .build()
        val (code, text) = execute(req, 20)
        if (code !in 200..299) throw mapHttpError(code, text, "-")
    }

    private suspend fun callWithRetry(model: String, apiKey: String, body: String, timeoutSeconds: Long): String {
        var attempt = 0
        while (true) {
            try {
                return call(model, apiKey, body, timeoutSeconds)
            } catch (e: GeminiError) {
                val canRetry = attempt < MAX_RETRIES && (e is GeminiError.RateLimited || e is GeminiError.Server)
                if (!canRetry) throw e
                val retryAfter = (e as? GeminiError.RateLimited)?.retryAfterSeconds
                // Bila Google meminta menunggu terlalu lama (kuota harian), langsung pindah model / antrian.
                if (retryAfter != null && retryAfter > MAX_WAIT_SECONDS) throw e
                val waitSeconds = retryAfter ?: backoff(attempt)
                delay(waitSeconds * 1000)
                attempt++
            }
        }
    }

    private fun backoff(attempt: Int): Long = min(1L shl attempt, 4L) // 1, 2 detik; selebihnya pindah ke model cadangan

    private suspend fun call(model: String, apiKey: String, body: String, timeoutSeconds: Long): String {
        val req = Request.Builder()
            .url("$BASE_URL/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        val (code, text) = execute(req, timeoutSeconds)
        if (code !in 200..299) throw mapHttpError(code, text, model)

        val response = runCatching { json.decodeFromString(GenerateContentResponse.serializer(), text) }
            .getOrElse { throw GeminiError.BadResponse("format respons") }
        response.promptFeedback?.blockReason?.let { throw GeminiError.Blocked(it) }
        val candidate = response.candidates.firstOrNull() ?: throw GeminiError.BadResponse("tanpa kandidat")
        if (candidate.finishReason == "SAFETY" || candidate.finishReason == "PROHIBITED_CONTENT") {
            throw GeminiError.Blocked(candidate.finishReason)
        }
        return candidate.content?.parts?.mapNotNull { it.text }?.joinToString("")?.takeIf { it.isNotBlank() }
            ?: throw GeminiError.BadResponse("teks kosong (${candidate.finishReason ?: "-"})")
    }

    /**
     * Menjalankan request HTTP yang bisa dibatalkan (permintaan cadangan yang kalah langsung diputus)
     * dengan batas waktu total [timeoutSeconds]. Timeout → [GeminiError.Timeout], tanpa internet → [GeminiError.Network].
     */
    private suspend fun execute(request: Request, timeoutSeconds: Long): Pair<Int, String> {
        val call = http.newCall(request)
        call.timeout().timeout(timeoutSeconds, TimeUnit.SECONDS)
        return try {
            suspendCancellableCoroutine<Pair<Int, String>> { cont ->
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching { response.use { it.code to it.body?.string().orEmpty() } }
                        if (cont.isActive) result.fold({ cont.resume(it) }, { cont.resumeWithException(it) })
                    }
                })
            }
        } catch (e: IOException) {
            throw when (e) {
                is UnknownHostException, is ConnectException, is NoRouteToHostException -> GeminiError.Network()
                is InterruptedIOException -> GeminiError.Timeout()
                else -> GeminiError.Network()
            }
        }
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
        const val MODEL_CACHE_MS = 6 * 60 * 60 * 1000L
        const val MODEL_LIST_TIMEOUT_MS = 6_000L
    }
}
