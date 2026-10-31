package com.hawdietpro.app.ui.feature.analytics

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.data.gemini.GeminiError
import com.hawdietpro.app.data.remote.RemoteConfigStore
import com.hawdietpro.app.data.repository.CachedReview
import com.hawdietpro.app.data.repository.EnergyRepository
import com.hawdietpro.app.data.repository.ExerciseRepository
import com.hawdietpro.app.data.repository.MealRepository
import com.hawdietpro.app.data.repository.PeriodReviewRepository
import com.hawdietpro.app.data.repository.ProfileRepository
import com.hawdietpro.app.data.repository.WeightProjectionRepository
import com.hawdietpro.app.data.repository.WeightRepository
import com.hawdietpro.app.domain.insight.Insight
import com.hawdietpro.app.domain.insight.InsightEngine
import com.hawdietpro.app.domain.insight.Period
import com.hawdietpro.app.domain.insight.PeriodStats
import com.hawdietpro.app.domain.insight.PeriodStatsCalculator
import com.hawdietpro.app.domain.model.UserProfile
import com.hawdietpro.app.domain.nutrition.DailyTarget
import com.hawdietpro.app.domain.nutrition.WeightProjection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val previous: PeriodStats? = null,
    val insights: List<Insight> = emptyList(),
    val profile: UserProfile? = null,
    val target: DailyTarget? = null,
    val projection: WeightProjection? = null,
    val loading: Boolean = true,
)

sealed interface ReviewState {
    data object Unavailable : ReviewState
    data object Idle : ReviewState
    data object Loading : ReviewState
    data class Ready(val cached: CachedReview, val outdated: Boolean) : ReviewState
    data class Error(val message: String) : ReviewState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    meals: MealRepository,
    profiles: ProfileRepository,
    weights: WeightRepository,
    projections: WeightProjectionRepository,
    private val reviews: PeriodReviewRepository,
    private val store: RemoteConfigStore,
    exercises: ExerciseRepository,
    energy: EnergyRepository,
) : ViewModel() {

    private data class Range(val period: Period, val offset: Int, val start: LocalDate, val end: LocalDate, val prevStart: LocalDate, val prevEnd: LocalDate)

    private val range = MutableStateFlow(computeRange(Period.WEEK, 0))

    private val _tab = MutableStateFlow(0)
    val tab: StateFlow<Int> = _tab.asStateFlow()

    private val _review = MutableStateFlow<ReviewState>(ReviewState.Idle)
    val review: StateFlow<ReviewState> = _review.asStateFlow()

    val state: StateFlow<AnalyticsUi> = range.flatMapLatest { r ->
        combine(
            combine(meals.observeRange(r.prevStart, r.end), exercises.observeRange(r.prevStart, r.end)) { m, e -> m to e },
            combine(profiles.observeProfile(), energy.observeTarget()) { p, t -> p to t },
            weights.observeAll(),
            projections.observe(),
            store.insightRules,
        ) { (list, exerciseList), (profile, target), w, projection, rules ->
            val burned = ExerciseRepository.dailyTotals(exerciseList)
            val today = LocalDate.now()
            val calc = PeriodStatsCalculator(rules)
            val effectiveEnd = if (r.end.isAfter(today)) today else r.end
            val stats = calc.compute(list, r.start, effectiveEnd, target?.calories, w, burned)
            val prev = calc.compute(list, r.prevStart, r.prevEnd, target?.calories, w, burned)
            AnalyticsUi(
                period = r.period,
                offset = r.offset,
                start = r.start,
                end = r.end,
                stats = stats,
                previous = prev,
                insights = if (store.appConfig.value.isEnabled("insights")) InsightEngine(rules).evaluate(stats, r.period) else emptyList(),
                profile = profile,
                target = target,
                projection = projection,
                loading = false,
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUi())

    init {
        viewModelScope.launch {
            state.collect { ui -> refreshCachedReview(ui) }
        }
    }

    private suspend fun refreshCachedReview(ui: AnalyticsUi) {
        val stats = ui.stats ?: return
        if (!reviews.available()) {
            _review.value = ReviewState.Unavailable
            return
        }
        if (_review.value is ReviewState.Loading) return
        val cached = reviews.cached(ui.period, stats)
        _review.value = if (cached == null) ReviewState.Idle else ReviewState.Ready(cached, cached.dataHash != reviews.dataHash(stats))
    }

    fun generateReview() {
        val ui = state.value
        val stats = ui.stats ?: return
        _review.value = ReviewState.Loading
        viewModelScope.launch {
            _review.value = try {
                ReviewState.Ready(reviews.generate(ui.period, stats, ui.previous, ui.target, ui.profile, ui.projection), outdated = false)
            } catch (e: GeminiError) {
                ReviewState.Error(e.message ?: "Analisa gagal")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ReviewState.Error("Analisa gagal: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun setTab(index: Int) {
        _tab.value = index
    }

    private fun computeRange(period: Period, offset: Int): Range {
        val today = LocalDate.now()
        return when (period) {
            Period.MONTH -> {
                val first = today.withDayOfMonth(1).plusMonths(offset.toLong())
                val prevFirst = first.minusMonths(1)
                Range(period, offset, first, first.with(TemporalAdjusters.lastDayOfMonth()), prevFirst, first.minusDays(1))
            }
            else -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
                Range(Period.WEEK, offset, monday, monday.plusDays(6), monday.minusWeeks(1), monday.minusDays(1))
            }
        }
    }

    fun setPeriod(period: Period) {
        _review.update { if (it is ReviewState.Loading) it else ReviewState.Idle }
        range.value = computeRange(period, 0)
    }

    fun shift(delta: Int) {
        val r = range.value
        _review.update { if (it is ReviewState.Loading) it else ReviewState.Idle }
        range.value = computeRange(r.period, (r.offset + delta).coerceAtMost(0))
    }
}
