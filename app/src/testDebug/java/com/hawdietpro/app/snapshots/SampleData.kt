package com.hawdietpro.app.snapshots

import com.hawdietpro.app.domain.insight.Insight
import com.hawdietpro.app.domain.model.ActivityLevel
import com.hawdietpro.app.domain.model.Exercise
import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.Goal
import com.hawdietpro.app.domain.model.Grade
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.model.Sex
import com.hawdietpro.app.domain.model.UserProfile
import com.hawdietpro.app.domain.model.WeightEntry
import com.hawdietpro.app.domain.model.sum
import com.hawdietpro.app.domain.nutrition.AdaptiveTdeeEstimator
import com.hawdietpro.app.domain.nutrition.TdeeCalculator
import com.hawdietpro.app.domain.nutrition.WeightProjector
import com.hawdietpro.app.ui.feature.home.DayChip
import com.hawdietpro.app.ui.feature.home.HomeState
import java.time.LocalDate
import java.time.ZoneId

/** Data contoh realistis untuk snapshot tampilan. */
object SampleData {
    private val today: LocalDate = LocalDate.now()
    private val zone = ZoneId.systemDefault()
    private fun at(hour: Int, minute: Int = 0) = today.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    val profile = UserProfile("Wahyu Subrata", 30, Sex.PRIA, 175.0, 87.6, ActivityLevel.SEDENTARI, Goal.TURUN, 80.0)

    private fun item(name: String, g: Double, kcal: Double, p: Double, c: Double, f: Double, fiber: Double = 1.0) =
        FoodItem(name = name, grams = g, nutrients = Nutrients(kcal, p, c, f, fiber, 4.0, 300.0))

    val meals = listOf(
        Meal(
            id = 1, eatenAt = at(7, 30), mealType = MealType.SARAPAN, menuName = "Nasi uduk telur balado", photoPath = null, confidence = 0.8,
            items = listOf(item("Nasi uduk", 150.0, 280.0, 5.0, 52.0, 6.0), item("Telur balado", 60.0, 120.0, 8.0, 3.0, 8.0)),
            grade = Grade.C,
        ),
        Meal(
            id = 2, eatenAt = at(12, 40), mealType = MealType.MAKAN_SIANG, menuName = "Ayam suwir bumbu Bali + sayur", photoPath = null, confidence = 0.86,
            items = listOf(item("Nasi putih", 150.0, 195.0, 4.0, 43.0, 0.5), item("Ayam suwir bumbu Bali", 100.0, 210.0, 24.0, 5.0, 10.0), item("Tumis kangkung", 80.0, 60.0, 2.5, 4.0, 4.0, 2.5)),
            grade = Grade.B,
        ),
        Meal(
            id = 3, eatenAt = at(16, 5), mealType = MealType.CAMILAN, menuName = "Pisang & kopi susu", photoPath = null, confidence = 0.9,
            items = listOf(item("Pisang ambon", 100.0, 92.0, 1.0, 23.0, 0.3, 2.6), item("Kopi susu gula aren", 200.0, 150.0, 3.0, 24.0, 4.0)),
            grade = Grade.D,
        ),
    )

    private val exercises = listOf(
        Exercise(id = 1, epochDay = today.toEpochDay(), performedAt = at(6), activityId = "jalan_cepat", name = "Jalan cepat", durationMin = 40, calories = 230.0, source = Exercise.SOURCE_ESTIMATE),
        Exercise(id = 2, epochDay = today.toEpochDay(), performedAt = at(18), activityId = "sepeda_santai", name = "Sepeda santai", durationMin = 30, calories = 170.0, source = Exercise.SOURCE_ESTIMATE),
    )

    private val start = today.minusDays(17)
    private val weights = listOf(
        WeightEntry(1, start.toEpochDay(), 90.0),
        WeightEntry(2, start.plusDays(6).toEpochDay(), 88.9),
        WeightEntry(3, start.plusDays(11).toEpochDay(), 87.9),
        WeightEntry(4, start.plusDays(16).toEpochDay(), 87.6),
    )
    private val intake = (0 until 17).associate { start.plusDays(it.toLong()) to (1250.0 + (it % 4) * 120) }
    private val burned = (0 until 17 step 2).associate { start.plusDays(it.toLong()) to 250.0 }

    fun home(): HomeState {
        val adaptive = AdaptiveTdeeEstimator.estimate(profile, today, intake, weights, burned)
        val target = TdeeCalculator.calculate(profile, adaptive.tdee)
        val projection = WeightProjector.project(profile, start, 90.0, today, intake, weights, burned, adaptive.multiplier)
        val days = (0 until 14).map { i ->
            val d = today.minusDays((13 - i).toLong())
            DayChip(d, if (d == today) meals.sumOf { it.total.calories } else intake[d] ?: 0.0, burned[d] ?: 0.0)
        }
        return HomeState(
            loading = false,
            date = today,
            isToday = true,
            name = profile.name,
            target = target,
            meals = meals,
            total = meals.map { it.total }.sum(),
            byType = meals.groupBy { it.mealType },
            insights = listOf(
                Insight("protein", "Protein hari ini baru 55% target. Tambah lauk tinggi protein seperti tempe, ikan, atau dada ayam.", "warning", 1),
                Insight("streak", "Mantap! Kamu mencatat 17 hari berturut-turut.", "positive", 2),
            ),
            pendingCount = 1,
            tip = "Minum segelas air sebelum makan membantu merasa kenyang lebih cepat.",
            hasApiKey = true,
            projection = projection,
            days = days,
            previousDayByType = mapOf(MealType.MAKAN_MALAM to listOf(meals[1].copy(menuName = "Pecel lele + lalapan"))),
            exercises = exercises,
            adaptive = adaptive,
            streak = 17,
        )
    }

    /** Hari sibuk: banyak catatan, nama menu & nama pengguna sangat panjang. */
    fun homeBusyLongNames(): HomeState {
        val base = home()
        val many = (0 until 14).map { i ->
            val m = meals[i % meals.size]
            m.copy(
                id = 50L + i,
                eatenAt = at(6 + i, 10),
                mealType = MealType.entries[i % MealType.entries.size],
                menuName = if (i % 3 == 0) "Nasi campur ayam betutu sambal matah lawar sayur urap telur pindang kerupuk" else m.menuName,
            )
        }
        return base.copy(
            name = "Ni Luh Putu Ayu Kadek Wahyuningsih Subrata",
            meals = many,
            total = many.map { it.total }.sum(),
            byType = many.groupBy { it.mealType },
        )
    }

    fun homeEmpty(): HomeState {
        val target = TdeeCalculator.calculate(profile)
        val days = (0 until 14).map { i -> DayChip(today.minusDays((13 - i).toLong()), 0.0) }
        return HomeState(
            loading = false,
            date = today,
            isToday = true,
            name = profile.name,
            target = target,
            hasApiKey = false,
            days = days,
            adaptive = AdaptiveTdeeEstimator.estimate(profile, today, emptyMap(), emptyList()),
        )
    }

    /** Data minggu lalu untuk rekap (rentang 7 hari dengan pola realistis). */
    fun wrap(): com.hawdietpro.app.domain.insight.WeeklyWrap {
        val start = com.hawdietpro.app.domain.insight.WeeklyWrapCalculator.lastWeekStart(today)
        val names = listOf("Nasi uduk telur balado", "Ayam suwir bumbu Bali", "Pecel lele", "Ayam suwir bumbu Bali", "Gado-gado", "Soto ayam", "Ayam suwir bumbu Bali")
        val weekMeals = (0 until 6).flatMap { i ->
            val d = start.plusDays(i.toLong())
            val t = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            listOf(
                Meal(id = 100L + i, eatenAt = t, mealType = MealType.MAKAN_SIANG, menuName = names[i], photoPath = "x.jpg", confidence = 0.8,
                    items = listOf(item(names[i], 300.0, 1250.0 + i * 140, 70.0 + i * 5, 150.0, 40.0, 14.0)), grade = if (i % 2 == 0) Grade.B else Grade.C),
            )
        }
        val ws = listOf(WeightEntry(10, start.toEpochDay(), 88.6), WeightEntry(11, start.plusDays(6).toEpochDay(), 87.6))
        return com.hawdietpro.app.domain.insight.WeeklyWrapCalculator.compute(
            start, weekMeals, { 1900 }, 2600, mapOf(start.plusDays(1) to 300.0, start.plusDays(3) to 250.0), ws, 140, zone,
        )
    }

    /** Statistik mingguan contoh untuk ringkasan Analisa. */
    fun weekStats(): com.hawdietpro.app.domain.insight.PeriodStats {
        val calc = com.hawdietpro.app.domain.insight.PeriodStatsCalculator(com.hawdietpro.app.domain.config.InsightRulesFile(version = 1, mealScoring = com.hawdietpro.app.domain.config.MealScoringConfig(rules = emptyList(), grades = emptyList())), zone)
        val start = today.minusDays(6)
        val list = (0 until 7).filter { it != 2 }.map { i ->
            val d = start.plusDays(i.toLong())
            Meal(id = 200L + i, eatenAt = d.atTime(13, 0).atZone(zone).toInstant().toEpochMilli(), mealType = MealType.MAKAN_SIANG,
                menuName = if (i % 2 == 0) "Ayam suwir bumbu Bali" else "Pecel lele", photoPath = null, confidence = 0.8,
                items = listOf(item("Menu", 300.0, listOf(1700.0, 2300.0, 0.0, 1900.0, 1500.0, 2000.0, 1850.0)[i], 80.0, 200.0, 50.0, 12.0)))
        }
        return calc.compute(list, start, today, 1900)
    }
}
