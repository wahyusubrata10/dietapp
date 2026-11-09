package com.hawdietpro.app.data.repository

import com.hawdietpro.app.data.local.dao.MealDao
import com.hawdietpro.app.data.local.toDomain
import com.hawdietpro.app.data.prefs.AppPreferences
import com.hawdietpro.app.domain.nutrition.Magnesium
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mengisi magnesium untuk catatan makan yang dibuat sebelum fitur magnesium ada (atau dipulihkan
 * dari cadangan lama). Tiap elemen dicocokkan ke database makanan (yang kini punya data magnesium);
 * bila tidak cocok, diperkirakan dari kategori & komposisinya. Hanya kolom magnesium yang diubah —
 * kalori, makro, gula & natrium tetap seperti yang tercatat.
 */
@Singleton
class MagnesiumBackfill @Inject constructor(
    private val meals: MealDao,
    private val foods: FoodRepository,
    private val prefs: AppPreferences,
) {
    private val mutex = Mutex()

    /** Dijalankan sekali per versi saat aplikasi dibuka. */
    suspend fun runIfNeeded() {
        if (prefs.current().magnesiumBackfill >= VERSION) return
        run()
        prefs.setMagnesiumBackfill(VERSION)
    }

    /** @return jumlah elemen yang diperbarui. */
    suspend fun run(): Int = mutex.withLock {
        foods.seedIfNeeded()
        val pending = meals.getElementsWithoutMagnesium()
        if (pending.isEmpty()) return@withLock 0
        val values = withContext(Dispatchers.Default) {
            pending.associate { e ->
                val ref = e.foodRefId?.let { foods.getById(it) } ?: foods.match(e.name, e.cookingMethod)?.reference
                e.id to Magnesium.forItem(e.toDomain(), ref)
            }
        }
        val changed = values.filterValues { it > 0 }
        if (changed.isNotEmpty()) {
            val mealIds = pending.filter { it.id in changed }.map { it.mealId }
            meals.applyMagnesium(changed, mealIds)
        }
        changed.size
    }

    private companion object {
        const val VERSION = 1
    }
}
