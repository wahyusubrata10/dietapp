package com.hawdietpro.app.data.repository

import com.hawdietpro.app.data.local.dao.PendingAnalysisDao
import com.hawdietpro.app.data.local.entity.PendingAnalysisEntity
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.work.PendingProcessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class PendingStatus(val label: String) {
    MENUNGGU("Menunggu giliran"),
    DIPROSES("Sedang dianalisa…"),
    GAGAL("Gagal");

    companion object {
        fun parse(v: String) = entries.firstOrNull { it.name == v } ?: MENUNGGU
    }
}

/** Antrian foto yang belum dianalisa (offline / kuota habis). */
@Singleton
class PendingRepository @Inject constructor(
    private val dao: PendingAnalysisDao,
    private val processor: PendingProcessor,
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
        processor.kick()
        return id
    }

    /** Status koneksi untuk keterangan antrian. */
    val online: StateFlow<Boolean> get() = processor.online

    /** Foto yang sedang dianalisa (id ke waktu mulai). */
    val current: StateFlow<Pair<Long, Long>?> get() = processor.current

    /** Dipanggil saat layar antrian dibuka: cek ulang jaringan & proses yang tertunda. */
    fun refresh() {
        processor.refreshOnline()
        processor.kick()
    }

    /** Coba ulang sekarang (jumlah percobaan direset). */
    suspend fun retry(id: Long) {
        dao.getById(id)?.let { reset(it) }
        processor.kick()
    }

    suspend fun retryAll() {
        dao.getAll().forEach { reset(it) }
        processor.kick()
    }

    private suspend fun reset(item: PendingAnalysisEntity) {
        if (processor.isProcessing(item.id)) return
        dao.update(item.copy(status = PendingStatus.MENUNGGU.name, attempts = 0, lastError = null))
    }

    suspend fun get(id: Long) = dao.getById(id)

    /** Hapus dari antrian. [deletePhoto] = false bila foto dipakai untuk input manual. */
    suspend fun remove(id: Long, deletePhoto: Boolean) {
        val item = dao.getById(id) ?: return
        dao.delete(id)
        if (deletePhoto) File(item.photoPath).delete()
    }
}
