package com.hawdietpro.app.data.remote

import android.content.Context
import android.util.Log
import com.hawdietpro.app.BuildConfig
import com.hawdietpro.app.domain.config.AppConfig
import com.hawdietpro.app.domain.config.ConfigValidator
import com.hawdietpro.app.domain.config.FoodDbFile
import com.hawdietpro.app.domain.config.GeminiPromptConfig
import com.hawdietpro.app.domain.config.InsightRulesFile
import com.hawdietpro.app.domain.config.VersionedConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class RemoteFile(val fileName: String) {
    CONFIG("config.json"),
    GEMINI_PROMPT("gemini_prompt.json"),
    FOOD_DB("food_db.json"),
    INSIGHT_RULES("insight_rules.json"),
}

/**
 * Sumber remote config yang dipakai aplikasi.
 * Urutan prioritas: cache hasil unduhan (filesDir/remote) bila valid & lebih baru,
 * lalu salinan bawaan di assets/remote. File rusak tidak pernah dipakai.
 */
@Singleton
class RemoteConfigStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val cacheDir = File(context.filesDir, "remote").apply { mkdirs() }

    private val _appConfig = MutableStateFlow(load(RemoteFile.CONFIG, AppConfig.serializer()))
    val appConfig: StateFlow<AppConfig> = _appConfig.asStateFlow()

    private val _geminiPrompt = MutableStateFlow(load(RemoteFile.GEMINI_PROMPT, GeminiPromptConfig.serializer()))
    val geminiPrompt: StateFlow<GeminiPromptConfig> = _geminiPrompt.asStateFlow()

    private val _insightRules = MutableStateFlow(load(RemoteFile.INSIGHT_RULES, InsightRulesFile.serializer()))
    val insightRules: StateFlow<InsightRulesFile> = _insightRules.asStateFlow()

    fun loadFoodDb(): FoodDbFile = load(RemoteFile.FOOD_DB, FoodDbFile.serializer())

    fun currentVersion(file: RemoteFile): Int = when (file) {
        RemoteFile.CONFIG -> _appConfig.value.version
        RemoteFile.GEMINI_PROMPT -> _geminiPrompt.value.version
        RemoteFile.INSIGHT_RULES -> _insightRules.value.version
        RemoteFile.FOOD_DB -> loadFoodDb().version
    }

    sealed interface AcceptResult {
        data object Updated : AcceptResult
        data object NotNewer : AcceptResult
        data class RequiresAppUpdate(val minVersionCode: Int) : AcceptResult
        data class Invalid(val reason: String) : AcceptResult
    }

    /** Memvalidasi teks JSON hasil unduhan; bila valid & lebih baru, disimpan atomik dan langsung dipakai. */
    fun accept(file: RemoteFile, raw: String): AcceptResult {
        val parsed = when (file) {
            RemoteFile.CONFIG -> parse(raw, AppConfig.serializer())
            RemoteFile.GEMINI_PROMPT -> parse(raw, GeminiPromptConfig.serializer())
            RemoteFile.FOOD_DB -> parse(raw, FoodDbFile.serializer())
            RemoteFile.INSIGHT_RULES -> parse(raw, InsightRulesFile.serializer())
        }
        val config = parsed.getOrElse { return AcceptResult.Invalid(it.message ?: "JSON tidak valid") }
        val problems = ConfigValidator.validate(config)
        if (problems.isNotEmpty()) return AcceptResult.Invalid(problems.joinToString("; "))
        if (config.version <= currentVersion(file)) return AcceptResult.NotNewer
        if (config.minAppVersionCode > BuildConfig.VERSION_CODE) return AcceptResult.RequiresAppUpdate(config.minAppVersionCode)

        val target = File(cacheDir, file.fileName)
        val tmp = File(cacheDir, file.fileName + ".tmp")
        tmp.writeText(raw)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
        when (config) {
            is AppConfig -> _appConfig.value = config
            is GeminiPromptConfig -> _geminiPrompt.value = config
            is InsightRulesFile -> _insightRules.value = config
            else -> Unit
        }
        return AcceptResult.Updated
    }

    private fun <T : VersionedConfig> parse(raw: String, serializer: KSerializer<T>): Result<T> =
        runCatching { json.decodeFromString(serializer, raw) }

    private fun <T : VersionedConfig> load(file: RemoteFile, serializer: KSerializer<T>): T {
        val bundled = readAsset(file)?.let { parse(it, serializer).getOrNull() }
            ?: error("Salinan bawaan ${file.fileName} rusak — build tidak valid")
        val cached = File(cacheDir, file.fileName).takeIf { it.exists() }
            ?.let { runCatching { it.readText() }.getOrNull() }
            ?.let { parse(it, serializer).getOrNull() }
            ?.takeIf { ConfigValidator.validate(it).isEmpty() }
            ?.takeIf { it.minAppVersionCode <= BuildConfig.VERSION_CODE }
        return if (cached != null && cached.version > bundled.version) cached else bundled.also {
            if (cached != null) Log.i(TAG, "${file.fileName}: bawaan v${bundled.version} lebih baru dari cache v${cached.version}")
        }
    }

    private fun readAsset(file: RemoteFile): String? =
        runCatching { context.assets.open("remote/${file.fileName}").bufferedReader().use { it.readText() } }
            .onFailure { Log.e(TAG, "Gagal membaca asset ${file.fileName}", it) }
            .getOrNull()

    private companion object {
        const val TAG = "RemoteConfigStore"
    }
}
