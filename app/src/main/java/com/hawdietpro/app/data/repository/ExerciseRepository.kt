package com.hawdietpro.app.data.repository

import com.hawdietpro.app.data.local.dao.ExerciseDao
import com.hawdietpro.app.data.local.toDomain
import com.hawdietpro.app.data.local.toEntity
import com.hawdietpro.app.domain.model.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(private val dao: ExerciseDao) {

    fun observeDay(date: LocalDate): Flow<List<Exercise>> = observeRange(date, date)

    fun observeRange(start: LocalDate, endInclusive: LocalDate): Flow<List<Exercise>> =
        dao.observeRange(start.toEpochDay(), endInclusive.toEpochDay()).map { list -> list.map { it.toDomain() } }

    fun observeAll(): Flow<List<Exercise>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Total kalori olahraga per hari (semua catatan). */
    fun observeDailyTotals(): Flow<Map<LocalDate, Double>> = observeAll().map(::dailyTotals)

    suspend fun getAll(): List<Exercise> = dao.getAll().map { it.toDomain() }

    suspend fun save(exercise: Exercise): Long = dao.upsert(exercise.toEntity())

    suspend fun delete(id: Long) = dao.delete(id)

    companion object {
        fun dailyTotals(list: List<Exercise>): Map<LocalDate, Double> =
            list.groupBy { LocalDate.ofEpochDay(it.epochDay) }.mapValues { (_, l) -> l.sumOf { it.calories } }
    }
}
