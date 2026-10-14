package com.hawdietpro.app.ui.feature.meal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.hawdietpro.app.domain.model.NutritionSource
import com.hawdietpro.app.ui.components.Pill
import com.hawdietpro.app.ui.components.SectionHeader
import com.hawdietpro.app.ui.components.SegmentedMacroBar
import com.hawdietpro.app.ui.components.SoftProgress
import com.hawdietpro.app.domain.nutrition.NutritionLimits
import com.hawdietpro.app.ui.theme.HawTheme
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.domain.model.FoodItem
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.ui.components.GradeBadge
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.HawScaffold
import com.hawdietpro.app.ui.components.LoadingBox
import com.hawdietpro.app.ui.components.MacroInline
import com.hawdietpro.app.ui.components.SourceBadge
import com.hawdietpro.app.ui.feature.camera.MealTypeChips
import com.hawdietpro.app.ui.theme.MacroColors
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt

@Composable
fun MealEditorScreen(
    onClose: (message: String?) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MealEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finishedMessage) {
        state.finishedMessage?.let { onClose(it) }
    }

    var confirmDelete by remember { mutableStateOf(false) }
    var editIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var linkIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    val title = when (state.mode) {
        EditorMode.PHOTO -> "Hasil Analisa"
        EditorMode.EDIT -> "Detail Makan"
        EditorMode.MANUAL -> "Input Manual"
    }

    HawScaffold(
        title = title,
        onBack = { onClose(null) },
        actions = {
            if (state.mode == EditorMode.EDIT) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Hapus") }
            }
        },
        bottomBar = {
            if (state.phase == EditorPhase.Ready || state.phase == EditorPhase.Saving) {
                SaveBar(state, onSave = viewModel::save)
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val phase = state.phase) {
                EditorPhase.Loading -> LoadingBox()
                EditorPhase.Analyzing -> AnalyzingView(
                    photoPath = state.meal.photoPath,
                    onBackground = if (state.mode == EditorMode.PHOTO) viewModel::continueInBackground else null,
                )
                is EditorPhase.Error -> ErrorView(
                    photoPath = state.meal.photoPath,
                    phase = phase,
                    hint = state.hint,
                    onHintChange = viewModel::setHint,
                    onRetry = viewModel::analyze,
                    onQueue = viewModel::queueForLater,
                    onManual = viewModel::continueManually,
                    onOpenSettings = onOpenSettings,
                )
                EditorPhase.Ready, EditorPhase.Saving -> EditorContent(
                    state = state,
                    viewModel = viewModel,
                    onEditItem = { editIndex = it },
                    onAdd = { showAdd = true },
                )
            }
        }
    }

    editIndex?.let { index ->
        val item = state.meal.items.getOrNull(index)
        if (item == null) {
            editIndex = null
        } else {
            EditItemSheet(
                item = item,
                viewModel = viewModel,
                onDismiss = { editIndex = null },
                onSave = { name, grams ->
                    viewModel.updateItem(index, name, grams)
                    editIndex = null
                },
                onDelete = {
                    viewModel.removeItem(index)
                    editIndex = null
                },
                onLinkDb = {
                    editIndex = null
                    linkIndex = index
                },
            )
        }
    }

    if (showAdd) {
        AddFoodSheet(
            results = state.searchResults,
            onSearch = viewModel::search,
            onPick = { ref, grams ->
                viewModel.addReference(ref, grams)
                showAdd = false
            },
            onCustom = { name, grams, nutrients, save ->
                viewModel.addCustom(name, grams, nutrients, save)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }

    linkIndex?.let { index ->
        AddFoodSheet(
            title = "Cocokkan dengan DB lokal",
            results = state.searchResults,
            onSearch = viewModel::search,
            allowCustom = false,
            initialQuery = state.meal.items.getOrNull(index)?.name.orEmpty(),
            onPick = { ref, _ ->
                viewModel.linkReference(index, ref)
                linkIndex = null
            },
            onCustom = { _, _, _, _ -> },
            onDismiss = { linkIndex = null },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus catatan?") },
            text = { Text("Catatan makan dan fotonya akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun SaveBar(state: EditorState, onSave: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Format.kcal(state.meal.total.calories), style = MaterialTheme.typography.titleLarge)
            }
            Button(
                onClick = onSave,
                enabled = state.meal.items.isNotEmpty() && state.phase == EditorPhase.Ready,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.height(52.dp),
            ) {
                Text(if (state.mode == EditorMode.EDIT) "Simpan perubahan" else "Simpan", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun PhotoHeader(path: String?, modifier: Modifier = Modifier) {
    if (path != null && File(path).exists()) {
        AsyncImage(
            model = File(path),
            contentDescription = "Foto makanan",
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(24.dp)),
        )
    }
}

@Composable
private fun AnalyzingView(photoPath: String?, onBackground: (() -> Unit)?) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(0.96f, 1.02f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale")
    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            seconds++
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.scale(scale)) { PhotoHeader(photoPath) }
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(8.dp))
        Text("Menganalisa makanan… ${seconds}s", style = MaterialTheme.typography.titleLarge)
        Text(
            when {
                seconds < 8 -> "AI sedang mengenali menu & memecah setiap elemen di piring"
                seconds < 20 -> "Masih memproses… server Gemini gratis kadang sedang ramai"
                else -> "Server sedang lambat. Anda bisa lanjut di background dan dapat notifikasi saat selesai."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth(0.6f))
        if (onBackground != null) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onBackground, shape = MaterialTheme.shapes.large) {
                Icon(Icons.Filled.CloudQueue, null)
                Spacer(Modifier.width(8.dp))
                Text("Lanjutkan di background")
            }
        }
    }
}

@Composable
private fun ErrorView(
    photoPath: String?,
    phase: EditorPhase.Error,
    hint: String,
    onHintChange: (String) -> Unit,
    onRetry: () -> Unit,
    onQueue: () -> Unit,
    onManual: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PhotoHeader(photoPath) }
        item {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large) {
                Row(Modifier.padding(16.dp)) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(phase.message, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        if (phase.retryable) {
            item {
                OutlinedTextField(
                    value = hint,
                    onValueChange = onHintChange,
                    label = { Text("Catatan untuk AI (opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Icon(Icons.Filled.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Coba lagi")
                }
            }
        }
        if (phase.queueable) {
            item {
                FilledTonalButton(onClick = onQueue, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Icon(Icons.Filled.CloudQueue, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Simpan ke antrian (otomatis saat online)")
                }
            }
        }
        if (phase.message.contains("API key", ignoreCase = true) || phase.message.contains("Pengaturan")) {
            item {
                FilledTonalButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                    Icon(Icons.Filled.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Buka Pengaturan")
                }
            }
        }
        item {
            OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Icon(Icons.Filled.EditNote, null)
                Spacer(Modifier.width(8.dp))
                Text("Isi manual dari DB lokal")
            }
        }
    }
}

@Composable
private fun EditorContent(
    state: EditorState,
    viewModel: MealEditorViewModel,
    onEditItem: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    val meal = state.meal
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (meal.photoPath != null && File(meal.photoPath).exists()) item { PhotoHero(meal) }

        item {
            OutlinedTextField(
                value = meal.menuName,
                onValueChange = viewModel::setMenuName,
                label = { Text("Nama menu") },
                placeholder = { Text("mis. Nasi Padang Rendang") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.titleMedium,
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MealTypeChips(meal.mealType, viewModel::setMealType, modifier = Modifier.padding(horizontal = 0.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val zoned = Instant.ofEpochMilli(meal.eatenAt).atZone(ZoneId.systemDefault())
                    AssistChip(
                        onClick = { showDate = true },
                        label = { Text(Format.relativeDay(zoned.toLocalDate())) },
                        leadingIcon = { Icon(Icons.Filled.CalendarToday, null, Modifier.size(18.dp)) },
                        shape = CircleShape,
                    )
                    AssistChip(
                        onClick = { showTime = true },
                        label = { Text(Format.time(meal.eatenAt)) },
                        leadingIcon = { Icon(Icons.Filled.Schedule, null, Modifier.size(18.dp)) },
                        shape = CircleShape,
                    )
                }
            }
        }

        item { NutritionCard(meal) }

        if (meal.grade != null) item { AssessmentCard(meal) }

        item {
            SectionHeader(
                "Elemen di piring",
                subtitle = if (meal.items.isEmpty()) "Belum ada elemen" else "${meal.items.size} elemen · ketuk untuk ubah gram / nama",
                action = {
                    TextButton(onClick = onAdd) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tambah")
                    }
                },
            )
        }

        itemsIndexed(meal.items) { index, item ->
            ItemCard(item, onClick = { onEditItem(index) })
        }

        item { AddElementCard(onAdd, empty = meal.items.isEmpty()) }

        item {
            OutlinedTextField(
                value = meal.userNote.orEmpty(),
                onValueChange = viewModel::setNote,
                label = { Text("Catatan pribadi (opsional)") },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(NutritionSource.DB)
                Text("angka dari database TKPI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SourceBadge(NutritionSource.AI)
                Text("estimasi AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showDate) DatePickDialog(meal, onDismiss = { showDate = false }, onPick = { viewModel.setDate(it); showDate = false })
    if (showTime) TimePickDialog(meal, onDismiss = { showTime = false }, onPick = { viewModel.setTime(it); showTime = false })
}

/** Foto besar dengan gradasi gelap di bawah berisi skor & keyakinan AI. */
@Composable
private fun PhotoHero(meal: Meal) {
    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(28.dp))) {
        AsyncImage(
            model = File(meal.photoPath!!),
            contentDescription = "Foto makanan",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f)),
            ),
        )
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            meal.grade?.let {
                GradeBadge(it, size = 46.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    meal.menuName.ifBlank { "Makanan" },
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${Format.kcal(meal.total.calories)} · ${meal.items.size} elemen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            meal.confidence?.let {
                Pill("AI ${(it * 100).roundToInt()}%", Color.White, filled = false, icon = Icons.Filled.AutoAwesome)
            }
        }
    }
}

@Composable
private fun NutritionCard(meal: Meal) {
    val t = meal.total
    HawCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Format.kcalNumber(t.calories), style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.width(6.dp))
                Text("kkal", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            }
            SegmentedMacroBar(t, height = 12.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val split = t.macroEnergy
                MacroValue("Protein", t.protein, split.proteinPct, MacroColors.Protein)
                MacroValue("Karbo", t.carbs, split.carbsPct, MacroColors.Carbs)
                MacroValue("Lemak", t.fat, split.fatPct, MacroColors.Fat)
                MacroValue("Serat", t.fiber, null, MacroColors.Fiber)
            }
            HorizontalDivider(color = HawTheme.colors.cardBorder)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LimitValue("🍬 Gula", "${Format.oneDecimal(t.sugar)} g", t.sugar / NutritionLimits.SUGAR_G, Modifier.weight(1f))
                LimitValue("🧂 Natrium", "${Format.grouped(t.sodium.roundToInt())} mg", t.sodium / NutritionLimits.SODIUM_MG, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LimitValue(label: String, value: String, fractionOfDaily: Double, modifier: Modifier = Modifier) {
    val color = when {
        fractionOfDaily >= 0.5 -> MaterialTheme.colorScheme.error
        fractionOfDaily >= 0.3 -> HawTheme.colors.warning
        else -> HawTheme.colors.success
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(6.dp))
        SoftProgress(fractionOfDaily.toFloat(), color, height = 6.dp)
        Text(
            "${(fractionOfDaily * 100).roundToInt()}% batas harian",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MacroValue(label: String, grams: Double, pct: Double?, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${Format.oneDecimal(grams)} g", style = MaterialTheme.typography.titleMedium)
        pct?.let { Text("${it.roundToInt()}% energi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssessmentCard(meal: Meal) {
    val grade = meal.grade ?: return
    HawCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradeBadge(grade, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Skor gizi ${grade.name}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (grade.name) {
                            "A" -> "Sangat baik, pertahankan!"
                            "B" -> "Baik, sedikit lagi sempurna"
                            "C" -> "Cukup, ada yang bisa diperbaiki"
                            "D" -> "Kurang seimbang"
                            else -> "Perlu banyak perbaikan"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val positive = setOf("Seimbang", "Kaya serat", "Protein baik")
                meal.assessments.forEach { label ->
                    Pill(label, if (label in positive) HawTheme.colors.success else HawTheme.colors.warning)
                }
            }
            if (meal.suggestions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    meal.suggestions.forEach { s ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Lightbulb, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(s, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            meal.aiNotes?.let { notes ->
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f), shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemCard(item: FoodItem, onClick: () -> Unit) {
    val accent = when (item.source) {
        NutritionSource.DB -> MaterialTheme.colorScheme.primary
        NutritionSource.AI -> MaterialTheme.colorScheme.tertiary
        NutritionSource.MANUAL -> MaterialTheme.colorScheme.secondary
    }
    HawCard(onClick = onClick, contentPadding = 0.dp) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
            Row(Modifier.weight(1f).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(6.dp))
                        SourceBadge(item.source)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(Format.gramsInt(item.grams), MaterialTheme.colorScheme.onSurfaceVariant)
                        item.cookingMethod?.let {
                            Spacer(Modifier.width(6.dp))
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    MacroInline(item.nutrients)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.kcalNumber(item.nutrients.calories), style = MaterialTheme.typography.titleLarge)
                    Text("kkal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddElementCard(onAdd: () -> Unit, empty: Boolean) {
    Surface(
        onClick = onAdd,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.AddCircle, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                if (empty) "Tambah elemen dari database makanan Indonesia" else "Ada yang terlewat? Tambah elemen",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickDialog(meal: Meal, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val initial = Instant.ofEpochMilli(meal.eatenAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { millis ->
                    onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                } ?: onDismiss()
            }) { Text("Pilih") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    ) {
        DatePicker(state = pickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(meal: Meal, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val initial = Instant.ofEpochMilli(meal.eatenAt).atZone(ZoneId.systemDefault()).toLocalTime()
    val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jam makan") },
        text = { TimePicker(state = pickerState) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(pickerState.hour, pickerState.minute)) }) { Text("Pilih") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
