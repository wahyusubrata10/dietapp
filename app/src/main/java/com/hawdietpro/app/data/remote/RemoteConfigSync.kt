package com.hawdietpro.app.data.remote

import android.util.Log
import com.hawdietpro.app.BuildConfig
import com.hawdietpro.app.data.prefs.AppPreferences
import com.hawdietpro.app.data.repository.FoodRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lapis 2 auto-update: mengambil /remote/*.json dari raw.githubusercontent.com.
 * Hanya versi yang lebih baru & valid yang dipakai; bila gagal, cache/bundel tetap dipakai.
 */
@Singleton
class RemoteConfigSync @Inject constructor(
    private val http: OkHttpClient,
    private val store: RemoteConfigStore,
    private val foods: FoodRepository,
    private val prefs: AppPreferences,
) {
    data class SyncReport(val updated: List<RemoteFile>, val failed: Map<RemoteFile, String>, val needsAppUpdate: Boolean)

    private val _needsAppUpdate = MutableStateFlow(false)
    /** true bila ada file remote yang butuh versi aplikasi lebih baru. */
    val needsAppUpdate: StateFlow<Boolean> = _needsAppUpdate.asStateFlow()

    suspend fun syncIfStale(maxAgeMillis: Long = 60 * 60 * 1000L) {
        if (System.currentTimeMillis() - prefs.current().lastRemoteSync >= maxAgeMillis) sync()
    }

    suspend fun sync(): SyncReport = withContext(Dispatchers.IO) {
        val updated = mutableListOf<RemoteFile>()
        val failed = mutableMapOf<RemoteFile, String>()
        var needsUpdate = false
        for (file in RemoteFile.entries) {
            try {
                val req = Request.Builder()
                    .url(BuildConfig.REMOTE_BASE_URL + file.fileName)
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .build()
                val raw = http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    resp.body?.string().orEmpty()
                }
                when (val result = store.accept(file, raw)) {
                    RemoteConfigStore.AcceptResult.Updated -> updated += file
                    RemoteConfigStore.AcceptResult.NotNewer -> Unit
                    is RemoteConfigStore.AcceptResult.RequiresAppUpdate -> needsUpdate = true
                    is RemoteConfigStore.AcceptResult.Invalid -> failed[file] = result.reason
                }
            } catch (e: Exception) {
                failed[file] = e.message ?: e.javaClass.simpleName
            }
        }
        if (RemoteFile.FOOD_DB in updated) foods.seedIfNeeded()
        if (store.appConfig.value.minSupportedVersionCode > BuildConfig.VERSION_CODE) needsUpdate = true
        _needsAppUpdate.value = needsUpdate
        if (failed.size < RemoteFile.entries.size) prefs.setLastRemoteSync(System.currentTimeMillis())
        if (failed.isNotEmpty()) Log.w("RemoteConfigSync", "Gagal: $failed")
        SyncReport(updated, failed, needsUpdate)
    }
}
