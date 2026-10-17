package com.hawdietpro.app.ui.feature.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.data.repository.ExerciseRepository
import com.hawdietpro.app.data.repository.ProfileRepository
import com.hawdietpro.app.domain.exercise.Activity
import com.hawdietpro.app.domain.exercise.ExerciseCatalog
import com.hawdietpro.app.domain.model.ActivityLevel
import com.hawdietpro.app.domain.model.Exercise
import com.hawdietpro.app.ui.components.Banner
import com.hawdietpro.app.ui.theme.HawTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

private val DURATIONS = listOf(15, 30, 45, 60, 90)

data class ExerciseForm(
    val date: LocalDate = LocalDate.now(),
    val editing: Exercise? = null,
    val activityId: String? = null,
    /** Nama bebas bila tidak memilih dari daftar. */
    val customName: String = "",
    val minutes: String = "30",
    val manual: Boolean = false,
    val manualKcal: String = "",
    val weightKg: Double = 65.0,
    val activityLevel: ActivityLevel = ActivityLevel.SEDENTARI,
) {
    val activity: Activity? get() = ExerciseCatalog.find(activityId)
    val minutesValue: Int? get() = minutes.trim().toIntOrNull()?.takeIf { it in 1..600 }
    val estimated: Double? get() {
        val a = activity ?: return null
        val m = minutesValue ?: return null
        return ExerciseCatalog.estimate(a.met, weightKg, m)
    }
    val calories: Double? get() = if (manual) manualKcal.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 1.0..3000.0 } else estimated
    val name: String get() = activity?.name ?: customName.trim()
    val canSave: Boolean get() = name.isNotBlank() && minutesValue != null && calories != null
}

@HiltViewModel
class ExerciseSheetViewModel @Inject constructor(
    private val repo: ExerciseRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(ExerciseForm())
    val form: StateFlow<ExerciseForm> = _form.asStateFlow()

    fun start(date: LocalDate, editing: Exercise?) {
        viewModelScope.launch {
            val profile = profiles.getProfile()
            _form.value = ExerciseForm(
                date = date,
                editing = editing,
                activityId = editing?.activityId,
                customName = if (editing?.activityId == null) editing?.name.orEmpty() else "",
                minutes = editing?.durationMin?.toString() ?: "30",
                manual = editing?.source == Exercise.SOURCE_MANUAL,
                manualKcal = editing?.takeIf { it.source == Exercise.SOURCE_MANUAL }?.calories?.roundToInt()?.toString().orEmpty(),
                weightKg = profile?.weightKg ?: 65.0,
                activityLevel = profile?.activityLevel ?: ActivityLevel.SEDENTARI,
            )
        }
    }

    fun select(id: String?) = _form.update {
        // Tanpa aktivitas dari daftar, kalori harus diisi sendiri.
        it.copy(activityId = id, manual = if (id == null) true else it.manual)
    }
    fun setCustomName(v: String) = _form.update { it.copy(customName = v) }
    fun setMinutes(v: String) = _form.update { it.copy(minutes = v.filter(Char::isDigit).take(3)) }
    fun setManual(v: Boolean) = _form.update { it.copy(manual = v || it.activity == null) }
    fun setManualKcal(v: String) = _form.update { it.copy(manualKcal = v.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6)) }

    fun save(onDone: (String) -> Unit) {
        val f = _form.value
        val kcal = f.calories ?: return
        val minutes = f.minutesValue ?: return
        val zone = ZoneId.systemDefault()
        val time = f.editing?.performedAt ?: run {
            val t = if (f.date == LocalDate.now()) LocalTime.now() else LocalTime.of(17, 0)
            f.date.atTime(t).atZone(zone).toInstant().toEpochMilli()
        }
        viewModelScope.launch {
            repo.save(
                Exercise(
                    id = f.editing?.id ?: 0,
                    epochDay = f.date.toEpochDay(),
                    performedAt = time,
                    activityId = f.activityId,
                    name = f.name,
                    durationMin = minutes,
                    calories = kcal,
                    source = if (f.manual) Exercise.SOURCE_MANUAL else Exercise.SOURCE_ESTIMATE,
                ),
            )
            onDone("${f.name} dicatat: ${Format.grouped(kcal.roundToInt())} kkal terbakar")
        }
    }

    fun delete(onDone: (String) -> Unit) {
        val e = _form.value.editing ?: return
        viewModelScope.launch {
            repo.delete(e.id)
            onDone("${e.name} dihapus")
        }
    }
}

/** Lembar untuk mencatat/mengubah olahraga pada [date]. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseSheet(
    date: LocalDate,
    editing: Exercise? = null,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit = {},
    viewModel: ExerciseSheetViewModel = hiltViewModel(),
) {
    LaunchedEffect(date, editing?.id) { viewModel.start(date, editing) }
    val f by viewModel.form.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val done: (String) -> Unit = { msg -> onMessage(msg); onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = HawTheme.colors.card) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 760.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (editing == null) "Catat olahraga" else "Ubah olahraga", style = MaterialTheme.typography.titleLarge)
            Text(
                if (date == LocalDate.now()) "Hari ini" else Format.dayMonth(date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (f.activityLevel.factor >= ActivityLevel.SEDANG.factor) {
                Banner(
                    title = "Hindari hitung dobel",
                    text = "Tingkat aktivitas di profil Anda \"${f.activityLevel.label}\", artinya olahraga rutin sudah masuk kebutuhan harian. " +
                        "Catat hanya olahraga tambahan, atau ubah aktivitas di profil ke \"Jarang bergerak\"/\"Ringan\" bila semua olahraga dicatat di sini.",
                )
            }

            Text("Aktivitas", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ExerciseCatalog.activities.forEach { a ->
                    FilterChip(selected = f.activityId == a.id, onClick = { viewModel.select(a.id) }, label = { Text("${a.emoji} ${a.name}") })
                }
                FilterChip(selected = f.activityId == null, onClick = { viewModel.select(null) }, label = { Text("✏️ Lainnya") })
            }
            if (f.activityId == null) {
                OutlinedTextField(
                    value = f.customName,
                    onValueChange = viewModel::setCustomName,
                    label = { Text("Nama aktivitas") },
                    placeholder = { Text("mis. Muay thai, panjat tebing") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text("Durasi", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = f.minutes,
                    onValueChange = viewModel::setMinutes,
                    label = { Text("Menit") },
                    singleLine = true,
                    isError = f.minutes.isNotBlank() && f.minutesValue == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(110.dp),
                )
                Spacer(Modifier.width(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DURATIONS.forEach { m ->
                        FilterChip(selected = f.minutesValue == m, onClick = { viewModel.setMinutes(m.toString()) }, label = { Text("$m") })
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Isi kalori sendiri", style = MaterialTheme.typography.titleSmall)
                    Text("mis. dari jam pintar / alat gym", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = f.manual, onCheckedChange = viewModel::setManual, enabled = f.activity != null)
            }
            if (f.manual) {
                OutlinedTextField(
                    value = f.manualKcal,
                    onValueChange = viewModel::setManualKcal,
                    label = { Text("Kalori aktif terbakar (kkal)") },
                    supportingText = { Text("Pakai angka kalori AKTIF, bukan total harian dari jam pintar.") },
                    singleLine = true,
                    isError = f.manualKcal.isNotBlank() && f.calories == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val kcal = f.calories
            Column {
                Text(
                    kcal?.let { "🔥 ${Format.grouped(it.roundToInt())} kkal terbakar" } ?: "🔥 — kkal",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                val a = f.activity
                if (!f.manual && a != null) {
                    Text(
                        "Perkiraan: (MET ${Format.oneDecimal(a.met)} − 1) × ${Format.oneDecimal(f.weightKg)} kg × ${f.minutesValue ?: 0} menit. " +
                            "Dikurangi 1 MET karena energi istirahat sudah termasuk di kebutuhan harian.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Kalori ini menambah jatah makan hari itu dan ikut dihitung di perkiraan berat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(onClick = { viewModel.save(done) }, enabled = f.canSave, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (editing == null) "Simpan" else "Simpan perubahan")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (editing != null) {
                    TextButton(onClick = { viewModel.delete(done) }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
