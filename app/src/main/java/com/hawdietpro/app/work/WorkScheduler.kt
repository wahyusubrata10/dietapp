package com.hawdietpro.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    private val wm get() = WorkManager.getInstance(context)

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Proses antrian foto begitu ada koneksi. */
    fun enqueuePendingAnalysis() {
        val request = OneTimeWorkRequestBuilder<PendingAnalysisWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniqueWork(PendingAnalysisWorker.NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Cek update APK + remote config secara berkala. */
    fun schedulePeriodic(intervalHours: Int) {
        val hours = intervalHours.coerceIn(1, 168).toLong()
        wm.enqueueUniquePeriodicWork(
            UpdateCheckWorker.NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(hours, TimeUnit.HOURS)
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build(),
        )
    }

    /** Unduh APK update di background. */
    fun enqueueUpdateDownload(wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()
        wm.enqueueUniqueWork(
            UpdateDownloadWorker.NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build(),
        )
    }

    fun cancelUpdateDownload() = wm.cancelUniqueWork(UpdateDownloadWorker.NAME)

    fun updateDownloadInfo() = wm.getWorkInfosForUniqueWorkFlow(UpdateDownloadWorker.NAME)
}
