package com.hawdietpro.app.widget

import android.content.Context
import com.hawdietpro.app.data.repository.EnergyRepository
import com.hawdietpro.app.data.repository.ExerciseRepository
import com.hawdietpro.app.data.repository.MealRepository
import com.hawdietpro.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Memperbarui widget setiap kali catatan hari ini / target berubah (selama proses aplikasi hidup). */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val meals: MealRepository,
    private val exercises: ExerciseRepository,
    private val energy: EnergyRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val today = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start() {
        scope.launch {
            today.flatMapLatest { d ->
                combine(meals.observeDay(d), exercises.observeDay(d), energy.observeTarget()) { m, e, t ->
                    Triple(m.sumOf { it.total.calories }.roundToInt(), e.sumOf { it.calories }.roundToInt(), t?.calories)
                }
            }
                .distinctUntilChanged()
                .debounce(800)
                .collect { HawWidget.refresh(context) }
        }
        // Ganti hari tanpa membuka aplikasi: cek tiap menit selama proses hidup (sisanya ditangani updatePeriodMillis).
        scope.launch {
            while (true) {
                delay(60_000)
                val now = LocalDate.now()
                if (now != today.value) today.value = now
            }
        }
    }
}
