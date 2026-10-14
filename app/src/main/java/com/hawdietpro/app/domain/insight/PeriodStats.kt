package com.hawdietpro.app.domain.insight

import com.hawdietpro.app.domain.config.InsightRulesFile
import com.hawdietpro.app.domain.model.Grade
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.model.WeightEntry
import com.hawdietpro.app.domain.model.sum
import com.hawdietpro.app.domain.scoring.FoodClassifier
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class Period(val label: String) {
    DAY("hari ini"),
    WEEK("minggu ini"),
    MONTH("bulan ini"),
}

data class DayTotal(val date: LocalDate, val nutrients: Nutrients, val mealCount: Int)

data class RankedItem(val name: String, val count: Int, val calories: Double, val sharePct: Double)

data class PeriodStats(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val days: List<DayTotal>,
    val daysLogged: Int,
    val totalCalories: Double,
    /** Rata-rata per hari tercatat. */
    val averagePerDay: Nutrients,
    val daysOver: Int,
    val daysUnder: Int,
    val daysOnTarget: Int,
    val mealTypeCalories: Map<MealType, Double>,
    /** Kalori per jam (index 0–23). */
    val hourlyCalories: List<Double>,
    val topMenus: List<RankedItem>,
    val topSources: List<RankedItem>,
    val weightChangeKg: Double?,
    /** Jumlah makan per grade skor gizi. */
    val gradeCounts: Map<Grade, Int>,
    /** Rata-rata kalori per hari dalam seminggu (hanya hari tercatat). */
    val weekdayAverages: Map<DayOfWeek, Double>,
    /** Hari berturut-turut tercatat sampai akhir periode. */
    val currentStreak: Int,
    val metrics: Map<String, Double>,
    val texts: Map<String, String>,
)

/** Menghitung statistik periode (harian/mingguan/bulanan) secara lokal. */
class PeriodStatsCalculator(private val rules: InsightRulesFile, private val zone: ZoneId = ZoneId.systemDefault()) {

    private val classifier = FoodClassifier(rules)

    fun compute(
        meals: List<Meal>,
        start: LocalDate,
        endInclusive: LocalDate,
        dailyTargetCalories: Int?,
        weights: List<WeightEntry> = emptyList(),
    ): PeriodStats {
        val inRange = meals.filter {
            val d = it.date()
            !d.isBefore(start) && !d.isAfter(endInclusive)
        }
        val byDay = inRange.groupBy { it.date() }
        val dayCount = ChronoUnit.DAYS.between(start, endInclusive).toInt() + 1
        val days = (0 until dayCount.coerceAtLeast(0)).map { offset ->
            val date = start.plusDays(offset.toLong())
            val list = byDay[date].orEmpty()
            DayTotal(date, list.map { it.total }.sum(), list.size)
        }
        val logged = days.filter { it.mealCount > 0 }
        val daysLogged = logged.size
        val total = inRange.map { it.total }.sum()
        val avg = if (daysLogged > 0) total * (1.0 / daysLogged) else Nutrients.ZERO

        var over = 0
        var under = 0
        var onTarget = 0
        if (dailyTargetCalories != null && dailyTargetCalories > 0) {
            val tol = rules.targetTolerancePct / 100.0
            logged.forEach { d ->
                when {
                    d.nutrients.calories > dailyTargetCalories * (1 + tol) -> over++
                    d.nutrients.calories < dailyTargetCalories * (1 - tol) -> under++
                    else -> onTarget++
                }
            }
        }

        val totalKcal = total.calories
        fun share(part: Double) = if (totalKcal > 0) part / totalKcal * 100 else 0.0

        val mealTypeCalories = MealType.entries.associateWith { type ->
            inRange.filter { it.mealType == type }.sumOf { it.total.calories }
        }

        val hourly = MutableList(24) { 0.0 }
        inRange.forEach { meal ->
            val hour = Instant.ofEpochMilli(meal.eatenAt).atZone(zone).hour
            hourly[hour] += meal.total.calories
        }
        val lateNight = hourly.withIndex().filter { it.index >= rules.lateNightHour || it.index < 4 }.sumOf { it.value }

        val allItems = inRange.flatMap { it.items }
        val friedKcal = allItems.filter(classifier::isFried).sumOf { it.nutrients.calories }
        val sweetDrinkKcal = allItems.filter(classifier::isSweetDrink).sumOf { it.nutrients.calories }
        val vegDays = byDay.count { (_, list) -> list.flatMap { it.items }.any(classifier::isVegetable) }

        val topMenus = inRange.groupBy { it.menuName.trim().lowercase() }
            .map { (_, list) ->
                val kcal = list.sumOf { it.total.calories }
                RankedItem(list.first().menuName.trim(), list.size, kcal, share(kcal))
            }
            .sortedWith(compareByDescending<RankedItem> { it.count }.thenByDescending { it.calories })
            .take(5)

        val topSources = allItems.groupBy { it.name.trim().lowercase() }
            .map { (_, list) ->
                val kcal = list.sumOf { it.nutrients.calories }
                RankedItem(list.first().name.trim(), list.size, kcal, share(kcal))
            }
            .sortedByDescending { it.calories }
            .take(5)

        val breakfastSkip = logged.count { day ->
            byDay[day.date].orEmpty().none { it.mealType == MealType.SARAPAN }
        }

        val weightsInRange = weights
            .filter { val d = LocalDate.ofEpochDay(it.epochDay); !d.isBefore(start) && !d.isAfter(endInclusive) }
            .sortedBy { it.epochDay }
        val weightChange = if (weightsInRange.size >= 2) weightsInRange.last().weightKg - weightsInRange.first().weightKg else null

        val gradeCounts = Grade.entries.associateWith { g -> inRange.count { it.grade == g } }
        val weekdayAverages = logged.groupBy { it.date.dayOfWeek }
            .mapValues { (_, list) -> list.map { it.nutrients.calories }.average() }
        var streak = 0
        for (d in days.asReversed()) {
            if (d.mealCount > 0) streak++ else if (streak > 0 || d.date != endInclusive) break
        }

        val split = avg.macroEnergy
        val metrics = buildMap {
            put("daysLogged", daysLogged.toDouble())
            put("daysInPeriod", dayCount.toDouble())
            put("avgCalories", avg.calories)
            put("avgProteinG", avg.protein)
            put("avgCarbsG", avg.carbs)
            put("avgFatG", avg.fat)
            put("avgFiberG", avg.fiber)
            put("avgSugarG", avg.sugar)
            put("avgSodiumMg", avg.sodium)
            put("daysSugarOver", logged.count { it.nutrients.sugar > 50.0 }.toDouble())
            put("daysSodiumOver", logged.count { it.nutrients.sodium > 2000.0 }.toDouble())
            put("avgProteinPct", split.proteinPct)
            put("avgCarbsPct", split.carbsPct)
            put("avgFatPct", split.fatPct)
            put("daysOverTarget", over.toDouble())
            put("daysUnderTarget", under.toDouble())
            put("daysOnTarget", onTarget.toDouble())
            put("daysOnTargetPct", if (daysLogged > 0) onTarget * 100.0 / daysLogged else 0.0)
            MealType.entries.forEach { put("mealTypeShare.${it.name}", share(mealTypeCalories[it] ?: 0.0)) }
            put("lateNightShare", share(lateNight))
            put("friedShare", share(friedKcal))
            put("sweetDrinkShare", share(sweetDrinkKcal))
            put("vegDaysPct", if (daysLogged > 0) vegDays * 100.0 / daysLogged else 0.0)
            put("breakfastSkipDays", breakfastSkip.toDouble())
            put("topSourceShare", topSources.firstOrNull()?.sharePct ?: 0.0)
            put("topMenuCount", topMenus.firstOrNull()?.count?.toDouble() ?: 0.0)
            put("currentStreak", streak.toDouble())
            weightChange?.let { put("weightChange", it) }
        }
        val texts = buildMap {
            topSources.firstOrNull()?.let { put("topSource", it.name) }
            topMenus.firstOrNull()?.let { put("topMenu", it.name) }
        }

        return PeriodStats(
            start = start,
            endInclusive = endInclusive,
            days = days,
            daysLogged = daysLogged,
            totalCalories = totalKcal,
            averagePerDay = avg,
            daysOver = over,
            daysUnder = under,
            daysOnTarget = onTarget,
            mealTypeCalories = mealTypeCalories,
            hourlyCalories = hourly,
            topMenus = topMenus,
            topSources = topSources,
            weightChangeKg = weightChange,
            gradeCounts = gradeCounts,
            weekdayAverages = weekdayAverages,
            currentStreak = streak,
            metrics = metrics,
            texts = texts,
        )
    }

    private fun Meal.date(): LocalDate = Instant.ofEpochMilli(eatenAt).atZone(zone).toLocalDate()
}
