package com.hawdietpro.app.domain.insight

import com.hawdietpro.app.domain.model.Grade
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.WeightEntry
import com.hawdietpro.app.domain.nutrition.DayStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** Rekap satu minggu (Senin–Minggu) untuk tampilan "Wrapped". */
data class WeeklyWrap(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val daysLogged: Int,
    val mealCount: Int,
    val photoCount: Int,
    val totalIntake: Int,
    val totalExercise: Int,
    /** Selisih energi terhadap kebutuhan (negatif = defisit) pada hari tercatat. */
    val energyBalance: Int,
    val estimatedKg: Double,
    val statuses: List<DayStatus>,
    val avgProtein: Int,
    val proteinTarget: Int?,
    val avgFiber: Int,
    val topMenu: String?,
    val topMenuCount: Int,
    val goodGradeMeals: Int,
    /** Hari paling dekat dengan jatah (hanya hari tercatat). */
    val bestDay: LocalDate?,
    val weightChange: Double?,
    val avgCalories: Int,
) {
    val daysOnTarget: Int get() = statuses.count { it == DayStatus.SESUAI }
    val hasData: Boolean get() = daysLogged > 0
}

object WeeklyWrapCalculator {

    private const val KCAL_PER_KG = 7700.0

    /** Senin dari minggu sebelum minggu [today]. */
    fun lastWeekStart(today: LocalDate): LocalDate =
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)

    /**
     * [budgetFor] = jatah kalori hari itu (target + olahraga), [tdee] = kebutuhan harian tanpa olahraga,
     * [exerciseByDay] = kalori olahraga per hari.
     */
    fun compute(
        weekStart: LocalDate,
        meals: List<Meal>,
        budgetFor: (LocalDate) -> Int?,
        tdee: Int?,
        exerciseByDay: Map<LocalDate, Double>,
        weights: List<WeightEntry>,
        proteinTarget: Int?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): WeeklyWrap {
        val days = (0L until 7L).map { weekStart.plusDays(it) }
        val weekEnd = days.last()
        val byDay = meals.groupBy { Instant.ofEpochMilli(it.eatenAt).atZone(zone).toLocalDate() }
            .filterKeys { it in days }
        val intakeByDay = days.associateWith { d -> byDay[d]?.sumOf { it.total.calories } }
        val logged = days.filter { (intakeByDay[it] ?: 0.0) > 0.0 }
        val weekMeals = days.flatMap { byDay[it].orEmpty() }

        val statuses = days.map { DayStatus.of(intakeByDay[it], budgetFor(it)) }
        val totalExercise = days.sumOf { exerciseByDay[it] ?: 0.0 }
        val balance = if (tdee == null) 0.0 else logged.sumOf { d -> (intakeByDay[d] ?: 0.0) - tdee - (exerciseByDay[d] ?: 0.0) }

        val top = weekMeals.groupBy { it.menuName.trim().lowercase() }
            .maxByOrNull { it.value.size }
        val bestDay = logged.minByOrNull { d ->
            val budget = budgetFor(d) ?: return@minByOrNull Double.MAX_VALUE
            kotlin.math.abs((intakeByDay[d] ?: 0.0) - budget)
        }
        val weekWeights = weights.filter { LocalDate.ofEpochDay(it.epochDay) in days }.sortedBy { it.epochDay }
        val weightChange = if (weekWeights.size >= 2) weekWeights.last().weightKg - weekWeights.first().weightKg else null

        val n = logged.size.coerceAtLeast(1)
        val totalIntake = logged.sumOf { intakeByDay[it] ?: 0.0 }
        return WeeklyWrap(
            weekStart = weekStart,
            weekEnd = weekEnd,
            daysLogged = logged.size,
            mealCount = weekMeals.size,
            photoCount = weekMeals.count { it.photoPath != null },
            totalIntake = totalIntake.roundToInt(),
            totalExercise = totalExercise.roundToInt(),
            energyBalance = balance.roundToInt(),
            estimatedKg = balance / KCAL_PER_KG,
            statuses = statuses,
            avgProtein = (weekMeals.sumOf { it.total.protein } / n).roundToInt(),
            proteinTarget = proteinTarget,
            avgFiber = (weekMeals.sumOf { it.total.fiber } / n).roundToInt(),
            topMenu = top?.value?.first()?.menuName,
            topMenuCount = top?.value?.size ?: 0,
            goodGradeMeals = weekMeals.count { it.grade == Grade.A || it.grade == Grade.B },
            bestDay = bestDay,
            weightChange = weightChange,
            avgCalories = (totalIntake / n).roundToInt(),
        )
    }
}
