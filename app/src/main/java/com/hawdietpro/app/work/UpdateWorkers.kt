package com.hawdietpro.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hawdietpro.app.core.Notifications
import com.hawdietpro.app.data.prefs.AppPreferences
import com.hawdietpro.app.data.remote.RemoteConfigSync
import com.hawdietpro.app.data.update.UpdateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Periodik (default tiap 6 jam): sinkron remote config + cek rilis baru di GitHub. */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: RemoteConfigSync,
    private val updates: UpdateRepository,
    private val prefs: AppPreferences,
    private val scheduler: WorkScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { sync.sync() }
        val release = updates.check() ?: return Result.success()
        val settings = prefs.current()
        if (settings.skippedUpdateVersionCode == release.versionCode) return Result.success()
        if (settings.autoDownloadUpdates) {
            scheduler.enqueueUpdateDownload(settings.updatesWifiOnly)
        } else {
            Notifications.updateAvailable(applicationContext, release.versionName, ready = false)
        }
        return Result.success()
    }

    companion object {
        const val NAME = "update_check"
    }
}

/** Mengunduh APK update di background dengan progress. */
@HiltWorker
class UpdateDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updates: UpdateRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val release = updates.latestKnownRelease() ?: updates.check() ?: return Result.success()
        return try {
            updates.download(release) { p -> setProgress(workDataOf(KEY_PROGRESS to p)) }
            Notifications.updateAvailable(applicationContext, release.versionName, ready = true)
            Result.success()
        } catch (e: SecurityException) {
            Result.failure()
        } catch (e: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val NAME = "update_download"
        const val KEY_PROGRESS = "progress"
    }
}
