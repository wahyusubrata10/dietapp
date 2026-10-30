package com.hawdietpro.app.data.repository

import com.hawdietpro.app.data.local.dao.MealDao
import com.hawdietpro.app.data.prefs.AppPreferences
import com.hawdietpro.app.domain.nutrition.AdaptiveTdee
import com.hawdietpro.app.domain.nutrition.AdaptiveTdeeEstimator
import com.hawdietpro.app.domain.nutrition.DailyTarget
import com.hawdietpro.app.domain.nutrition.TdeeCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sumber tunggal kebutuhan kalori & target harian.
 * Target memakai TDEE adaptif (dipelajari dari catatan makan + timbangan) bila diaktifkan dan datanya cukup.
 */
@Singleton
class EnergyRepository @Inject constructor(
    private val mealDao: MealDao,
    private val profiles: ProfileRepository,
    private val weights: WeightRepository,
    private val exercises: ExerciseRepository,
    private val prefs: AppPreferences,
) {
    /** Total kalori masuk per hari (semua catatan). */
    fun observeDailyIntake(): Flow<Map<LocalDate, Double>> = mealDao.observeCalorieEntries().map { entries ->
        val zone = ZoneId.systemDefault()
        entries.groupBy { Instant.ofEpochMilli(it.eatenAt).atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.sumOf { it.calories } }
    }

    /** Hasil belajar kebutuhan kalori (selalu dihitung agar bisa ditampilkan, walau fitur dimatikan). */
    fun observeAdaptive(): Flow<AdaptiveTdee?> = combine(
        observeDailyIntake(),
        profiles.observeProfile(),
        weights.observeAll(),
        exercises.observeDailyTotals(),
    ) { intake, profile, logs, burned ->
        profile?.let { AdaptiveTdeeEstimator.estimate(it, LocalDate.now(), intake, logs, burned) }
    }.flowOn(Dispatchers.Default)

    val adaptiveEnabled: Flow<Boolean> = prefs.settings.map { it.adaptiveTdee }.distinctUntilChanged()

    /** Hasil adaptif yang benar-benar dipakai (null = pakai rumus). */
    fun observeApplied(): Flow<AdaptiveTdee?> = combine(observeAdaptive(), adaptiveEnabled) { a, on -> a?.takeIf { on && it.status != AdaptiveTdee.Status.BELUM_CUKUP } }

    fun observeTarget(): Flow<DailyTarget?> = combine(profiles.observeProfile(), observeApplied()) { p, a ->
        p?.let { TdeeCalculator.calculate(it, a?.tdee) }
    }.distinctUntilChanged()

    suspend fun getTarget(): DailyTarget? = observeTarget().first()

    suspend fun setAdaptiveEnabled(value: Boolean) = prefs.setAdaptiveTdee(value)
}
