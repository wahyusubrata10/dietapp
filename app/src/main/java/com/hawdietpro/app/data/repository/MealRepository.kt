package com.hawdietpro.app.data.repository

import com.hawdietpro.app.data.local.dao.MealDao
import com.hawdietpro.app.data.local.toDomain
import com.hawdietpro.app.data.local.toEntity
import com.hawdietpro.app.domain.model.Meal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealRepository @Inject constructor(
    private val dao: MealDao,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun startOf(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun observeDay(date: LocalDate): Flow<List<Meal>> = observeRange(date, date)

    /** Rentang tanggal inklusif. */
    fun observeRange(start: LocalDate, endInclusive: LocalDate): Flow<List<Meal>> =
        dao.observeMealsBetween(startOf(start), startOf(endInclusive.plusDays(1)))
            .map { list -> list.map { it.toDomain() } }

    suspend fun getRange(start: LocalDate, endInclusive: LocalDate): List<Meal> =
        dao.getMealsBetween(startOf(start), startOf(endInclusive.plusDays(1))).map { it.toDomain() }

    fun observeMeal(id: Long): Flow<Meal?> = dao.observeMeal(id).map { it?.toDomain() }

    suspend fun getMeal(id: Long): Meal? = dao.getMeal(id)?.toDomain()

    fun search(query: String): Flow<List<Meal>> = dao.searchMeals(query.trim()).map { list -> list.map { it.toDomain() } }

    /** Tanggal-tanggal yang memiliki catatan makan dalam rentang (untuk titik di kalender). */
    fun observeLoggedDays(start: LocalDate, endInclusive: LocalDate): Flow<Set<LocalDate>> =
        dao.observeMealTimes(startOf(start), startOf(endInclusive.plusDays(1)))
            .map { times -> times.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.toSet() }

    suspend fun save(meal: Meal): Long {
        val now = System.currentTimeMillis()
        val createdAt = if (meal.id != 0L) dao.getMeal(meal.id)?.meal?.createdAt ?: now else now
        val entity = meal.toEntity(now, createdAt)
        return dao.saveMeal(entity, meal.items.mapIndexed { i, item -> item.toEntity(meal.id, i) })
    }

    suspend fun delete(id: Long) {
        val photo = dao.getMeal(id)?.meal?.photoPath
        dao.deleteMeal(id)
        if (photo != null) withContext(Dispatchers.IO) { File(photo).delete() }
    }

    suspend fun getAll(): List<Meal> = dao.getAllMeals().map { it.toDomain() }

    suspend fun firstMealDate(): LocalDate? =
        dao.firstMealTime()?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
}
