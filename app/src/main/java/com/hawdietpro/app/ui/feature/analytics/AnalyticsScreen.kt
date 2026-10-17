package com.hawdietpro.app.ui.feature.analytics

import com.hawdietpro.app.ui.components.StatRow
import com.hawdietpro.app.ui.components.hawSegmentedColors
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.domain.insight.Period
import com.hawdietpro.app.domain.insight.PeriodStats
import com.hawdietpro.app.domain.insight.RankedItem
import com.hawdietpro.app.domain.model.Grade
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.nutrition.DailyTarget
import com.hawdietpro.app.domain.nutrition.NutritionLimits
import com.hawdietpro.app.ui.components.BarChart
import com.hawdietpro.app.ui.components.Banner
import com.hawdietpro.app.ui.components.ChartLegend
import com.hawdietpro.app.ui.components.DivergingBarChart
import com.hawdietpro.app.ui.components.DonutChart
import com.hawdietpro.app.ui.components.DonutSlice
import com.hawdietpro.app.ui.components.GradeBadge
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.HawScaffold
import com.hawdietpro.app.ui.components.LoadingBox
import com.hawdietpro.app.ui.components.StackedBarChart
import com.hawdietpro.app.ui.components.StatTile
import com.hawdietpro.app.ui.components.gradeColor
import com.hawdietpro.app.ui.feature.home.InsightCard
import com.hawdietpro.app.ui.feature.weight.WeightProjectionSection
import com.hawdietpro.app.ui.theme.MacroColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.abs
import kotlin.math.roundToInt

private val TABS = listOf("Ringkasan", "Nutrisi", "Pola", "Berat")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(onOpenWeight: () -> Unit, viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val review by viewModel.review.collectAsStateWithLifecycle()

    HawScaffold(title = "Analisa", topLevel = true) { padding ->
        val stats = state.stats
        if (state.loading || stats == null) {
            LoadingBox(Modifier.padding(padding))
            return@HawScaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                TABS.forEachIndexed { i, title ->
                    Tab(
                        selected = tab == i,
                        onClick = { viewModel.setTab(i) },
                        text = { Text(title, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Medium) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tab != 3) periodHeader(state, viewModel)
                when (tab) {
                    0 -> summaryTab(state, stats, review, viewModel)
                    1 -> nutritionTab(stats, state.target)
                    2 -> patternTab(stats, state.period)
                    else -> weightTab(state, onOpenWeight)
                }
            }
        }
    }
}

// ------------------------------------------- Header --------------------------------------------

private fun LazyListScope.periodHeader(state: AnalyticsUi, viewModel: AnalyticsViewModel) {
    item {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(Period.WEEK to "Mingguan", Period.MONTH to "Bulanan").forEachIndexed { i, (p, label) ->
                SegmentedButton(
                    colors = hawSegmentedColors(),
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
}

private fun rangeLabel(period: Period, start: LocalDate, end: LocalDate, offset: Int): String = when (period) {
    Period.MONTH -> Format.monthYear(start) + if (offset == 0) " · bulan ini" else ""
    else -> {
        val range = if (start.month == end.month) "${start.dayOfMonth}–${Format.shortDate(end)}" else "${Format.dayMonth(start)} – ${Format.shortDate(end)}"
        range + if (offset == 0) " · minggu ini" else ""
    }
}

private fun dayLabels(stats: PeriodStats, period: Period) =
    stats.days.map { if (period == Period.WEEK) Format.dayName(it.date) else it.date.dayOfMonth.toString() }

private fun dayTooltips(stats: PeriodStats) = stats.days.map { Format.dayName(it.date) + " " + Format.dayMonth(it.date) }

// ------------------------------------------ Ringkasan ------------------------------------------

private fun LazyListScope.summaryTab(state: AnalyticsUi, stats: PeriodStats, review: ReviewState, viewModel: AnalyticsViewModel) {
    val target = state.target
    item { SummaryTiles(stats, state.previous, target?.calories) }

    val hasExercise = stats.days.any { it.exercise > 0 }
    item {
        HawCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (hasExercise) "Kalori bersih harian" else "Kalori harian", style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        if (hasExercise) append("Kalori makan dikurangi kalori olahraga. ")
                        append(target?.let { "Garis putus-putus = target ${Format.grouped(it.calories)} kkal · merah = >10% di atas target" } ?: "Kalori masuk per hari")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BarChart(
                    values = stats.days.map { it.netCalories.coerceAtLeast(0.0) },
                    labels = dayLabels(stats, state.period),
                    tooltipLabels = dayTooltips(stats),
                    target = target?.calories?.toDouble(),
                    targetLabel = "Target",
                    valueFormat = { Format.grouped(it.roundToInt()) },
                    emptyText = "Belum ada catatan di periode ini",
                )
            }
        }
    }

    if (target != null && stats.daysLogged > 0) {
        item {
            HawCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Selisih terhadap target", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Kalori bersih (makan − olahraga) dibanding target. Hijau = di bawah target · merah = di atas target. Hari tanpa catatan dikosongkan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DivergingBarChart(
                        values = stats.days.map { if (it.mealCount > 0) it.netCalories - target.calories else null },
                        labels = dayLabels(stats, state.period),
                        tooltipLabels = dayTooltips(stats),
                    )
                    val total = stats.days.filter { it.mealCount > 0 }.sumOf { it.netCalories - target.calories }
                    Text(
                        if (total <= 0) "Total ${Format.grouped((-total).roundToInt())} kkal di bawah target pada hari tercatat (≈ ${Format.oneDecimal(-total / 7700)} kg)."
                        else "Total ${Format.grouped(total.roundToInt())} kkal di atas target pada hari tercatat (≈ ${Format.oneDecimal(total / 7700)} kg).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    if (hasExercise) item { ExerciseSummaryCard(stats, state.period) }

    item { ReviewCard(review, stats.daysLogged, viewModel) }

    if (state.insights.isNotEmpty()) {
        item { Text("Insight otomatis", style = MaterialTheme.typography.titleMedium) }
        items(state.insights, key = { it.id }) { InsightCard(it) }
    }
}

@Composable
private fun ExerciseSummaryCard(stats: PeriodStats, period: Period) {
    val active = stats.days.filter { it.exercise > 0 }
    val total = active.sumOf { it.exercise }
    HawCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Olahraga", style = MaterialTheme.typography.titleMedium)
            StatRow {
                StatTile("Terbakar", Format.grouped(total.roundToInt()), Modifier.weight(1f), sub = "kkal total", accent = MaterialTheme.colorScheme.tertiary)
                StatTile("Hari aktif", "${active.size}/${stats.days.size}", Modifier.weight(1f), sub = "hari")
                StatTile("Rata-rata", Format.grouped((total / active.size).roundToInt()), Modifier.weight(1f), sub = "kkal/hari aktif")
            }
            BarChart(
                values = stats.days.map { it.exercise },
                labels = dayLabels(stats, period),
                tooltipLabels = dayTooltips(stats),
                valueFormat = { Format.grouped(it.roundToInt()) },
                emptyText = "Belum ada olahraga",
                barColor = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                "≈ ${Format.oneDecimal(total / 7700)} kg lemak setara dari olahraga periode ini.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryTiles(stats: PeriodStats, prev: PeriodStats?, target: Int?) {
    val avg = stats.averagePerDay.calories
    val prevAvg = prev?.takeIf { it.daysLogged > 0 }?.averagePerDay?.calories
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatRow {
            StatTile(
                "Rata-rata/hari",
                Format.grouped(avg.roundToInt()),
                Modifier.weight(1f),
                sub = buildString {
                    append("kkal")
                    target?.let { append(" · target ").append(Format.grouped(it)) }
                },
                color = MaterialTheme.colorScheme.primaryContainer,
            )
            StatTile(
                "vs periode lalu",
                if (prevAvg != null && prevAvg > 0 && stats.daysLogged > 0) {
                    val pct = (avg - prevAvg) / prevAvg * 100
                    (if (pct > 0) "+" else "") + "${pct.roundToInt()}%"
                } else "-",
                Modifier.weight(1f),
                sub = prevAvg?.let { "lalu ${Format.grouped(it.roundToInt())} kkal" } ?: "belum ada data",
            )
        }
        StatRow {
            StatTile("Tercatat", "${stats.daysLogged}/${stats.days.size}", Modifier.weight(1f), sub = "hari")
            if (target != null) {
                StatTile("Sesuai", "${stats.daysOnTarget}", Modifier.weight(1f), sub = "hari (±10%)")
                StatTile("Lebih", "${stats.daysOver}", Modifier.weight(1f), sub = "hari", color = MaterialTheme.colorScheme.errorContainer)
                StatTile("Kurang", "${stats.daysUnder}", Modifier.weight(1f), sub = "hari", color = MaterialTheme.colorScheme.tertiaryContainer)
            }
        }
    }
}

@Composable
private fun ReviewCard(review: ReviewState, daysLogged: Int, viewModel: AnalyticsViewModel) {
    if (review is ReviewState.Unavailable) return
    HawCard(color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text("Analisa mendalam AI", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (review is ReviewState.Ready) {
                    IconButton(onClick = viewModel::generateReview) { Icon(Icons.Filled.Refresh, contentDescription = "Buat ulang") }
                }
            }
            when (review) {
                ReviewState.Idle -> {
                    Text(
                        "Gemini membaca ringkasan angka periode ini (tanpa foto), lalu memberi evaluasi, rekomendasi, dan contoh menu yang sesuai target Anda.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = viewModel::generateReview, enabled = daysLogged > 0) {
                        Text(if (daysLogged > 0) "Buat analisa" else "Belum ada data")
                    }
                }
                ReviewState.Loading -> {
                    Text("Gemini sedang menganalisa pola makan Anda…", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is ReviewState.Error -> {
                    Text(review.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = viewModel::generateReview) { Text("Coba lagi") }
                }
                is ReviewState.Ready -> ReviewContent(review)
                ReviewState.Unavailable -> Unit
            }
        }
    }
}

@Composable
private fun ReviewContent(state: ReviewState.Ready) {
    val r = state.cached.review
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.outdated) {
            Text(
                "Ada catatan baru sejak analisa ini dibuat. Ketuk ↻ untuk memperbarui.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        r.periodScore?.takeIf { it.isNotBlank() }?.let { score ->
            val grade = Grade.parse(score.trim().take(1).uppercase())
            Row(verticalAlignment = Alignment.CenterVertically) {
                grade?.let {
                    GradeBadge(it, size = 40.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(score, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
        Text(r.summary, style = MaterialTheme.typography.bodyLarge)
        BulletSection("👍 Sudah baik", r.positives)
        BulletSection("⚠️ Perlu diperbaiki", r.improvements)
        if (r.recommendations.isNotEmpty()) {
            Text("💡 Rekomendasi", style = MaterialTheme.typography.titleSmall)
            r.recommendations.forEachIndexed { i, rec ->
                Column {
                    Text("${i + 1}. ${rec.title}", style = MaterialTheme.typography.labelLarge)
                    Text(rec.detail, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        BulletSection("🍽️ Contoh menu sehari", r.sampleMenu)
        r.nextFocus?.takeIf { it.isNotBlank() }?.let {
            Banner(text = it, title = "🎯 Fokus periode berikutnya", container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(
            "Dibuat ${Format.time(state.cached.createdAt)} · bukan nasihat medis",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BulletSection(title: String, items: List<String>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        items.forEach { Row { Text("•  ", color = MaterialTheme.colorScheme.primary); Text(it, style = MaterialTheme.typography.bodyMedium) } }
    }
}

// ------------------------------------------- Nutrisi -------------------------------------------

private fun LazyListScope.nutritionTab(stats: PeriodStats, target: DailyTarget?) {
    if (stats.daysLogged == 0) {
        item { Text("Belum ada catatan di periode ini.", color = Color.Gray) }
        return
    }
    val avg = stats.averagePerDay
    item {
        HawCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Komposisi energi rata-rata", style = MaterialTheme.typography.titleMedium)
                DonutChart(
                    slices = listOf(
                        DonutSlice("Protein", avg.protein * 4, MacroColors.Protein),
                        DonutSlice("Karbohidrat", avg.carbs * 4, MacroColors.Carbs),
                        DonutSlice("Lemak", avg.fat * 9, MacroColors.Fat),
                    ),
                    centerTitle = Format.grouped(avg.calories.roundToInt()),
                    centerSubtitle = "kkal/hari",
                )
                Text(
                    "Acuan seimbang: protein 20–30%, karbo 45–60%, lemak 20–35% energi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    item {
        HawCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kalori dari makro per hari", style = MaterialTheme.typography.titleMedium)
                StackedBarChart(
                    stacks = stats.days.map { listOf(it.nutrients.protein * 4, it.nutrients.carbs * 4, it.nutrients.fat * 9) },
                    colors = listOf(MacroColors.Protein, MacroColors.Carbs, MacroColors.Fat),
                    names = listOf("P", "K", "L"),
                    labels = stats.days.map { it.date.dayOfMonth.toString() },
                    tooltipLabels = dayTooltips(stats),
                    valueFormat = { Format.grouped(it.roundToInt()) },
                )
                ChartLegend(listOf("Protein" to MacroColors.Protein, "Karbo" to MacroColors.Carbs, "Lemak" to MacroColors.Fat))
            }
        }
    }
    if (target != null) {
        item {
            val logged = stats.days.filter { it.mealCount > 0 }
            val proteinHit = logged.count { it.nutrients.protein >= target.proteinG * 0.9 }
            val fiberHit = logged.count { it.nutrients.fiber >= target.fiberG * 0.9 }
            val calHit = logged.count { abs(it.netCalories - target.calories) <= target.calories * 0.1 }
            HawCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Target tercapai", style = MaterialTheme.typography.titleMedium)
                    HitBar("Kalori sesuai target (±10%)", calHit, logged.size, MaterialTheme.colorScheme.primary)
                    HitBar("Protein ≥ ${target.proteinG} g", proteinHit, logged.size, MacroColors.Protein)
                    HitBar("Serat ≥ ${target.fiberG} g", fiberHit, logged.size, MacroColors.Fiber)
                    Text(
                        "Rata-rata protein ${avg.protein.roundToInt()} g (target ${target.proteinG} g) · serat ${avg.fiber.roundToInt()} g (target ${target.fiberG} g)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    item { SugarSaltCard(stats) }
    item { GradeCard(stats) }
    if (stats.topSources.isNotEmpty()) item { RankCard("Sumber kalori terbesar", stats.topSources, showCount = false) }
}

@Composable
private fun SugarSaltCard(stats: PeriodStats) {
    val logged = stats.days.filter { it.mealCount > 0 }
    val avg = stats.averagePerDay
    val sugarOver = logged.count { it.nutrients.sugar > NutritionLimits.SUGAR_G }
    val sodiumOver = logged.count { it.nutrients.sodium > NutritionLimits.SODIUM_MG }
    HawCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Gula & garam", style = MaterialTheme.typography.titleMedium)
            Text(
                "Batas harian Kemenkes: gula 50 g (±4 sdm) · natrium 2.000 mg (±5 g garam / 1 sdt).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatRow {
                StatTile("Gula rata-rata", "${avg.sugar.roundToInt()} g", Modifier.weight(1f), sub = "$sugarOver hari lewat batas", accent = MacroColors.Carbs)
                StatTile("Natrium rata-rata", "${Format.grouped(avg.sodium.roundToInt())} mg", Modifier.weight(1f), sub = "$sodiumOver hari lewat batas", accent = MacroColors.Fat)
            }
            BarChart(
                values = stats.days.map { it.nutrients.sodium },
                labels = stats.days.map { it.date.dayOfMonth.toString() },
                tooltipLabels = dayTooltips(stats),
                target = NutritionLimits.SODIUM_MG,
                targetLabel = "Batas natrium",
                barColor = MacroColors.Fat,
                valueFormat = { Format.grouped(it.roundToInt()) },
                height = 170.dp,
                emptyText = "Belum ada data natrium",
            )
        }
    }
}

@Composable
private fun HitBar(label: String, hit: Int, total: Int, color: Color) {
    ShareBar(label, "$hit/$total hari", if (total > 0) hit / total.toFloat() else 0f, color)
}

@Composable
private fun GradeCard(stats: PeriodStats) {
    val total = stats.gradeCounts.values.sum()
    if (total == 0) return
    val avgScore = stats.gradeCounts.entries.sumOf { (g, c) -> (4 - g.ordinal) * c } / total.toDouble()
    val avgGrade = Grade.entries[(4 - avgScore.roundToInt()).coerceIn(0, 4)]
    HawCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Distribusi skor gizi", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("rata-rata ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                GradeBadge(avgGrade, size = 28.dp)
            }
            Grade.entries.forEach { g ->
                val c = stats.gradeCounts[g] ?: 0
                ShareBar("Skor ${g.name}", "$c makan", c / total.toFloat(), gradeColor(g))
            }
        }
    }
}

// --------------------------------------------- Pola --------------------------------------------

private fun LazyListScope.patternTab(stats: PeriodStats, period: Period) {
    if (stats.daysLogged == 0) {
        item { Text("Belum ada catatan di periode ini.", color = Color.Gray) }
        return
    }
    item {
        StatRow {
            StatTile("Streak", "${stats.currentStreak}", Modifier.weight(1f), sub = "hari beruntun", color = MaterialTheme.colorScheme.primaryContainer)
            StatTile("Konsistensi", "${(stats.daysLogged * 100 / stats.days.size.coerceAtLeast(1))}%", Modifier.weight(1f), sub = "hari tercatat")
            StatTile("Larut malam", "${(stats.metrics["lateNightShare"] ?: 0.0).roundToInt()}%", Modifier.weight(1f), sub = "kalori ≥ 21.00")
        }
    }
    item {
        val colors = listOf(Color(0xFFF2A93B), Color(0xFF2E7D5B), Color(0xFF4F5BB8), Color(0xFFE5635A))
        HawCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Kalori per waktu makan", style = MaterialTheme.typography.titleMedium)
                DonutChart(
                    slices = MealType.entries.mapIndexed { i, t -> DonutSlice(t.label, stats.mealTypeCalories[t] ?: 0.0, colors[i]) },
                    centerTitle = Format.grouped(stats.totalCalories.roundToInt()),
                    centerSubtitle = "kkal total",
                    legendValue = { s, pct -> "${pct.roundToInt()}% · ${Format.grouped((s.value / stats.daysLogged).roundToInt())}/hr" },
                )
            }
        }
    }
    item {
        HawCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pola jam makan", style = MaterialTheme.typography.titleMedium)
                Text("Total kalori per jam dalam periode ini", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BarChart(
                    values = stats.hourlyCalories,
                    labels = (0 until 24).map { if (it % 3 == 0) "$it" else "" },
                    tooltipLabels = (0 until 24).map { "Jam %02d.00".format(it) },
                    barColor = MaterialTheme.colorScheme.tertiary,
                    overColor = null,
                    valueFormat = { Format.grouped(it.roundToInt()) },
                    height = 180.dp,
                )
            }
        }
    }
    if (period == Period.MONTH || stats.weekdayAverages.size >= 3) {
        item {
            val order = DayOfWeek.entries
            HawCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Rata-rata per hari dalam seminggu", style = MaterialTheme.typography.titleMedium)
                    BarChart(
                        values = order.map { stats.weekdayAverages[it] ?: 0.0 },
                        labels = order.map { it.getDisplayName(TextStyle.SHORT, Format.locale) },
                        barColor = MaterialTheme.colorScheme.secondary,
                        overColor = null,
                        valueFormat = { Format.grouped(it.roundToInt()) },
                        height = 180.dp,
                    )
                    val maxDay = stats.weekdayAverages.maxByOrNull { it.value }
                    maxDay?.let {
                        Text(
                            "Kalori tertinggi biasanya di hari ${it.key.getDisplayName(TextStyle.FULL, Format.locale)} (${Format.kcal(it.value)}).",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
    if (stats.topMenus.isNotEmpty()) item { RankCard("Menu paling sering", stats.topMenus, showCount = true) }
}

// --------------------------------------------- Berat -------------------------------------------

private fun LazyListScope.weightTab(state: AnalyticsUi, onOpenWeight: () -> Unit) {
    item {
        FilledTonalButton(onClick = onOpenWeight, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.MonitorWeight, null)
            Spacer(Modifier.width(8.dp))
            Text("Catat timbangan")
        }
    }
    val p = state.projection
    if (p == null) {
        item { Text("Lengkapi profil untuk melihat perkiraan berat.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        item { WeightProjectionSection(p) }
    }
}

// ------------------------------------------- Shared --------------------------------------------

@Composable
private fun ShareBar(label: String, value: String, fraction: Float, color: Color) {
    Column(Modifier.fillMaxWidth()) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(color))
        }
    }
}

@Composable
private fun RankCard(title: String, items: List<RankedItem>, showCount: Boolean) {
    val max = items.maxOfOrNull { it.calories }?.takeIf { it > 0 } ?: 1.0
    HawCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            items.forEachIndexed { i, item ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(24.dp))
                        Text(item.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (showCount) "${item.count}× · ${Format.kcal(item.calories)}" else "${Format.kcal(item.calories)} · ${item.sharePct.roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.padding(start = 24.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        Box(Modifier.fillMaxWidth((item.calories / max).toFloat()).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
                    }
                }
            }
        }
    }
}
