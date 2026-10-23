package com.hawdietpro.app.data.repository

import com.hawdietpro.app.data.local.dao.FoodReferenceDao
import com.hawdietpro.app.data.local.toDomain
import com.hawdietpro.app.data.local.toEntity
import com.hawdietpro.app.data.local.toReference
import com.hawdietpro.app.data.prefs.AppPreferences
import com.hawdietpro.app.data.remote.RemoteConfigStore
import com.hawdietpro.app.domain.model.FoodReference
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.nutrition.FoodMatcher
import com.hawdietpro.app.domain.util.TextNormalizer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Database nutrisi lokal (TKPI + makanan populer + makanan buatan pengguna). */
@Singleton
class FoodRepository @Inject constructor(
    private val dao: FoodReferenceDao,
    private val store: RemoteConfigStore,
    private val prefs: AppPreferences,
) {
    private val mutex = Mutex()
    private var cachedMatcher: FoodMatcher? = null
    private var cachedRefs: Map<String, FoodReference> = emptyMap()

    /** Menyalin food_db.json (bawaan/remote) ke Room bila versinya berubah. */
    suspend fun seedIfNeeded(force: Boolean = false) = mutex.withLock {
        val file = store.loadFoodDb()
        val seeded = prefs.current().foodDbVersion
        if (!force && seeded == file.version && dao.count() > 0) return@withLock
        val refs = file.foods.map { it.toReference().toEntity(file.version) }
        dao.upsertAll(refs)
        dao.deleteBundledOlderThan(file.version)
        prefs.setFoodDbVersion(file.version)
        cachedMatcher = null
    }

    suspend fun matcher(): FoodMatcher = mutex.withLock {
        cachedMatcher ?: withContext(Dispatchers.Default) {
            val refs = dao.getAll().map { it.toDomain() }
            cachedRefs = refs.associateBy { it.id }
            FoodMatcher(refs).also { cachedMatcher = it }
        }
    }

    suspend fun getById(id: String): FoodReference? {
        matcher()
        return cachedRefs[id] ?: dao.getById(id)?.toDomain()
    }

    /** Pencocokan nama ke DB di thread latar (DB besar). */
    suspend fun match(name: String, cookingMethod: String? = null): FoodMatcher.Match? {
        val m = matcher()
        return withContext(Dispatchers.Default) { m.match(name, cookingMethod) }
    }

    suspend fun search(query: String, limit: Int = 30): List<FoodReference> {
        val m = matcher()
        return withContext(Dispatchers.Default) { m.search(query, limit) }
    }

    suspend fun all(): List<FoodReference> = dao.getAll().map { it.toDomain() }

    /** Menambah makanan buatan pengguna agar bisa dipakai lagi di input manual. */
    suspend fun addUserFood(name: String, per100g: Nutrients, servingG: Double, category: String = "hidangan"): FoodReference {
        val ref = FoodReference(
            id = "user_" + TextNormalizer.normalize(name).replace(' ', '_') + "_" + System.currentTimeMillis().toString(36),
            name = name.trim(),
            aliases = emptyList(),
            category = category,
            per100g = per100g,
            defaultServingG = servingG,
            servingLabel = "1 porsi",
            source = "USER",
        )
        dao.upsertAll(listOf(ref.toEntity(0)))
        mutex.withLock { cachedMatcher = null }
        return ref
    }
}
