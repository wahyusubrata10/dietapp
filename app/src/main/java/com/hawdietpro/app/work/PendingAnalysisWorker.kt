package com.hawdietpro.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Cadangan untuk memproses antrian foto saat aplikasi tidak aktif. Logika ada di [PendingProcessor]
 * (dipakai bersama pemrosesan langsung di aplikasi, dijaga satu mutex).
 */
@HiltWorker
class PendingAnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val processor: PendingProcessor,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val again = processor.process(fromWorker = true)
        return if (again && runAttemptCount < MAX_RUNS) Result.retry() else Result.success()
    }

    companion object {
        const val NAME = "pending_analysis"
        private const val MAX_RUNS = 12
    }
}
