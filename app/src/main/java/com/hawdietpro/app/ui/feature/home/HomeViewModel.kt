package com.hawdietpro.app.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.data.prefs.SecureStorage
import com.hawdietpro.app.data.remote.RemoteConfigStore
import com.hawdietpro.app.data.repository.AchievementRepository
import com.hawdietpro.app.data.repository.EnergyRepository
import com.hawdietpro.app.data.repository.ExerciseRepository
import com.hawdietpro.app.data.repository.MealRepository
import com.hawdietpro.app.data.repository.PendingRepository
import com.hawdietpro.app.data.repository.PendingStatus
import com.hawdietpro.app.data.repository.ProfileRepository
import com.hawdietpro.app.data.repository.WeightProjectionRepository
import com.hawdietpro.app.domain.achievement.Achievement
import com.hawdietpro.app.domain.insight.Insight
import com.hawdietpro.app.domain.insight.InsightEngine
import com.hawdietpro.app.domain.insight.Period
import com.hawdietpro.app.domain.insight.PeriodStatsCalculator
import com.hawdietpro.app.domain.model.Exercise
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.model.sum
import com.hawdietpro.app.domain.nutrition.AdaptiveTdee
import com.hawdietpro.app.domain.nutrition.DailyTarget
import com.hawdietpro.app.domain.nutrition.WeightProjection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

/** Satu hari di strip tanggal Beranda. */
/** [exercise] = kalori olahraga hari itu, menambah jatah seperti di kartu utama. */
data class DayChip(val date: LocalDate, val calories: Double, val exercise: Double = 0.0)

data class HomeState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val isToday: Boolean = true,
    val name: String = "",
    val target: DailyTarget? = null,
    val meals: List<Meal> = emptyList(),
    val total: Nutrients = Nutrients.ZERO,
    val byType: Map<MealType, List<Meal>> = emptyMap(),
    val insights: List<Insight> = emptyList(),
    val pendingCount: Int = 0,
    val pendingFailed: Int = 0,
    val tip: String? = null,
    val hasApiKey: Boolean = true,
    val projection: WeightProjection? = null,
    val days: List<DayChip> = emptyList(),
    /** Catatan sehari sebelumnya, per waktu makan (untuk "Salin dari kemarin"). */
    val previousDayByType: Map<MealType, List<Meal>> = emptyMap(),
    /** Badge yang baru terbuka dan belum dilihat. */
    val newAchievements: List<Achievement> = emptyList(),
    /** Olahraga pada tanggal yang dipilih. */
    val exercises: List<Exercise> = emptyList(),
    /** Kebutuhan kalori yang dipelajari dari data. */
    val adaptive: AdaptiveTdee? = null,
    /** Hari berturut-turut dengan catatan makan (sampai hari ini/kemarin). */
    val streak: Int = 0,
) {
    val exerciseKcal: Double get() = exercises.sumOf { it.calories }
    /** Jatah kalori hari itu = target + kalori olahraga. */
    val budget: Int? get() = target?.let { it.calories + exerciseKcal.roundToInt() }
    /** Sisa kalori = target − makan + olahraga. */
    val remaining: Int? get() = budget?.let { it - total.calories.roundToInt() }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val meals: MealRepository,
    profiles: ProfileRepository,
    pending: PendingRepository,
    secure: SecureStorage,
    private val store: RemoteConfigStore,
    projections: WeightProjectionRepository,
    achievements: AchievementRepository,
    exercises: ExerciseRepository,
    energy: EnergyRepository,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())
    private val selected = MutableStateFlow(LocalDate.now())

    /** Strip 14 hari terakhir (kalori per hari). */
    private val strip = today.flatMapLatest { t ->
        val start = t.minusDays(STRIP_DAYS - 1L)
        combine(meals.observeRange(start, t), exercises.observeRange(start, t)) { list, ex ->
            val zone = ZoneId.systemDefault()
            val byDay = list.groupBy { Instant.ofEpochMilli(it.eatenAt).atZone(zone).toLocalDate() }
            val burned = ExerciseRepository.dailyTotals(ex)
            (0 until STRIP_DAYS).map { i ->
                val d = t.minusDays((STRIP_DAYS - 1 - i).toLong())
                DayChip(d, byDay[d].orEmpty().sumOf { it.total.calories }, burned[d] ?: 0.0)
            }
        }
    }

    private val base = combine(
        selected.flatMapLatest { d -> combine(meals.observeDay(d), exercises.observeDay(d)) { m, e -> Triple(d, m, e) } },
        combine(profiles.observeProfile(), energy.observeTarget()) { p, t -> p to t },
        pending.observe(),
        secure.hasApiKey,
        store.insightRules,
    ) { (date, list, exerciseList), (profile, target), queue, hasKey, rules ->
        val burned = ExerciseRepository.dailyTotals(exerciseList)
        val stats = PeriodStatsCalculator(rules).compute(list, date, date, target?.calories, exerciseByDay = burned)
        val tips = store.appConfig.value.tips
        HomeState(
            loading = false,
            date = date,
            isToday = date == LocalDate.now(),
            name = profile?.name.orEmpty(),
            target = target,
            meals = list,
            total = list.map { it.total }.sum(),
            byType = list.sortedBy { it.eatenAt }.groupBy { it.mealType },
            insights = if (store.appConfig.value.isEnabled("insights")) InsightEngine(rules).evaluate(stats, Period.DAY) else emptyList(),
            pendingCount = queue.size,
            pendingFailed = queue.count { it.status == PendingStatus.GAGAL.name },
            tip = tips.takeIf { it.isNotEmpty() }?.let { it[date.dayOfYear % it.size] },
            hasApiKey = hasKey,
            exercises = exerciseList,
        )
    }

    private val previousDay = selected.flatMapLatest { d -> meals.observeDay(d.minusDays(1)) }
        .map { list -> list.sortedBy { it.eatenAt }.groupBy { it.mealType } }

    val state: StateFlow<HomeState> = combine(
        base,
        combine(projections.observe(), energy.observeAdaptive(), energy.observeDailyIntake()) { p, a, intake -> Triple(p, a, streakOf(intake.keys)) },
        strip,
        previousDay,
        achievements.observeNew(),
    ) { home, (proj, adaptive, streak), days, prev, fresh ->
        home.copy(projection = proj, adaptive = adaptive, streak = streak, days = days, previousDayByType = prev, newAchievements = fresh)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** Salin semua makan [type] dari hari sebelumnya ke hari yang sedang dilihat (jam sama). */
    fun copyFromPreviousDay(type: MealType) {
        val date = state.value.date
        val source = state.value.previousDayByType[type].orEmpty()
        if (source.isEmpty()) return
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            source.forEach { meal ->
                val time = Instant.ofEpochMilli(meal.eatenAt).atZone(zone).toLocalTime()
                meals.duplicate(meal, date.atTime(time).atZone(zone).toInstant().toEpochMilli(), type)
            }
        }
    }

    fun select(date: LocalDate) {
        selected.value = date
    }

    /** Dipanggil saat layar tampil agar "hari ini" selalu benar setelah lewat tengah malam. */
    fun refreshDate() {
        val now = LocalDate.now()
        if (today.value != now) {
            if (selected.value == today.value) selected.value = now
            today.value = now
        }
    }

    companion object {
        private const val STRIP_DAYS = 14

        /** Jumlah hari berturut-turut yang punya catatan, dihitung mundur dari hari ini (atau kemarin bila hari ini belum). */
        fun streakOf(days: Set<LocalDate>, today: LocalDate = LocalDate.now()): Int {
            var d = if (today in days) today else today.minusDays(1)
            var n = 0
            while (d in days) {
                n++
                d = d.minusDays(1)
            }
            return n
        }
    }
}
