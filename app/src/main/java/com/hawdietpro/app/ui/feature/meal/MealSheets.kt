package com.hawdietpro.app.ui.feature.meal

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.FoodReference
import com.hawdietpro.app.domain.model.NutritionSource
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.domain.nutrition.NutritionCalculator
import com.hawdietpro.app.ui.components.MacroInline
import com.hawdietpro.app.ui.components.SourceBadge
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemSheet(
    item: FoodItem,
    viewModel: MealEditorViewModel,
    onDismiss: () -> Unit,
    onSave: (name: String, grams: Double) -> Unit,
    onDelete: () -> Unit,
    onLinkDb: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(item) { mutableStateOf(item.name) }
    var grams by remember(item) { mutableFloatStateOf(item.grams.toFloat()) }
    val maxGrams = remember(item) { max(500f, (item.grams * 2.5).toFloat()).let { ((it / 50f).roundToInt() * 50).toFloat() } }
    val ref by produceState<FoodReference?>(null, item) { value = viewModel.referenceFor(item) }

    val preview = NutritionCalculator.rescale(item, grams.toDouble(), ref.takeIf { item.source == NutritionSource.DB })

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Edit elemen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                SourceBadge(item.source)
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Nama yang cocok dengan DB lokal akan memakai angka gizi DB") },
            )

            Row(verticalAlignment = Alignment.Bottom) {
                Text("${grams.roundToInt()}", style = MaterialTheme.typography.displaySmall)
                Text(" gram", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.kcal(preview.nutrients.calories), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    MacroInline(preview.nutrients)
                }
            }
            Slider(
                value = grams.coerceAtMost(maxGrams),
                onValueChange = { grams = (it / 5f).roundToInt() * 5f },
                valueRange = 0f..maxGrams,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("½×" to 0.5f, "−10" to -10f, "+10" to 10f, "2×" to 2f).forEach { (label, v) ->
                    OutlinedButton(
                        onClick = {
                            grams = when (label) {
                                "½×", "2×" -> grams * v
                                else -> (grams + v).coerceAtLeast(0f)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(label) }
                }
            }

            val sourceInfo = when {
                item.source == NutritionSource.DB && ref != null ->
                    "Sumber angka: DB lokal (${ref!!.source}) · ${Format.kcalNumber(ref!!.per100g.calories)} kkal per 100 g" +
                        (item.aiNutrients?.let { " · estimasi AI awal ${Format.kcal(it.calories)}" } ?: "")
                item.source == NutritionSource.AI -> "Sumber angka: estimasi AI (tidak ditemukan padanan di DB lokal)"
                else -> "Sumber angka: input manual"
            }
            Text(sourceInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Hapus")
                }
                TextButton(onClick = onLinkDb) {
                    Icon(Icons.Filled.Link, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Cocokkan DB")
                }
            }
            Button(
                onClick = { onSave(name, grams.toDouble()) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.large,
            ) { Text("Terapkan") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodSheet(
    results: List<FoodReference>,
    onSearch: (String) -> Unit,
    onPick: (FoodReference, Double) -> Unit,
    onCustom: (name: String, grams: Double, nutrients: Nutrients, saveToDb: Boolean) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Tambah elemen",
    allowCustom: Boolean = true,
    initialQuery: String = "",
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf(initialQuery) }
    var selected by remember { mutableStateOf<FoodReference?>(null) }
    var custom by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        delay(200)
        onSearch(query)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            val sel = selected
            when {
                sel != null -> PortionPicker(sel, onBack = { selected = null }, onConfirm = { g -> onPick(sel, g) })
                custom -> CustomFoodForm(initialName = query, onBack = { custom = false }, onConfirm = onCustom)
                else -> {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        placeholder = { Text("Cari: nasi, rendang, tempe, soto…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(results, key = { it.id }) { ref ->
                            Row(
                                Modifier.fillMaxWidth().clickable { selected = ref }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(ref.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${ref.servingLabel ?: "1 porsi"} (${Format.gramsInt(ref.defaultServingG)}) · ${Format.kcal(ref.per100g.calories * ref.defaultServingG / 100)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    "${Format.kcalNumber(ref.per100g.calories)}/100g",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                    if (query.isNotBlank() && results.isEmpty()) {
                        Text("Tidak ditemukan di DB lokal.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (allowCustom) {
                        FilledTonalButton(onClick = { custom = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Tambah makanan sendiri (isi kalori manual)")
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PortionPicker(ref: FoodReference, onBack: () -> Unit, onConfirm: (Double) -> Unit) {
    var grams by remember(ref) { mutableFloatStateOf(ref.defaultServingG.toFloat()) }
    val maxG = max(500f, ref.defaultServingG.toFloat() * 3)
    val n = NutritionCalculator.fromReference(ref, grams.toDouble())
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(ref.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "Porsi umum: ${ref.servingLabel ?: "1 porsi"} ≈ ${Format.gramsInt(ref.defaultServingG)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${grams.roundToInt()} g", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            Text(Format.kcal(n.calories), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        MacroInline(n)
        Slider(value = grams.coerceAtMost(maxG), onValueChange = { grams = (it / 5f).roundToInt() * 5f }, valueRange = 0f..maxG)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.5, 1.0, 1.5, 2.0).forEach { f ->
                OutlinedButton(onClick = { grams = (ref.defaultServingG * f).toFloat() }, modifier = Modifier.weight(1f)) {
                    Text(if (f == 0.5) "½" else Format.oneDecimal(f) + "×")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Kembali") }
            Button(onClick = { onConfirm(grams.toDouble()) }, enabled = grams > 0, modifier = Modifier.weight(1f)) { Text("Tambahkan") }
        }
    }
}

@Composable
private fun CustomFoodForm(
    initialName: String,
    onBack: () -> Unit,
    onConfirm: (String, Double, Nutrients, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var grams by remember { mutableStateOf("100") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var save by remember { mutableStateOf(true) }

    fun num(s: String) = s.replace(',', '.').toDoubleOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Nama makanan") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(grams, { grams = it }, "Berat (g)", Modifier.weight(1f))
            NumberField(kcal, { kcal = it }, "Kalori (kkal)", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(protein, { protein = it }, "Protein g", Modifier.weight(1f))
            NumberField(carbs, { carbs = it }, "Karbo g", Modifier.weight(1f))
            NumberField(fat, { fat = it }, "Lemak g", Modifier.weight(1f))
        }
        Text(
            "Isi nilai untuk porsi yang Anda makan. Lihat kemasan / label gizi bila ada.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { save = !save }) {
            Checkbox(checked = save, onCheckedChange = { save = it })
            Text("Simpan ke DB lokal untuk dipakai lagi")
        }
        val valid = name.isNotBlank() && (num(grams) ?: 0.0) > 0 && num(kcal) != null
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Kembali") }
            Button(
                onClick = {
                    onConfirm(
                        name,
                        num(grams) ?: 100.0,
                        Nutrients(num(kcal) ?: 0.0, num(protein) ?: 0.0, num(carbs) ?: 0.0, num(fat) ?: 0.0, 0.0),
                        save,
                    )
                },
                enabled = valid,
                modifier = Modifier.weight(1f),
            ) { Text("Tambahkan") }
        }
    }
}

@Composable
fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
