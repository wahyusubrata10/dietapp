package com.hawdietpro.app.ui.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.data.update.UpdateState
import com.hawdietpro.app.domain.insight.Insight
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.ui.MainViewModel
import com.hawdietpro.app.ui.components.Banner
import com.hawdietpro.app.ui.components.CalorieRing
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.LoadingBox
import com.hawdietpro.app.ui.components.MacroProgress
import com.hawdietpro.app.ui.components.MealRow
import com.hawdietpro.app.ui.components.macroSpecs
import com.hawdietpro.app.ui.feature.weight.WeightEstimateCompact
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    onOpenCamera: (MealType?) -> Unit,
    onManual: (MealType?) -> Unit,
    onOpenMeal: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWeight: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val update by mainViewModel.updateState.collectAsStateWithLifecycle()
    val announcement by mainViewModel.announcement.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshDate()
        onPauseOrDispose { }
    }
    RequestNotificationPermissionOnce()

    if (state.loading) {
        LoadingBox()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    if (state.name.isNotBlank()) "Halo, ${state.name.substringBefore(' ')} 👋" else "Halo 👋",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(Format.date(state.date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        when (val u = update) {
            is UpdateState.Available, is UpdateState.Downloading, is UpdateState.ReadyToInstall -> item {
                val text = when (u) {
                    is UpdateState.ReadyToInstall -> "Versi ${u.release.versionName} siap dipasang. Ketuk untuk install."
                    is UpdateState.Downloading -> "Mengunduh versi ${u.release.versionName}… ${(u.progress * 100).roundToInt()}%"
                    is UpdateState.Available -> "Versi ${u.release.versionName} tersedia. Ketuk untuk melihat perubahan."
                    else -> ""
                }
                Banner(
                    text = text,
                    title = "Update tersedia",
                    icon = Icons.Filled.SystemUpdate,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = onOpenUpdate,
                )
            }
            else -> Unit
        }

        announcement?.let { a ->
            item {
                Banner(
                    text = a.message,
                    title = a.title,
                    icon = Icons.Filled.Campaign,
                    container = if (a.level == "warning") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    content = if (a.level == "warning") MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    actions = { TextButton(onClick = { mainViewModel.dismissAnnouncement(a.id) }) { Text("Tutup") } },
                )
            }
        }

        if (!state.hasApiKey) {
            item {
                Banner(
                    title = "Isi API key Gemini",
                    text = "Analisa foto membutuhkan API key gratis dari Google AI Studio. Ketuk untuk mengisi.",
                    icon = Icons.Filled.Key,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = onOpenSettings,
                )
            }
        }

        item { SummaryCard(state) }

        state.projection?.takeIf { it.days.isNotEmpty() || it.latestActual != null }?.let { proj ->
            item { WeightEstimateCompact(proj, onClick = onOpenWeight) }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onOpenCamera(null) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = MaterialTheme.shapes.large,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.Filled.CameraAlt, null, Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Foto Makanan", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = { onManual(null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Filled.EditNote, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Input manual cepat")
                }
            }
        }

        if (state.pendingCount > 0) {
            item {
                Banner(
                    title = "${state.pendingCount} foto di antrian",
                    text = "Akan dianalisa otomatis saat online. Ketuk untuk melihat.",
                    icon = Icons.Filled.CloudQueue,
                    onClick = onOpenQueue,
                )
            }
        }

        MealType.entries.forEach { type ->
            val list = state.byType[type].orEmpty()
            item(key = "type_${type.name}") {
                HawCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${type.emoji}  ${type.label}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (list.isNotEmpty()) {
                                Text(
                                    Format.kcal(list.sumOf { it.total.calories }),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onOpenCamera(type) }) {
                                Icon(Icons.Filled.Add, contentDescription = "Tambah ${type.label}")
                            }
                        }
                        if (list.isEmpty()) {
                            Text(
                                "Belum ada catatan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            list.forEachIndexed { i, meal ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                MealRow(meal, onClick = { onOpenMeal(meal.id) }, showType = false)
                            }
                        }
                    }
                }
            }
        }

        if (state.insights.isNotEmpty()) {
            items(state.insights, key = { it.id }) { InsightCard(it) }
        }

        state.tip?.let { tip ->
            item {
                Banner(text = tip, title = "Tips hari ini", icon = Icons.Filled.TipsAndUpdates)
            }
        }
    }
}

@Composable
private fun SummaryCard(state: HomeState) {
    val target = state.target
    HawCard(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CalorieRing(consumed = state.total.calories, target = target?.calories ?: 2000, size = 150.dp, stroke = 14.dp)
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledValue("Target", Format.kcal((target?.calories ?: 0).toDouble()))
                    LabeledValue("Masuk", Format.kcal(state.total.calories))
                    LabeledValue("Makan", "${state.meals.size} kali")
                }
            }
            Spacer(Modifier.height(16.dp))
            if (target != null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    macroSpecs(state.total, target.proteinG, target.carbsG, target.fatG, target.fiberG).forEach { spec ->
                        MacroProgress(spec)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun InsightCard(insight: Insight, modifier: Modifier = Modifier) {
    val (container, content) = when (insight.severity) {
        "positive" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "warning" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
    }
    Banner(text = insight.message, icon = Icons.Filled.Lightbulb, container = container, content = content, modifier = modifier)
}

@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < 33) return
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
