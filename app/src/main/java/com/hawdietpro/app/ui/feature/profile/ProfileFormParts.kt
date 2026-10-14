package com.hawdietpro.app.ui.feature.profile

import com.hawdietpro.app.ui.components.StatRow
import com.hawdietpro.app.ui.components.hawSegmentedColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.domain.model.ActivityLevel
import com.hawdietpro.app.domain.model.Goal
import com.hawdietpro.app.domain.model.Sex
import com.hawdietpro.app.domain.nutrition.DailyTarget
import com.hawdietpro.app.ui.components.Banner
import com.hawdietpro.app.ui.components.StatTile
import com.hawdietpro.app.ui.theme.MacroColors

@Composable
fun FormNumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    suffix: String,
    error: String?,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(6)) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { msg -> @Composable { Text(msg) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun PersonalFields(form: ProfileForm, onChange: ((ProfileForm) -> ProfileForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { v -> onChange { it.copy(name = v.take(40)) } },
            label = { Text("Nama panggilan") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Jenis kelamin", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Sex.entries.forEachIndexed { i, sex ->
                SegmentedButton(
                    colors = hawSegmentedColors(),
                    selected = form.sex == sex,
                    onClick = { onChange { it.copy(sex = sex) } },
                    shape = SegmentedButtonDefaults.itemShape(i, Sex.entries.size),
                ) { Text(sex.label) }
            }
        }
        FormNumberField(form.age, { v -> onChange { it.copy(age = v) } }, "Umur", "tahun", form.ageError, decimal = false)
    }
}

@Composable
fun BodyFields(form: ProfileForm, onChange: ((ProfileForm) -> ProfileForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FormNumberField(form.height, { v -> onChange { it.copy(height = v) } }, "Tinggi badan", "cm", form.heightError)
        FormNumberField(form.weight, { v -> onChange { it.copy(weight = v) } }, "Berat badan saat ini", "kg", form.weightError)
        FormNumberField(
            form.targetWeight,
            { v -> onChange { it.copy(targetWeight = v) } },
            "Berat target (opsional)",
            "kg",
            form.targetWeightError,
            supporting = "Untuk memperkirakan berapa minggu menuju target",
        )
    }
}

@Composable
fun <T> ChoiceCard(selected: Boolean, title: String, subtitle: String?, value: T, onSelect: (T) -> Unit) {
    Surface(
        onClick = { onSelect(value) },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = { onSelect(value) })
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
fun ActivityPicker(form: ProfileForm, onChange: ((ProfileForm) -> ProfileForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ActivityLevel.entries.forEach { level ->
            ChoiceCard(form.activity == level, level.label, level.description, level) { v -> onChange { it.copy(activity = v) } }
        }
    }
}

@Composable
fun GoalPicker(form: ProfileForm, onChange: ((ProfileForm) -> ProfileForm) -> Unit) {
    val desc = mapOf(
        Goal.TURUN to "Defisit aman maks 500 kkal/hari (±0,5 kg/minggu)",
        Goal.JAGA to "Kalori sesuai kebutuhan harian (TDEE)",
        Goal.NAIK to "Surplus ringan maks 300 kkal/hari",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Goal.entries.forEach { goal ->
            ChoiceCard(form.goal == goal, goal.label, desc[goal], goal) { v -> onChange { it.copy(goal = v) } }
        }
    }
}

@Composable
fun TargetSummary(target: DailyTarget, form: ProfileForm, onChange: ((ProfileForm) -> ProfileForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Target kalori harian", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(Format.grouped(target.calories), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    when {
                        target.adjustment < -20 -> "Defisit ${-target.adjustment} kkal dari TDEE (≈ ${Format.oneDecimal(-target.weeklyChangeKg)} kg/minggu)"
                        target.adjustment > 20 -> "Surplus ${target.adjustment} kkal dari TDEE (≈ +${Format.oneDecimal(target.weeklyChangeKg)} kg/minggu)"
                        else -> "Sesuai kebutuhan harian"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                target.weeksToTarget?.let {
                    Text("Perkiraan mencapai berat target: ±$it minggu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        StatRow {
            StatTile("BMR", "${Format.grouped(target.bmr)}", Modifier.weight(1f), sub = "kkal istirahat")
            StatTile("TDEE", "${Format.grouped(target.tdee)}", Modifier.weight(1f), sub = "kkal/hari")
            StatTile("BMI", Format.oneDecimal(target.bmi), Modifier.weight(1f), sub = target.bmiCategory)
        }
        StatRow {
            StatTile("Protein", "${target.proteinG} g", Modifier.weight(1f), color = MacroColors.Protein.copy(alpha = 0.15f))
            StatTile("Karbo", "${target.carbsG} g", Modifier.weight(1f), color = MacroColors.Carbs.copy(alpha = 0.15f))
            StatTile("Lemak", "${target.fatG} g", Modifier.weight(1f), color = MacroColors.Fat.copy(alpha = 0.15f))
        }
        target.warnings.forEach {
            Banner(
                text = it,
                icon = Icons.Filled.WarningAmber,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        FormNumberField(
            form.customCalories,
            { v -> onChange { it.copy(customCalories = v) } },
            "Target kalori manual (opsional)",
            "kkal",
            null,
            decimal = false,
            supporting = "Kosongkan untuk memakai hitungan otomatis. Tetap dibatasi batas aman.",
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Rumus: BMR Mifflin-St Jeor × faktor aktivitas. Target bukan saran medis; konsultasikan dengan tenaga kesehatan bila punya kondisi khusus.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
