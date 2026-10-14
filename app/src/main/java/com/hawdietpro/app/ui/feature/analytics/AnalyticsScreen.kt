package com.hawdietpro.app.ui.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.domain.insight.Period
import com.hawdietpro.app.domain.insight.PeriodStats
import com.hawdietpro.app.domain.insight.RankedItem
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.ui.components.CalorieTrendChart
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.HawScaffold
import com.hawdietpro.app.ui.components.LineTrendChart
import com.hawdietpro.app.ui.components.LoadingBox
import com.hawdietpro.app.ui.components.SectionHeader
import com.hawdietpro.app.ui.components.StatTile
import com.hawdietpro.app.ui.feature.home.InsightCard
import com.hawdietpro.app.ui.theme.MacroColors
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun AnalyticsScreen(onOpenWeight: () -> Unit, viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HawScaffold(title = "Analisa", topLevel = true) { padding ->
        val stats = state.stats
        if (state.loading || stats == null) {
            LoadingBox(Modifier.padding(padding))
            return@HawScaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(Period.WEEK to "Mingguan", Period.MONTH to "Bulanan").forEachIndexed { i, (p, label) ->
                        SegmentedButton(
                            selected = state.period == p,
                            onClick = { viewModel.setPeriod(p) },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.shift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Sebelumnya") }
                    Text(
                        rangeLabel(state.period, state.start, state.end, state.offset),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.shift(1) }, enabled = state.offset < 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Berikutnya")
                    }
                }
            }

            item { SummaryTiles(stats, state.target?.calories) }

            item {
                HawCard {
                    Column {
                        Text("Tren kalori harian", style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.target?.let { "Batang = kalori masuk · garis = target ${Format.grouped(it.calories)} kkal" } ?: "Kalori masuk per hari",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        val labels = remember(stats.days, state.period) {
                            stats.days.map { if (state.period == Period.WEEK) Format.dayName(it.date) else it.date.dayOfMonth.toString() }
                        }
                        CalorieTrendChart(
                            values = stats.days.map { it.nutrients.calories },
                            labels = labels,
                            target = state.target?.calories,
                        )
                    }
                }
            }

            if (stats.daysLogged > 0) {
                item { MacroCard(stats, state.target?.fiberG) }
                item { MealTypeCard(stats) }
                item { HourCard(stats) }
                if (stats.topMenus.isNotEmpty()) item { RankCard("Menu paling sering", stats.topMenus, showCount = true) }
                if (stats.topSources.isNotEmpty()) item { RankCard("Sumber kalori terbesar", stats.topSources, showCount = false) }
            }

            if (state.insights.isNotEmpty()) {
                item { SectionHeader("Insight otomatis") }
                items(state.insights, key = { it.id }) { InsightCard(it) }
            }

            item {
                HawCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Tren berat badan", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            stats.weightChangeKg?.let {
                                val sign = if (it > 0) "+" else ""
                                Text("$sign${Format.oneDecimal(it)} kg", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val recent = state.weights.takeLast(30)
                        LineTrendChart(
                            values = recent.map { it.weightKg },
                            labels = recent.map { Format.dayMonth(LocalDate.ofEpochDay(it.epochDay)) },
                            emptyText = "Belum ada catatan berat",
                        )
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = onOpenWeight, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.MonitorWeight, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Log berat badan")
                        }
                    }
                }
            }
        }
    }
}

private fun rangeLabel(period: Period, start: LocalDate, end: LocalDate, offset: Int): String = when (period) {
    Period.MONTH -> Format.monthYear(start) + if (offset == 0) " (bulan ini)" else ""
    else -> "${Format.dayMonth(start)} – ${Format.shortDate(end)}" + if (offset == 0) "\n(minggu ini)" else ""
}

@Composable
private fun SummaryTiles(stats: PeriodStats, target: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                "Rata-rata/hari",
                Format.grouped(stats.averagePerDay.calories.roundToInt()),
                Modifier.weight(1f),
                sub = target?.let { "kkal · target ${Format.grouped(it)}" } ?: "kkal",
                color = MaterialTheme.colorScheme.primaryContainer,
            )
            StatTile("Hari tercatat", "${stats.daysLogged}/${stats.days.size}", Modifier.weight(1f), sub = "hari")
        }
        if (target != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Sesuai target", "${stats.daysOnTarget}", Modifier.weight(1f), sub = "hari (±10%)")
                StatTile("Lebih", "${stats.daysOver}", Modifier.weight(1f), sub = "hari", color = MaterialTheme.colorScheme.errorContainer)
                StatTile("Kurang", "${stats.daysUnder}", Modifier.weight(1f), sub = "hari", color = MaterialTheme.colorScheme.tertiaryContainer)
            }
        }
    }
}

@Composable
private fun ShareBar(label: String, value: String, fraction: Float, color: Color) {
    Column(Modifier.fillMaxWidth()) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = 0.15f)),
        ) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(color))
        }
    }
}

@Composable
private fun MacroCard(stats: PeriodStats, fiberTarget: Int?) {
    val avg = stats.averagePerDay
    val split = avg.macroEnergy
    HawCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Rata-rata makro per hari", style = MaterialTheme.typography.titleMedium)
            ShareBar("Protein", "${avg.protein.roundToInt()} g · ${split.proteinPct.roundToInt()}% energi", (split.proteinPct / 100).toFloat(), MacroColors.Protein)
            ShareBar("Karbohidrat", "${avg.carbs.roundToInt()} g · ${split.carbsPct.roundToInt()}% energi", (split.carbsPct / 100).toFloat(), MacroColors.Carbs)
            ShareBar("Lemak", "${avg.fat.roundToInt()} g · ${split.fatPct.roundToInt()}% energi", (split.fatPct / 100).toFloat(), MacroColors.Fat)
            ShareBar(
                "Serat",
                "${avg.fiber.roundToInt()} g" + (fiberTarget?.let { " dari $it g" } ?: ""),
                ((avg.fiber / (fiberTarget ?: 30)).toFloat()),
                MacroColors.Fiber,
            )
            Text(
                "Acuan seimbang: protein 20–30%, karbo 45–60%, lemak 20–35% energi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MealTypeCard(stats: PeriodStats) {
    val total = stats.totalCalories.takeIf { it > 0 } ?: 1.0
    HawCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Kalori per waktu makan", style = MaterialTheme.typography.titleMedium)
            val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary, MacroColors.Carbs)
            MealType.entries.forEachIndexed { i, type ->
                val kcal = stats.mealTypeCalories[type] ?: 0.0
                ShareBar(
                    "${type.emoji} ${type.label}",
                    "${Format.kcal(kcal / stats.daysLogged.coerceAtLeast(1))}/hari · ${(kcal / total * 100).roundToInt()}%",
                    (kcal / total).toFloat(),
                    colors[i],
                )
            }
        }
    }
}

@Composable
private fun HourCard(stats: PeriodStats) {
    val buckets = listOf(
        "Pagi (04–10)" to (4 until 10),
        "Siang (10–15)" to (10 until 15),
        "Sore (15–18)" to (15 until 18),
        "Malam (18–21)" to (18 until 21),
        "Larut (21–04)" to ((21 until 24) + (0 until 4)),
    )
    val total = stats.hourlyCalories.sum().takeIf { it > 0 } ?: 1.0
    HawCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pola jam makan", style = MaterialTheme.typography.titleMedium)
            buckets.forEach { (label, hours) ->
                val kcal = hours.sumOf { stats.hourlyCalories[it] }
                ShareBar(label, "${(kcal / total * 100).roundToInt()}%", (kcal / total).toFloat(), MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun RankCard(title: String, items: List<RankedItem>, showCount: Boolean) {
    HawCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            items.forEachIndexed { i, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(24.dp))
                    Text(item.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (showCount) "${item.count}× · ${Format.kcal(item.calories)}" else "${Format.kcal(item.calories)} · ${item.sharePct.roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
