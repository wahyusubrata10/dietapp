package com.hawdietpro.app.data.repository

import com.hawdietpro.app.di.ApplicationScope
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.core.PhotoFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Urungkan" untuk hapus catatan makan: catatan yang dihapus disimpan sebentar di memori,
 * fotonya baru dihapus setelah waktu urungkan lewat (atau saat ada penghapusan berikutnya).
 */
@Singleton
class DeleteUndo @Inject constructor(
    private val meals: MealRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var last: Meal? = null
    private var cleanup: Job? = null

    @Synchronized
    fun remember(meal: Meal) {
        // Penghapusan sebelumnya yang belum diurungkan: fotonya dibereskan sekarang.
        finalizePending()
        last = meal
        cleanup = scope.launch {
            delay(UNDO_WINDOW_MS)
            finalizePending()
        }
    }

    /** Kembalikan catatan terakhir yang dihapus (dengan id baru). */
    @Synchronized
    fun undo() {
        val meal = last ?: return
        last = null
        cleanup?.cancel()
        scope.launch { meals.save(meal.copy(id = 0, items = meal.items.map { it.copy(id = 0) })) }
    }

    @Synchronized
    private fun finalizePending() {
        val meal = last ?: return
        last = null
        cleanup?.cancel()
        meal.photoPath?.let { path ->
            scope.launch {
                withContext(Dispatchers.IO) { File(path).delete() }
                PhotoFiles.forget(path)
            }
        }
    }

    companion object {
        const val DELETED_MESSAGE = "Catatan dihapus"
        const val UNDO_WINDOW_MS = 11_000L
    }
}
