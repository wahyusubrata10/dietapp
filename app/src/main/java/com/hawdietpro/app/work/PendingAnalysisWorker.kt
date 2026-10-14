package com.hawdietpro.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.core.Notifications
import com.hawdietpro.app.data.gemini.GeminiError
import com.hawdietpro.app.data.local.dao.PendingAnalysisDao
import com.hawdietpro.app.data.repository.MealAnalyzer
import com.hawdietpro.app.data.repository.MealRepository
import com.hawdietpro.app.data.repository.PendingStatus
import com.hawdietpro.app.domain.model.MealType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * Memproses foto di antrian offline: dianalisa Gemini lalu langsung tersimpan sebagai
 * catatan makan (tetap bisa diedit). Kegagalan sementara → retry dengan backoff.
 */
@HiltWorker
class PendingAnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: PendingAnalysisDao,
    private val analyzer: MealAnalyzer,
    private val meals: MealRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        dao.resetStuck()
        var needsRetry = false
        val processed = mutableSetOf<Long>()
        while (true) {
            val item = dao.getProcessable()
                .firstOrNull { it.id !in processed && it.status != PendingStatus.GAGAL.name && it.attempts < MAX_ATTEMPTS }
                ?: break
            processed += item.id
            if (!File(item.photoPath).exists()) {
                dao.delete(item.id)
                continue
            }
            dao.update(item.copy(status = PendingStatus.DIPROSES.name))
            try {
                val meal = analyzer.analyze(item.photoPath, MealType.parse(item.mealType), item.capturedAt, null)
                val id = meals.save(meal)
                dao.delete(item.id)
                Notifications.mealAnalyzed(
                    applicationContext,
                    id,
                    "${meal.menuName} tercatat",
                    "${Format.kcal(meal.total.calories)} · ${meal.mealType.label}. Ketuk untuk cek & edit.",
                )
            } catch (e: GeminiError) {
                val temporary = e.queueable
                dao.update(
                    item.copy(
                        status = if (temporary) PendingStatus.MENUNGGU.name else PendingStatus.GAGAL.name,
                        attempts = item.attempts + 1,
                        lastError = e.message,
                    ),
                )
                if (temporary) {
                    needsRetry = true
                    if (e is GeminiError.RateLimited) break // kuota habis: tunggu backoff
                } else {
                    Notifications.analysisFailed(applicationContext, e.message ?: "Analisa gagal")
                    if (e is GeminiError.MissingApiKey || e is GeminiError.InvalidApiKey) break
                }
            } catch (e: Exception) {
                dao.update(item.copy(status = PendingStatus.MENUNGGU.name, attempts = item.attempts + 1, lastError = e.message))
                needsRetry = true
            }
        }
        return if (needsRetry && runAttemptCount < 8) Result.retry() else Result.success()
    }

    companion object {
        const val NAME = "pending_analysis"
        const val MAX_ATTEMPTS = 10
    }
}
