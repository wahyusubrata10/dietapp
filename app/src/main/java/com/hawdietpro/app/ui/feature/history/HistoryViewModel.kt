package com.hawdietpro.app.ui.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.data.repository.ExerciseRepository
import com.hawdietpro.app.data.repository.MealRepository
import com.hawdietpro.app.data.repository.ProfileRepository
import com.hawdietpro.app.domain.model.Grade
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

enum class HistoryMode { TIMELINE, CALENDAR }

data class DayGroup(val date: LocalDate, val meals: List<Meal>) {
    val calories: Double get() = meals.sumOf { it.total.calories }
}

data class HistoryFilters(
    val query: String = "",
    val types: Set<MealType> = emptySet(),
    val grades: Set<Grade> = emptySet(),
) {
    val active get() = query.isNotBlank() || types.isNotEmpty() || grades.isNotEmpty()

    fun apply(list: List<Meal>) = list.filter { m ->
        (types.isEmpty() || m.mealType in types) && (grades.isEmpty() || m.grade in grades)
    }
}

data class HistoryUi(
    val mode: HistoryMode = HistoryMode.TIMELINE,
    val filters: HistoryFilters = HistoryFilters(),
    val groups: List<DayGroup> = emptyList(),
    val windowDays: Long = 90,
    val month: YearMonth = YearMonth.now(),
    val dayCalories: Map<LocalDate, Double> = emptyMap(),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedMeals: List<Meal> = emptyList(),
    val targetCalories: Int? = null,
    /** Kalori olahraga per hari: jatah hari itu = target + olahraga (sama seperti Beranda). */
    val exerciseByDay: Map<LocalDate, Double> = emptyMap(),
    val loading: Boolean = true,
) {
    fun budgetFor(date: LocalDate): Int? = targetCalories?.let { it + (exerciseByDay[date] ?: 0.0).toInt() }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val meals: MealRepository,
    profiles: ProfileRepository,
    exercises: ExerciseRepository,
) : ViewModel() {

    private val mode = MutableStateFlow(HistoryMode.TIMELINE)
    private val filters = MutableStateFlow(HistoryFilters())
    private val windowDays = MutableStateFlow(90L)
    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow(LocalDate.now())

    private val zone get() = ZoneId.systemDefault()

    private val timeline = combine(filters.debounce(250), windowDays) { f, w -> f to w }
        .flatMapLatest { (f, w) ->
            val source = if (f.query.isNotBlank()) meals.search(f.query) else {
                val today = LocalDate.now()
                meals.observeRange(today.minusDays(w - 1), today)
            }
            source.map { list ->
                f.apply(list)
                    .groupBy { Instant.ofEpochMilli(it.eatenAt).atZone(zone).toLocalDate() }
                    .toSortedMap(compareByDescending<LocalDate> { it })
                    .map { (d, l) -> DayGroup(d, l.sortedBy { it.eatenAt }) }
            }
        }

    private val monthData = month.flatMapLatest { m -> meals.observeRange(m.atDay(1), m.atEndOfMonth()) }

    val state: StateFlow<HistoryUi> = combine(
        combine(mode, filters, windowDays, month, selected) { a, b, c, d, e -> Params(a, b, c, d, e) },
        timeline,
        monthData,
        profiles.observeTarget(),
        exercises.observeDailyTotals(),
    ) { p, groups, monthMeals, target, burned ->
        val filteredMonth = p.filters.apply(monthMeals)
        val byDay = filteredMonth.groupBy { Instant.ofEpochMilli(it.eatenAt).atZone(zone).toLocalDate() }
        HistoryUi(
            mode = p.mode,
            filters = p.filters,
            groups = groups,
            windowDays = p.window,
            month = p.month,
            dayCalories = byDay.mapValues { (_, l) -> l.sumOf { it.total.calories } },
            selectedDate = p.selected,
            selectedMeals = byDay[p.selected].orEmpty().sortedBy { it.eatenAt },
            targetCalories = target?.calories,
            exerciseByDay = burned,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUi())

    private data class Params(val mode: HistoryMode, val filters: HistoryFilters, val window: Long, val month: YearMonth, val selected: LocalDate)

    fun setMode(m: HistoryMode) { mode.value = m }
    fun setQuery(q: String) = filters.update { it.copy(query = q) }
    fun toggleType(t: MealType) = filters.update { it.copy(types = if (t in it.types) it.types - t else it.types + t) }
    fun toggleGrade(g: Grade) = filters.update { it.copy(grades = if (g in it.grades) it.grades - g else it.grades + g) }
    fun clearFilters() { filters.value = HistoryFilters() }
    fun loadMore() = windowDays.update { it + 90 }
    fun setMonth(m: YearMonth) { month.value = m }
    fun select(date: LocalDate) {
        selected.value = date
        if (YearMonth.from(date) != month.value) month.value = YearMonth.from(date)
    }
}
