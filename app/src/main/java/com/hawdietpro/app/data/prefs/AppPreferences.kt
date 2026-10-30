package com.hawdietpro.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

/** Warna aksen aplikasi. [light]/[dark] = warna utama (ARGB) untuk tema terang/gelap. */
enum class AccentColor(val label: String, val light: Long, val dark: Long) {
    EMERALD("Hijau", 0xFF1A8A5F, 0xFF5FE0A8),
    OCEAN("Biru", 0xFF1F6FE0, 0xFF8AB8FF),
    GRAPE("Ungu", 0xFF6E49E0, 0xFFB7A2FF),
    SUNSET("Oranye", 0xFFDB5A12, 0xFFFFAA72),
    ROSE("Merah muda", 0xFFD0336C, 0xFFFF92B8);

    companion object {
        fun parse(value: String?) = entries.firstOrNull { it.name == value } ?: EMERALD
    }
}

/** Pengaturan tampilan yang dipakai tema. */
data class Appearance(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.EMERALD,
    /** Latar hitam pekat di mode gelap (hemat baterai di layar AMOLED). */
    val amoled: Boolean = false,
    /** Warna dari wallpaper (Material You, Android 12+). */
    val dynamicColor: Boolean = false,
)

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
    /** true = setelah foto diambil langsung kembali, analisa jalan di latar belakang + notifikasi. */
    val backgroundAnalysis: Boolean = true,
    val autoDownloadUpdates: Boolean = true,
    val updatesWifiOnly: Boolean = true,
    val geminiModelOverride: String? = null,
    val dismissedAnnouncementId: String? = null,
    val lastUpdateCheck: Long = 0,
    val lastRemoteSync: Long = 0,
    val foodDbVersion: Int = 0,
    val skippedUpdateVersionCode: Int = 0,
    /** true = target & perkiraan berat memakai kebutuhan kalori yang dipelajari dari timbangan. */
    val adaptiveTdee: Boolean = true,
    val accent: AccentColor = AccentColor.EMERALD,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = false,
    /** Minggu (epochDay hari Senin) terakhir yang rekapnya sudah dilihat. */
    val lastWrapWeek: Long = 0,
) {
    val appearance: Appearance get() = Appearance(themeMode, accent, amoled, dynamicColor)
}

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val ANALYSIS_MODE = stringPreferencesKey("analysis_mode")
        val BACKGROUND_ANALYSIS = booleanPreferencesKey("background_analysis")
        val AUTO_DOWNLOAD = booleanPreferencesKey("auto_download_updates")
        val WIFI_ONLY = booleanPreferencesKey("updates_wifi_only")
        val MODEL_OVERRIDE = stringPreferencesKey("gemini_model_override")
        val DISMISSED_ANNOUNCEMENT = stringPreferencesKey("dismissed_announcement")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val LAST_REMOTE_SYNC = longPreferencesKey("last_remote_sync")
        val FOOD_DB_VERSION = intPreferencesKey("food_db_version")
        val SKIPPED_UPDATE = intPreferencesKey("skipped_update_version")
        val SEEN_ACHIEVEMENTS = stringSetPreferencesKey("seen_achievements")
        val ADAPTIVE_TDEE = booleanPreferencesKey("adaptive_tdee")
        val ACCENT = stringPreferencesKey("accent_color")
        val AMOLED = booleanPreferencesKey("amoled")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LAST_WRAP_WEEK = longPreferencesKey("last_wrap_week")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            themeMode = ThemeMode.parse(p[Keys.THEME]),
            analysisMode = AnalysisMode.parse(p[Keys.ANALYSIS_MODE]),
            backgroundAnalysis = p[Keys.BACKGROUND_ANALYSIS] ?: true,
            autoDownloadUpdates = p[Keys.AUTO_DOWNLOAD] ?: true,
            updatesWifiOnly = p[Keys.WIFI_ONLY] ?: true,
            geminiModelOverride = p[Keys.MODEL_OVERRIDE]?.takeIf { it.isNotBlank() },
            dismissedAnnouncementId = p[Keys.DISMISSED_ANNOUNCEMENT],
            lastUpdateCheck = p[Keys.LAST_UPDATE_CHECK] ?: 0,
            lastRemoteSync = p[Keys.LAST_REMOTE_SYNC] ?: 0,
            foodDbVersion = p[Keys.FOOD_DB_VERSION] ?: 0,
            skippedUpdateVersionCode = p[Keys.SKIPPED_UPDATE] ?: 0,
            adaptiveTdee = p[Keys.ADAPTIVE_TDEE] ?: true,
            accent = AccentColor.parse(p[Keys.ACCENT]),
            amoled = p[Keys.AMOLED] ?: false,
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: false,
            lastWrapWeek = p[Keys.LAST_WRAP_WEEK] ?: 0,
        )
    }

    suspend fun current(): Settings = settings.first()

    /** Id badge yang sudah pernah dilihat pengguna (untuk penanda "badge baru"). */
    val seenAchievements: Flow<Set<String>> = context.dataStore.data.map { it[Keys.SEEN_ACHIEVEMENTS].orEmpty() }

    suspend fun markAchievementsSeen(ids: Set<String>) = context.dataStore.edit {
        it[Keys.SEEN_ACHIEVEMENTS] = it[Keys.SEEN_ACHIEVEMENTS].orEmpty() + ids
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }
    suspend fun setAnalysisMode(mode: AnalysisMode) = context.dataStore.edit { it[Keys.ANALYSIS_MODE] = mode.name }
    suspend fun setBackgroundAnalysis(value: Boolean) = context.dataStore.edit { it[Keys.BACKGROUND_ANALYSIS] = value }
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
    suspend fun setAdaptiveTdee(value: Boolean) = context.dataStore.edit { it[Keys.ADAPTIVE_TDEE] = value }
    suspend fun setAccent(value: AccentColor) = context.dataStore.edit { it[Keys.ACCENT] = value.name }
    suspend fun setAmoled(value: Boolean) = context.dataStore.edit { it[Keys.AMOLED] = value }
    suspend fun setDynamicColor(value: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = value }
    suspend fun setLastWrapWeek(value: Long) = context.dataStore.edit { it[Keys.LAST_WRAP_WEEK] = value }
}
