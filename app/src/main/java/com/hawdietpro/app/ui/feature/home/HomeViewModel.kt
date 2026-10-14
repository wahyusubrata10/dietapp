package com.hawdietpro.app.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.data.prefs.SecureStorage
import com.hawdietpro.app.data.remote.RemoteConfigStore
import com.hawdietpro.app.data.repository.MealRepository
import com.hawdietpro.app.data.repository.PendingRepository
import com.hawdietpro.app.data.repository.ProfileRepository
import com.hawdietpro.app.data.repository.WeightProjectionRepository
import com.hawdietpro.app.domain.insight.Insight
import com.hawdietpro.app.domain.insight.InsightEngine
import com.hawdietpro.app.domain.insight.Period
import com.hawdietpro.app.domain.insight.PeriodStatsCalculator
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.model.sum
import com.hawdietpro.app.domain.nutrition.DailyTarget
import com.hawdietpro.app.domain.nutrition.TdeeCalculator
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Satu hari di strip tanggal Beranda. */
data class DayChip(val date: LocalDate, val calories: Double)

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
    val tip: String? = null,
    val hasApiKey: Boolean = true,
    val projection: WeightProjection? = null,
    val days: List<DayChip> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    meals: MealRepository,
    profiles: ProfileRepository,
    pending: PendingRepository,
    secure: SecureStorage,
    private val store: RemoteConfigStore,
    projections: WeightProjectionRepository,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())
    private val selected = MutableStateFlow(LocalDate.now())

    /** Strip 14 hari terakhir (kalori per hari). */
    private val strip = today.flatMapLatest { t ->
        meals.observeRange(t.minusDays(STRIP_DAYS - 1L), t).map { list ->
            val zone = ZoneId.systemDefault()
            val byDay = list.groupBy { Instant.ofEpochMilli(it.eatenAt).atZone(zone).toLocalDate() }
            (0 until STRIP_DAYS).map { i ->
                val d = t.minusDays((STRIP_DAYS - 1 - i).toLong())
                DayChip(d, byDay[d].orEmpty().sumOf { it.total.calories })
            }
        }
    }

    private val base = combine(
        selected.flatMapLatest { d -> meals.observeDay(d).map { d to it } },
        profiles.observeProfile(),
        pending.observe(),
        secure.hasApiKey,
        store.insightRules,
    ) { (date, list), profile, queue, hasKey, rules ->
        val target = profile?.let(TdeeCalculator::calculate)
        val stats = PeriodStatsCalculator(rules).compute(list, date, date, target?.calories)
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
            tip = tips.takeIf { it.isNotEmpty() }?.let { it[date.dayOfYear % it.size] },
            hasApiKey = hasKey,
        )
    }

    val state: StateFlow<HomeState> = combine(base, projections.observe(), strip) { home, proj, days ->
        home.copy(projection = proj, days = days)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

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

    private companion object {
        const val STRIP_DAYS = 14
    }
}
