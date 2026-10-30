package com.hawdietpro.app.ui.feature.weight

import com.hawdietpro.app.ui.components.StatRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.data.repository.EnergyRepository
import com.hawdietpro.app.data.repository.ProfileRepository
import com.hawdietpro.app.domain.nutrition.AdaptiveTdee
import com.hawdietpro.app.data.repository.WeightProjectionRepository
import com.hawdietpro.app.domain.nutrition.WeightProjection
import com.hawdietpro.app.data.repository.WeightRepository
import com.hawdietpro.app.domain.model.WeightEntry
import com.hawdietpro.app.domain.nutrition.TdeeCalculator
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.HawScaffold
import com.hawdietpro.app.ui.components.LineTrendChart
import com.hawdietpro.app.ui.components.StatTile
import com.hawdietpro.app.ui.feature.meal.NumberField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class WeightUi(
    val entries: List<WeightEntry> = emptyList(),
    val heightCm: Double? = null,
    val targetKg: Double? = null,
    val projection: WeightProjection? = null,
    val adaptive: AdaptiveTdee? = null,
    val adaptiveOn: Boolean = true,
    val targetCalories: Int? = null,
)

@HiltViewModel
class WeightViewModel @Inject constructor(
    private val weights: WeightRepository,
    profiles: ProfileRepository,
    projections: WeightProjectionRepository,
    private val energy: EnergyRepository,
) : ViewModel() {
    val state: StateFlow<WeightUi> = combine(
        weights.observeAll(),
        profiles.observeProfile(),
        projections.observe(),
        combine(energy.observeAdaptive(), energy.adaptiveEnabled, energy.observeTarget()) { a, on, t -> Triple(a, on, t) },
    ) { w, p, proj, (adaptive, on, target) ->
        WeightUi(w, p?.heightCm, p?.targetWeightKg, proj, adaptive, on, target?.calories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUi())

    fun setAdaptive(on: Boolean) {
        viewModelScope.launch { energy.setAdaptiveEnabled(on) }
    }

    fun log(kg: Double) {
        viewModelScope.launch { weights.log(LocalDate.now(), kg) }
    }
    fun delete(id: Long) {
        viewModelScope.launch { weights.delete(id) }
    }
}

@Composable
fun WeightScreen(onBack: () -> Unit, viewModel: WeightViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    HawScaffold(
        title = "Berat badan",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAdd = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Catat hari ini") })
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val latest = state.entries.lastOrNull()
            val first = state.entries.firstOrNull()
            item {
                StatRow {
                    StatTile("Terakhir", latest?.let { Format.kg(it.weightKg) } ?: "-", Modifier.weight(1f), color = MaterialTheme.colorScheme.primaryContainer)
                    StatTile(
                        "Perubahan",
                        if (latest != null && first != null && latest != first) {
                            val d = latest.weightKg - first.weightKg
                            (if (d > 0) "+" else "") + Format.oneDecimal(d) + " kg"
                        } else "-",
                        Modifier.weight(1f),
                    )
                    StatTile(
                        "BMI",
                        latest?.let { l -> state.heightCm?.let { Format.oneDecimal(TdeeCalculator.bmi(l.weightKg, it)) } } ?: "-",
                        Modifier.weight(1f),
                    )
                }
            }
            state.targetKg?.let { t ->
                item {
                    Text(
                        "Berat target: ${Format.kg(t)}" + (latest?.let { " · sisa ${Format.oneDecimal(kotlin.math.abs(it.weightKg - t))} kg" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.adaptive?.let { a ->
                item { AdaptiveSection(a, state.adaptiveOn, state.targetCalories, viewModel::setAdaptive) }
            }
            state.projection?.let { proj ->
                item { WeightProjectionSection(proj) }
            }
            if (state.entries.isNotEmpty()) {
                item { Text("Riwayat timbangan", style = MaterialTheme.typography.titleMedium) }
            }
            items(state.entries.reversed(), key = { it.id }) { e ->
                ListItem(
                    headlineContent = { Text(Format.kg(e.weightKg)) },
                    supportingContent = { Text(Format.relativeDay(LocalDate.ofEpochDay(e.epochDay))) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.delete(e.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Hapus") }
                    },
                )
            }
        }
    }

    if (showAdd) {
        var value by remember { mutableStateOf(state.entries.lastOrNull()?.weightKg?.let(Format::oneDecimal)?.replace(',', '.') ?: "") }
        val kg = value.replace(',', '.').toDoubleOrNull()
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Berat hari ini") },
            text = { NumberField(value, { value = it }, "Berat (kg)") },
            confirmButton = {
                TextButton(
                    onClick = {
                        kg?.let(viewModel::log)
                        showAdd = false
                    },
                    enabled = kg != null && kg in 25.0..300.0,
                ) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Batal") } },
        )
    }
}
