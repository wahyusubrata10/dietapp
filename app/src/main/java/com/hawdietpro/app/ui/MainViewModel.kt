package com.hawdietpro.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.BuildConfig
import com.hawdietpro.app.data.prefs.AppPreferences
import com.hawdietpro.app.data.prefs.Appearance
import com.hawdietpro.app.data.remote.RemoteConfigStore
import com.hawdietpro.app.data.remote.RemoteConfigSync
import com.hawdietpro.app.data.repository.ProfileRepository
import com.hawdietpro.app.data.update.UpdateRepository
import com.hawdietpro.app.data.update.UpdateState
import com.hawdietpro.app.domain.config.Announcement
import com.hawdietpro.app.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.hawdietpro.app.data.repository.PendingRepository
import com.hawdietpro.app.domain.model.MealType
import javax.inject.Inject

sealed interface StartState {
    data object Loading : StartState
    data object NeedsOnboarding : StartState
    data object Ready : StartState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    profiles: ProfileRepository,
    private val prefs: AppPreferences,
    private val updates: UpdateRepository,
    private val sync: RemoteConfigSync,
    private val scheduler: WorkScheduler,
    store: RemoteConfigStore,
    private val pending: PendingRepository,
) : ViewModel() {

    val startState: StateFlow<StartState> = profiles.observeProfile()
        .map { if (it == null) StartState.NeedsOnboarding else StartState.Ready }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StartState.Loading)

    val appearance: StateFlow<Appearance> = prefs.settings.map { it.appearance }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())

    val updateState: StateFlow<UpdateState> = updates.state

    /** Versi app terlalu lama menurut config.json → wajib update (Lapis 1). */
    val mustUpdate: StateFlow<Boolean> = combine(store.appConfig, sync.needsAppUpdate) { cfg, needs ->
        needs || cfg.minSupportedVersionCode > BuildConfig.VERSION_CODE
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val announcement: StateFlow<Announcement?> = combine(store.appConfig, prefs.settings) { cfg, s ->
        cfg.announcement?.takeIf { it.id != s.dismissedAnnouncementId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Saat aplikasi dibuka: sinkron remote config (bila sudah >1 jam) & cek update.
        viewModelScope.launch {
            runCatching { sync.syncIfStale() }
            val release = updates.check()
            val s = prefs.current()
            if (release != null && s.autoDownloadUpdates && s.skippedUpdateVersionCode != release.versionCode) {
                scheduler.enqueueUpdateDownload(s.updatesWifiOnly)
            }
        }
    }

    /** Setelan "analisa di latar belakang" (default aktif). */
    val backgroundAnalysis: StateFlow<Boolean> = prefs.settings.map { it.backgroundAnalysis }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Foto langsung masuk antrian analisa; hasil tercatat otomatis + notifikasi. */
    fun analyzeInBackground(photoPath: String, type: MealType, time: Long, hint: String?) {
        viewModelScope.launch { pending.enqueue(photoPath, type, time, hint) }
    }

    fun dismissAnnouncement(id: String) {
        viewModelScope.launch { prefs.setDismissedAnnouncement(id) }
    }
}
