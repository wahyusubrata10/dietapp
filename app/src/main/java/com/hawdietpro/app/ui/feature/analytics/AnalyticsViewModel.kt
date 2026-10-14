package com.hawdietpro.app.ui.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.data.remote.RemoteConfigStore
import com.hawdietpro.app.data.repository.MealRepository
import com.hawdietpro.app.data.repository.ProfileRepository
import com.hawdietpro.app.data.repository.WeightRepository
import com.hawdietpro.app.domain.insight.Insight
import com.hawdietpro.app.domain.insight.InsightEngine
import com.hawdietpro.app.domain.insight.Period
import com.hawdietpro.app.domain.insight.PeriodStats
import com.hawdietpro.app.domain.insight.PeriodStatsCalculator
import com.hawdietpro.app.domain.model.WeightEntry
import com.hawdietpro.app.domain.nutrition.DailyTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class AnalyticsUi(
    val period: Period = Period.WEEK,
    val offset: Int = 0,
    val start: LocalDate = LocalDate.now(),
    val end: LocalDate = LocalDate.now(),
    val stats: PeriodStats? = null,
    val insights: List<Insight> = emptyList(),
    val target: DailyTarget? = null,
    val weights: List<WeightEntry> = emptyList(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    meals: MealRepository,
    profiles: ProfileRepository,
    weights: WeightRepository,
    private val store: RemoteConfigStore,
) : ViewModel() {

    private data class Range(val period: Period, val offset: Int, val start: LocalDate, val end: LocalDate)

    private val selection = MutableStateFlow(Period.WEEK to 0)

    private val range = MutableStateFlow(computeRange(Period.WEEK, 0))

    val state: StateFlow<AnalyticsUi> = range.flatMapLatest { r ->
        combine(
            meals.observeRange(r.start, r.end),
            profiles.observeTarget(),
            weights.observeAll(),
            store.insightRules,
        ) { list, target, w, rules ->
            val today = LocalDate.now()
            val effectiveEnd = if (r.end.isAfter(today)) today else r.end
            val stats = PeriodStatsCalculator(rules).compute(list, r.start, effectiveEnd, target?.calories, w)
            AnalyticsUi(
                period = r.period,
                offset = r.offset,
                start = r.start,
                end = r.end,
                stats = stats,
                insights = if (store.appConfig.value.isEnabled("insights")) InsightEngine(rules).evaluate(stats, r.period) else emptyList(),
                target = target,
                weights = w,
                loading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUi())

    private fun computeRange(period: Period, offset: Int): Range {
        val today = LocalDate.now()
        return when (period) {
            Period.MONTH -> {
                val first = today.withDayOfMonth(1).plusMonths(offset.toLong())
                Range(period, offset, first, first.with(TemporalAdjusters.lastDayOfMonth()))
            }
            else -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
                Range(Period.WEEK, offset, monday, monday.plusDays(6))
            }
        }
    }

    fun setPeriod(period: Period) {
        selection.value = period to 0
        range.value = computeRange(period, 0)
    }

    fun shift(delta: Int) {
        val (p, o) = selection.value
        val next = (o + delta).coerceAtMost(0)
        selection.value = p to next
        range.value = computeRange(p, next)
    }
}
