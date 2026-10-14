package com.hawdietpro.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode(val label: String) {
    SYSTEM("Ikuti sistem"),
    LIGHT("Terang"),
    DARK("Gelap");

    companion object {
        fun parse(value: String?) = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

enum class AnalysisMode(val label: String, val description: String) {
    TELITI("Teliti", "Kualitas analisa maksimal (AI berpikir dulu). Sedikit lebih lama."),
    CEPAT("Cepat", "Jawaban lebih cepat, ketelitian bisa sedikit turun pada piring yang rumit.");

    companion object {
        fun parse(value: String?) = entries.firstOrNull { it.name == value } ?: TELITI
    }
}

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val analysisMode: AnalysisMode = AnalysisMode.TELITI,
    val autoDownloadUpdates: Boolean = true,
    val updatesWifiOnly: Boolean = true,
    val geminiModelOverride: String? = null,
    val dismissedAnnouncementId: String? = null,
    val lastUpdateCheck: Long = 0,
    val lastRemoteSync: Long = 0,
    val foodDbVersion: Int = 0,
    val skippedUpdateVersionCode: Int = 0,
)

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val ANALYSIS_MODE = stringPreferencesKey("analysis_mode")
        val AUTO_DOWNLOAD = booleanPreferencesKey("auto_download_updates")
        val WIFI_ONLY = booleanPreferencesKey("updates_wifi_only")
        val MODEL_OVERRIDE = stringPreferencesKey("gemini_model_override")
        val DISMISSED_ANNOUNCEMENT = stringPreferencesKey("dismissed_announcement")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val LAST_REMOTE_SYNC = longPreferencesKey("last_remote_sync")
        val FOOD_DB_VERSION = intPreferencesKey("food_db_version")
        val SKIPPED_UPDATE = intPreferencesKey("skipped_update_version")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            themeMode = ThemeMode.parse(p[Keys.THEME]),
            analysisMode = AnalysisMode.parse(p[Keys.ANALYSIS_MODE]),
            autoDownloadUpdates = p[Keys.AUTO_DOWNLOAD] ?: true,
            updatesWifiOnly = p[Keys.WIFI_ONLY] ?: true,
            geminiModelOverride = p[Keys.MODEL_OVERRIDE]?.takeIf { it.isNotBlank() },
            dismissedAnnouncementId = p[Keys.DISMISSED_ANNOUNCEMENT],
            lastUpdateCheck = p[Keys.LAST_UPDATE_CHECK] ?: 0,
            lastRemoteSync = p[Keys.LAST_REMOTE_SYNC] ?: 0,
            foodDbVersion = p[Keys.FOOD_DB_VERSION] ?: 0,
            skippedUpdateVersionCode = p[Keys.SKIPPED_UPDATE] ?: 0,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }
    suspend fun setAnalysisMode(mode: AnalysisMode) = context.dataStore.edit { it[Keys.ANALYSIS_MODE] = mode.name }
    suspend fun setAutoDownloadUpdates(value: Boolean) = context.dataStore.edit { it[Keys.AUTO_DOWNLOAD] = value }
    suspend fun setUpdatesWifiOnly(value: Boolean) = context.dataStore.edit { it[Keys.WIFI_ONLY] = value }
    suspend fun setGeminiModelOverride(value: String?) = context.dataStore.edit {
        if (value.isNullOrBlank()) it.remove(Keys.MODEL_OVERRIDE) else it[Keys.MODEL_OVERRIDE] = value.trim()
    }
    suspend fun setDismissedAnnouncement(id: String) = context.dataStore.edit { it[Keys.DISMISSED_ANNOUNCEMENT] = id }
    suspend fun setLastUpdateCheck(time: Long) = context.dataStore.edit { it[Keys.LAST_UPDATE_CHECK] = time }
    suspend fun setLastRemoteSync(time: Long) = context.dataStore.edit { it[Keys.LAST_REMOTE_SYNC] = time }
    suspend fun setFoodDbVersion(version: Int) = context.dataStore.edit { it[Keys.FOOD_DB_VERSION] = version }
    suspend fun setSkippedUpdate(versionCode: Int) = context.dataStore.edit { it[Keys.SKIPPED_UPDATE] = versionCode }
}
