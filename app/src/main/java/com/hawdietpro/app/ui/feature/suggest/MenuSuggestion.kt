package com.hawdietpro.app.ui.feature.suggest

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.data.gemini.GeminiClient
import com.hawdietpro.app.data.gemini.GeminiError
import com.hawdietpro.app.data.gemini.GeminiSuggestion
import com.hawdietpro.app.data.repository.MealRepository
import com.hawdietpro.app.data.repository.ProfileRepository
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.sum
import com.hawdietpro.app.domain.nutrition.NutritionLimits
import com.hawdietpro.app.domain.nutrition.TdeeCalculator
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.Pill
import com.hawdietpro.app.ui.theme.HawTheme
import com.hawdietpro.app.ui.theme.MacroColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlin.math.roundToInt

private val PLACES = listOf("Warteg / rumah makan", "Nasi Padang", "Masak sendiri", "Kantin kantor", "Ojek online", "Minimarket", "Jajanan / kaki lima")

data class SuggestUi(
    val mealType: MealType = MealType.fromHour(LocalTime.now().hour),
    val place: String? = null,
    val note: String = "",
    val loading: Boolean = false,
    val result: GeminiSuggestion? = null,
    val error: String? = null,
    val remainingKcal: Int? = null,
)

@HiltViewModel
class MenuSuggestionViewModel @Inject constructor(
    private val gemini: GeminiClient,
    private val meals: MealRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SuggestUi())
    val state: StateFlow<SuggestUi> = _state.asStateFlow()

    val available: Boolean get() = gemini.suggestionAvailable()

    init {
        viewModelScope.launch {
            val target = profiles.getTarget()
            val today = meals.observeDay(LocalDate.now()).first()
            val total = today.map { it.total }.sum()
            _state.update { it.copy(remainingKcal = target?.let { t -> t.calories - total.calories.roundToInt() }) }
        }
    }

    fun setPlace(p: String?) = _state.update { it.copy(place = p) }
    fun setNote(n: String) = _state.update { it.copy(note = n) }
    fun setMealType(t: MealType) = _state.update { it.copy(mealType = t) }

    fun request() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val profile = profiles.getProfile()
                val target = profile?.let(TdeeCalculator::calculate)
                val today = meals.observeDay(LocalDate.now()).first()
                val total = today.map { it.total }.sum()
                val favorites = meals.observeFavorites().first().take(8)
                val s = _state.value
                val context = buildJsonObject {
                    profile?.let {
                        put("tujuan", it.goal.label)
                        put("jenis_kelamin", it.sex.label)
                        put("umur", it.age)
                    }
                    target?.let { t ->
                        put("target_kalori", t.calories)
                        put("sisa_kalori", t.calories - total.calories.roundToInt())
                        put("target_protein_g", t.proteinG)
                        put("sisa_protein_g", (t.proteinG - total.protein).roundToInt())
                        put("target_serat_g", t.fiberG)
                        put("sisa_serat_g", (t.fiberG - total.fiber).roundToInt())
                    }
                    put("gula_hari_ini_g", total.sugar.roundToInt())
                    put("batas_gula_g", NutritionLimits.SUGAR_G.roundToInt())
                    put("natrium_hari_ini_mg", total.sodium.roundToInt())
                    put("batas_natrium_mg", NutritionLimits.SODIUM_MG.roundToInt())
                    put("jam_sekarang", LocalTime.now().toString().take(5))
                    put("waktu_makan_berikutnya", s.mealType.label)
                    s.place?.let { put("tempat_makan", it) }
                    s.note.takeIf { it.isNotBlank() }?.let { put("catatan_pengguna", it) }
                    putJsonArray("sudah_dimakan_hari_ini") {
                        today.forEach { m -> add("${m.mealType.label}: ${m.menuName} (${m.total.calories.roundToInt()} kkal)") }
                    }
                    putJsonArray("menu_favorit") { favorites.forEach { add(it.menuName) } }
                }
                val result = gemini.generateSuggestion(context.toString(), s.mealType.label)
                _state.update { it.copy(loading = false, result = result) }
            } catch (e: GeminiError) {
                _state.update { it.copy(loading = false, error = e.message) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Gagal meminta saran: ${e.message}") }
            }
        }
    }
}

/** Lembar "Saran menu" dari sisa kalori hari ini. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSuggestionSheet(onDismiss: () -> Unit, viewModel: MenuSuggestionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = HawTheme.colors.card) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text("Saran menu", style = MaterialTheme.typography.titleLarge)
            }
            state.remainingKcal?.let { rem ->
                Text(
                    if (rem > 0) "Sisa kalori hari ini: ${Format.grouped(rem)} kkal" else "Target hari ini sudah terpenuhi (${Format.grouped(-rem)} kkal di atas target)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Untuk", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealType.entries.forEach { t ->
                    FilterChip(selected = state.mealType == t, onClick = { viewModel.setMealType(t) }, label = { Text("${t.emoji} ${t.label}") })
                }
            }
            Text("Makan di mana?", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PLACES.forEach { p ->
                    FilterChip(selected = state.place == p, onClick = { viewModel.setPlace(if (state.place == p) null else p) }, label = { Text(p) })
                }
            }
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text("Catatan (opsional)") },
                placeholder = { Text("mis. budget 25rb, tidak suka pedas, lagi pengen yang berkuah") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )
            Button(
                onClick = viewModel::request,
                enabled = !state.loading && viewModel.available,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(if (state.result == null) "Minta saran" else "Minta saran lain")
            }
            if (!viewModel.available) {
                Text("Fitur ini menunggu sinkron data terbaru. Coba lagi nanti.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            state.result?.let { r ->
                if (r.intro.isNotBlank()) Text(r.intro, style = MaterialTheme.typography.bodyLarge)
                r.options.forEachIndexed { i, o ->
                    HawCard(contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}. ${o.name}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Pill("${o.calories.roundToInt()} kkal", MaterialTheme.colorScheme.primary)
                            }
                            Pill("Protein ±${o.protein.roundToInt()} g", MacroColors.Protein)
                            Text(o.reason, style = MaterialTheme.typography.bodyMedium)
                            if (o.tips.isNotBlank()) {
                                Text("💡 ${o.tips}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (r.avoid.isNotEmpty()) {
                    Text("Sebaiknya dihindari", style = MaterialTheme.typography.titleSmall)
                    r.avoid.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                }
                Text(
                    "Setelah makan, foto saja makanannya agar tercatat akurat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Tutup") }
            Spacer(Modifier.height(8.dp))
        }
    }
}
