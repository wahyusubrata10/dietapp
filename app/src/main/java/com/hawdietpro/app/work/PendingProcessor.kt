package com.hawdietpro.app.work

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.core.Notifications
import com.hawdietpro.app.data.gemini.GeminiError
import com.hawdietpro.app.data.local.dao.PendingAnalysisDao
import com.hawdietpro.app.data.local.entity.PendingAnalysisEntity
import com.hawdietpro.app.data.repository.MealAnalyzer
import com.hawdietpro.app.data.repository.MealRepository
import com.hawdietpro.app.data.repository.PendingStatus
import com.hawdietpro.app.di.ApplicationScope
import com.hawdietpro.app.domain.model.MealType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Memproses antrian foto. Selama aplikasi hidup, antrian langsung dikerjakan di sini (tanpa menunggu
 * jadwal WorkManager yang bisa ditunda berjam-jam oleh sistem/penghemat baterai). WorkManager hanya
 * cadangan bila aplikasi ditutup di tengah proses. Satu [Mutex] memastikan tidak ada analisa ganda.
 */
@Singleton
class PendingProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PendingAnalysisDao,
    private val analyzer: MealAnalyzer,
    private val meals: MealRepository,
    private val scheduler: WorkScheduler,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val triggers = Channel<Unit>(Channel.CONFLATED)
    private val started = AtomicBoolean(false)
    private var retryJob: Job? = null

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        _online.value = isOnline()
        runCatching {
            context.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _online.value = true
                        kick()
                    }

                    override fun onLost(network: Network) {
                        _online.value = false
                    }
                },
            )
        }
        // Setiap aplikasi dibuka lagi → langsung cek antrian.
        runCatching {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) kick() },
            )
        }
        scope.launch {
            for (t in triggers) runCatching { process(fromWorker = false) }
        }
        kick()
    }

    /** Minta antrian diproses sekarang (tidak memblokir; panggilan beruntun digabung). */
    fun kick() {
        triggers.trySend(Unit)
    }

    /** @return true bila masih ada foto yang perlu dicoba lagi nanti. */
    suspend fun process(fromWorker: Boolean): Boolean = mutex.withLock {
        dao.resetStuck()
        if (!fromWorker && !isOnline()) {
            _online.value = false
            // Tunggu jaringan: callback jaringan & WorkManager (constraint online) akan memicu lagi.
            if (hasProcessable()) scheduler.enqueuePendingAnalysis() else scheduler.cancelPendingAnalysis()
            return@withLock false
        }
        // Cadangan bila aplikasi ditutup di tengah analisa (KEEP: worker yang sedang jalan tidak dibatalkan).
        if (!fromWorker && hasProcessable()) scheduler.enqueuePendingAnalysis(BACKUP_DELAY_SECONDS)

        var needsRetry = false
        var retryAfterSeconds = DEFAULT_RETRY_SECONDS
        val processed = mutableSetOf<Long>()
        while (true) {
            val item = dao.getProcessable().firstOrNull { it.id !in processed && it.isAutoProcessable() } ?: break
            processed += item.id
            if (!File(item.photoPath).exists()) {
                dao.delete(item.id)
                continue
            }
            dao.update(item.copy(status = PendingStatus.DIPROSES.name, lastError = null))
            try {
                val meal = analyzer.analyze(item.photoPath, MealType.parse(item.mealType), item.capturedAt, item.hint)
                val id = meals.save(meal)
                dao.delete(item.id)
                Notifications.mealAnalyzed(
                    context,
                    id,
                    "${meal.menuName} tercatat",
                    "${Format.kcal(meal.total.calories)} · ${meal.mealType.label}. Ketuk untuk cek & edit.",
                )
            } catch (e: CancellationException) {
                if (!currentCoroutineContext().isActive) {
                    withContext(NonCancellable) { dao.update(item.copy(status = PendingStatus.MENUNGGU.name)) }
                    throw e
                }
                // Timeout internal, bukan pembatalan: anggap gagal sementara.
                needsRetry = markFailed(item, "AI terlalu lama menjawab.", temporary = true) || needsRetry
            } catch (e: GeminiError) {
                val retry = markFailed(item, e.message, temporary = e.queueable)
                needsRetry = needsRetry || retry
                if (e is GeminiError.RateLimited) {
                    // Kuota habis: jangan habiskan percobaan untuk foto lain, tunggu dulu.
                    retryAfterSeconds = maxOf(e.retryAfterSeconds ?: 0L, RATE_LIMIT_RETRY_SECONDS)
                    break
                }
                if (e is GeminiError.MissingApiKey || e is GeminiError.InvalidApiKey) break
            } catch (e: Exception) {
                needsRetry = markFailed(item, e.message ?: e.javaClass.simpleName, temporary = true) || needsRetry
            }
        }

        if (!fromWorker) {
            retryJob?.cancel()
            if (needsRetry) {
                scheduler.enqueuePendingAnalysis(retryAfterSeconds)
                retryJob = scope.launch {
                    delay(retryAfterSeconds * 1000)
                    kick()
                }
            } else {
                scheduler.cancelPendingAnalysis()
            }
        }
        needsRetry
    }

    /** @return true bila item masih akan dicoba otomatis. */
    private suspend fun markFailed(item: PendingAnalysisEntity, message: String?, temporary: Boolean): Boolean {
        val attempts = item.attempts + 1
        val giveUp = !temporary || attempts >= MAX_ATTEMPTS
        dao.update(
            item.copy(
                status = if (giveUp) PendingStatus.GAGAL.name else PendingStatus.MENUNGGU.name,
                attempts = attempts,
                lastError = message,
            ),
        )
        if (giveUp) Notifications.analysisFailed(context, message ?: "Analisa gagal")
        return !giveUp
    }

    private suspend fun hasProcessable() = dao.getProcessable().any { it.isAutoProcessable() }

    private fun PendingAnalysisEntity.isAutoProcessable() =
        status == PendingStatus.MENUNGGU.name && attempts < MAX_ATTEMPTS

    private fun isOnline(): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    companion object {
        const val MAX_ATTEMPTS = 6
        private const val BACKUP_DELAY_SECONDS = 180L
        private const val DEFAULT_RETRY_SECONDS = 120L
        private const val RATE_LIMIT_RETRY_SECONDS = 600L
    }
}
