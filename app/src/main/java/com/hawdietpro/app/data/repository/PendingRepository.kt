package com.hawdietpro.app.data.repository

import com.hawdietpro.app.data.local.dao.PendingAnalysisDao
import com.hawdietpro.app.data.local.entity.PendingAnalysisEntity
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.work.WorkScheduler
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class PendingStatus(val label: String) {
    MENUNGGU("Menunggu koneksi"),
    DIPROSES("Sedang dianalisa"),
    GAGAL("Gagal");

    companion object {
        fun parse(v: String) = entries.firstOrNull { it.name == v } ?: MENUNGGU
    }
}

/** Antrian foto yang belum dianalisa (offline / kuota habis). */
@Singleton
class PendingRepository @Inject constructor(
    private val dao: PendingAnalysisDao,
    private val scheduler: WorkScheduler,
) {
    fun observe(): Flow<List<PendingAnalysisEntity>> = dao.observeAll()

    suspend fun enqueue(photoPath: String, mealType: MealType, capturedAt: Long, hint: String? = null): Long {
        val id = dao.insert(
            PendingAnalysisEntity(
                photoPath = photoPath,
                mealType = mealType.name,
                capturedAt = capturedAt,
                status = PendingStatus.MENUNGGU.name,
                attempts = 0,
                lastError = null,
                createdAt = System.currentTimeMillis(),
                hint = hint?.takeIf { it.isNotBlank() },
            ),
        )
        scheduler.enqueuePendingAnalysis()
        return id
    }

    suspend fun retry(id: Long) {
        dao.getById(id)?.let { dao.update(it.copy(status = PendingStatus.MENUNGGU.name, lastError = null)) }
        scheduler.enqueuePendingAnalysis()
    }

    suspend fun retryAll() = scheduler.enqueuePendingAnalysis()

    suspend fun get(id: Long) = dao.getById(id)

    /** Hapus dari antrian. [deletePhoto] = false bila foto dipakai untuk input manual. */
    suspend fun remove(id: Long, deletePhoto: Boolean) {
        val item = dao.getById(id) ?: return
        dao.delete(id)
        if (deletePhoto) File(item.photoPath).delete()
    }
}
