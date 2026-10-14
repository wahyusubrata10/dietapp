package com.hawdietpro.app.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.data.remote.RemoteConfigStore
import com.hawdietpro.app.data.repository.MealRepository
import com.hawdietpro.app.data.repository.PendingRepository
import com.hawdietpro.app.data.repository.ProfileRepository
import com.hawdietpro.app.data.prefs.SecureStorage
import com.hawdietpro.app.domain.insight.Insight
import com.hawdietpro.app.domain.insight.InsightEngine
import com.hawdietpro.app.domain.insight.Period
import com.hawdietpro.app.domain.insight.PeriodStatsCalculator
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.model.sum
import com.hawdietpro.app.domain.nutrition.DailyTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class HomeState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val name: String = "",
    val target: DailyTarget? = null,
    val meals: List<Meal> = emptyList(),
    val total: Nutrients = Nutrients.ZERO,
    val byType: Map<MealType, List<Meal>> = emptyMap(),
    val insights: List<Insight> = emptyList(),
    val pendingCount: Int = 0,
    val tip: String? = null,
    val hasApiKey: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    meals: MealRepository,
    profiles: ProfileRepository,
    pending: PendingRepository,
    secure: SecureStorage,
    private val store: RemoteConfigStore,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())

    val state: StateFlow<HomeState> = combine(
        today.flatMapLatest { meals.observeDay(it) },
        profiles.observeProfile(),
        pending.observe(),
        secure.hasApiKey,
        store.insightRules,
    ) { list, profile, queue, hasKey, rules ->
        val date = today.value
        val target = profile?.let { com.hawdietpro.app.domain.nutrition.TdeeCalculator.calculate(it) }
        val stats = PeriodStatsCalculator(rules).compute(list, date, date, target?.calories)
        val tips = store.appConfig.value.tips
        HomeState(
            loading = false,
            date = date,
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** Dipanggil saat layar tampil agar "hari ini" selalu benar setelah lewat tengah malam. */
    fun refreshDate() {
        today.value = LocalDate.now()
    }
}
