package com.hawdietpro.app.data.repository

import com.hawdietpro.app.data.local.dao.MealDao
import com.hawdietpro.app.domain.nutrition.WeightProjection
import com.hawdietpro.app.domain.nutrition.WeightProjector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Perkiraan berat harian sejak awal pemakaian, diperbarui otomatis setiap ada catatan baru. */
@Singleton
class WeightProjectionRepository @Inject constructor(
    private val mealDao: MealDao,
    private val profiles: ProfileRepository,
    private val weights: WeightRepository,
    private val exercises: ExerciseRepository,
    private val energy: EnergyRepository,
) {
    fun observe(): Flow<WeightProjection?> = combine(
        mealDao.observeCalorieEntries(),
        profiles.observeProfile(),
        weights.observeAll(),
        exercises.observeDailyTotals(),
        energy.observeApplied(),
    ) { entries, profile, logs, burned, adaptive ->
        if (profile == null) return@combine null
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val daily = entries.groupBy { Instant.ofEpochMilli(it.eatenAt).atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.sumOf { it.calories } }
        val firstMeal = daily.keys.minByOrNull { it.toEpochDay() }
        val (start, startWeight) = WeightProjector.startPoint(profile, firstMeal, logs, today)
        WeightProjector.project(profile, start, startWeight, today, daily, logs, burned, adaptive?.multiplier ?: 1.0)
    }.flowOn(Dispatchers.Default)
}
