package com.hawdietpro.app.ui.feature.history

import com.hawdietpro.app.ui.components.hawSegmentedColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.domain.model.Grade
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.ui.components.EmptyState
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.HawScaffold
import com.hawdietpro.app.ui.components.LoadingBox
import com.hawdietpro.app.ui.components.MealRow
import com.hawdietpro.app.ui.components.Pill
import com.hawdietpro.app.ui.components.SoftProgress
import com.hawdietpro.app.ui.theme.HawTheme
import androidx.compose.material3.HorizontalDivider
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HistoryScreen(
    onOpenMeal: (Long) -> Unit,
    onAddForDate: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HawScaffold(title = "Riwayat", topLevel = true) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@HawScaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        colors = hawSegmentedColors(),
                        selected = state.mode == HistoryMode.TIMELINE,
                        onClick = { viewModel.setMode(HistoryMode.TIMELINE) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = { Icon(Icons.AutoMirrored.Filled.ViewList, null, Modifier.size(18.dp)) },
                    ) { Text("Timeline") }
                    SegmentedButton(
                        colors = hawSegmentedColors(),
                        selected = state.mode == HistoryMode.CALENDAR,
                        onClick = { viewModel.setMode(HistoryMode.CALENDAR) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = { Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp)) },
                    ) { Text("Kalender") }
                }
            }
            item { Filters(state, viewModel) }

            if (state.mode == HistoryMode.TIMELINE) {
                if (state.groups.isEmpty()) {
                    item {
                        EmptyState(
                            Icons.Filled.History,
                            if (state.filters.active) "Tidak ada hasil" else "Belum ada riwayat",
                            if (state.filters.active) "Coba ubah kata kunci atau filter." else "Catatan makan Anda akan muncul di sini.",
                        )
                    }
                }
                items(state.groups, key = { it.date.toEpochDay() }) { group ->
                    DayCard(group, state.targetCalories, onOpenMeal)
                }
                if (state.filters.query.isBlank() && state.groups.isNotEmpty()) {
                    item {
                        TextButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth()) {
                            Text("Tampilkan lebih lama (${state.windowDays} hari terakhir)")
                        }
                    }
                }
            } else {
                item { CalendarCard(state, viewModel) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Format.relativeDay(state.selectedDate), style = MaterialTheme.typography.titleMedium)
                            Text(
                                Format.kcal(state.selectedMeals.sumOf { it.total.calories }) +
                                    (state.targetCalories?.let { " dari $it kkal" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!state.selectedDate.isAfter(LocalDate.now())) {
                            OutlinedButton(onClick = {
                                val time = state.selectedDate.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                onAddForDate(time)
                            }) {
                                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                                Text(" Tambah")
                            }
                        }
                    }
                }
                if (state.selectedMeals.isEmpty()) {
                    item { Text("Tidak ada catatan di tanggal ini.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(state.selectedMeals, key = { it.id }) { meal ->
                    MealRow(meal, onClick = { onOpenMeal(meal.id) })
                }
            }
        }
    }
}

@Composable
private fun Filters(state: HistoryUi, viewModel: HistoryViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.filters.query,
            onValueChange = viewModel::setQuery,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (state.filters.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = "Hapus") }
                }
            },
            placeholder = { Text("Cari menu atau elemen (mis. rendang)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MealType.entries.forEach { t ->
                FilterChip(selected = t in state.filters.types, onClick = { viewModel.toggleType(t) }, label = { Text(t.label) })
            }
            Grade.entries.forEach { g ->
                FilterChip(selected = g in state.filters.grades, onClick = { viewModel.toggleGrade(g) }, label = { Text("Skor ${g.name}") })
            }
            if (state.filters.active) {
                TextButton(onClick = viewModel::clearFilters) { Text("Reset") }
            }
        }
    }
}

@Composable
private fun DayCard(group: DayGroup, target: Int?, onOpenMeal: (Long) -> Unit) {
    val over = target != null && group.calories > target * 1.1
    val color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    HawCard(contentPadding = 14.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Format.relativeDay(group.date), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${group.meals.size} catatan" + (target?.let { " · ${(group.calories / it * 100).toInt()}% target" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Pill(Format.kcal(group.calories), color)
            }
            if (target != null) {
                Spacer(Modifier.height(10.dp))
                SoftProgress((group.calories / target).toFloat(), color, height = 6.dp)
            }
            Spacer(Modifier.height(4.dp))
            group.meals.forEachIndexed { i, meal ->
                if (i > 0) HorizontalDivider(color = HawTheme.colors.cardBorder)
                MealRow(meal, onClick = { onOpenMeal(meal.id) })
            }
        }
    }
}

@Composable
private fun CalendarCard(state: HistoryUi, viewModel: HistoryViewModel) {
    val month = state.month
    val today = LocalDate.now()
    HawCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.setMonth(month.minusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Bulan sebelumnya")
                }
                Text(
                    Format.monthYear(month.atDay(1)),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.setMonth(month.plusMonths(1)) }, enabled = month.atDay(1).isBefore(today.withDayOfMonth(1))) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Bulan berikutnya")
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val first = month.atDay(1)
            val offset = (first.dayOfWeek.value - DayOfWeek.MONDAY.value)
            val cells = offset + month.lengthOfMonth()
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val dayNum = r * 7 + c - offset + 1
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (dayNum in 1..month.lengthOfMonth()) {
                                val date = month.atDay(dayNum)
                                DayCell(
                                    date = date,
                                    calories = state.dayCalories[date],
                                    target = state.targetCalories,
                                    selected = date == state.selectedDate,
                                    isToday = date == today,
                                    onClick = { viewModel.select(date) },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend(MaterialTheme.colorScheme.primary, "Sesuai target")
                Legend(MaterialTheme.colorScheme.error, "Lebih")
                Legend(MaterialTheme.colorScheme.tertiary, "Kurang")
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, calories: Double?, target: Int?, selected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val status = when {
        calories == null -> null
        target == null -> MaterialTheme.colorScheme.primary
        calories > target * 1.1 -> MaterialTheme.colorScheme.error
        calories < target * 0.9 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .then(if (isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Box(Modifier.size(6.dp).clip(CircleShape).background(status ?: androidx.compose.ui.graphics.Color.Transparent))
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(" $label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
