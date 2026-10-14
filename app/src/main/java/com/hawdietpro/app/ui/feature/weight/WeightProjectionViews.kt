package com.hawdietpro.app.ui.feature.weight

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.domain.nutrition.WeightProjection
import com.hawdietpro.app.ui.components.ChartLegend
import com.hawdietpro.app.ui.components.DivergingBarChart
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.LineChart
import com.hawdietpro.app.ui.components.LineSeries
import com.hawdietpro.app.ui.components.StatTile
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

private fun signed(v: Double, unit: String) = (if (v > 0) "+" else if (v < 0) "−" else "") + Format.oneDecimal(abs(v)) + unit

/** Kartu ringkas untuk Beranda. */
@Composable
fun WeightEstimateCompact(p: WeightProjection, onClick: () -> Unit) {
    HawCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = when {
                p.estimatedChange < -0.05 -> Icons.AutoMirrored.Filled.TrendingDown
                p.estimatedChange > 0.05 -> Icons.AutoMirrored.Filled.TrendingUp
                else -> Icons.AutoMirrored.Filled.TrendingFlat
            }
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Perkiraan berat hari ini", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${Format.kg(p.estimatedToday)}  (${signed(p.estimatedChange, " kg")} sejak ${Format.dayMonth(p.startDate)})",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                p.latestActual?.let {
                    Text(
                        "Timbangan terakhir ${Format.kg(it.weightKg)} · ${Format.relativeDay(LocalDate.ofEpochDay(it.epochDay))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Bagian lengkap: ringkasan, grafik perkiraan vs aktual vs target + proyeksi, dan surplus/defisit harian. */
@Composable
fun WeightProjectionSection(p: WeightProjection) {
    var range by rememberSaveable { mutableIntStateOf(30) }
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                "Perkiraan hari ini",
                Format.kg(p.estimatedToday),
                Modifier.weight(1f),
                sub = "${signed(p.estimatedChange, " kg")} sejak awal",
                color = cs.primaryContainer,
            )
            StatTile(
                "Timbangan terakhir",
                p.latestActual?.let { Format.kg(it.weightKg) } ?: "-",
                Modifier.weight(1f),
                sub = p.actualChange?.let { "${signed(it, " kg")} sejak awal" } ?: "belum ada catatan",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                "Total surplus/defisit",
                (if (p.cumulativeBalance > 0) "+" else "") + Format.grouped(p.cumulativeBalance.roundToInt()),
                Modifier.weight(1f),
                sub = "kkal sejak ${Format.dayMonth(p.startDate)}",
            )
            StatTile(
                "Rata-rata 14 hari",
                p.recentDailyBalance?.let { (if (it > 0) "+" else "") + Format.grouped(it.roundToInt()) } ?: "-",
                Modifier.weight(1f),
                sub = "kkal/hari",
            )
        }

        HawCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Berat: perkiraan vs aktual", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(14 to "14 hari", 30 to "30 hari", 90 to "90 hari", 0 to "Semua").forEach { (days, label) ->
                        FilterChip(selected = range == days, onClick = { range = days }, label = { Text(label) })
                    }
                }
                val chart = remember(p, range) { buildChart(p, range) }
                LineChart(
                    series = listOf(
                        LineSeries(chart.estimate, cs.primary, "Perkiraan", fill = true),
                        LineSeries(chart.actual, cs.secondary, "Aktual", dots = true, strokeWidth = 2.dp),
                        LineSeries(chart.forecast, cs.primary.copy(alpha = 0.7f), "Proyeksi", dashed = true),
                    ),
                    labels = chart.labels,
                    tooltipLabels = chart.fullLabels,
                    target = p.targetWeight,
                    targetLabel = p.targetWeight?.let { "Target ${Format.oneDecimal(it)}" },
                    valueFormat = { Format.oneDecimal(it) },
                    height = 240.dp,
                )
                ChartLegend(listOf("Perkiraan dari kalori" to cs.primary, "Timbangan" to cs.secondary, "Proyeksi 4 minggu" to cs.primary.copy(alpha = 0.7f)), dashedLast = true)
                ProjectionText(p)
            }
        }

        HawCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Surplus / defisit harian", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Kalori masuk dikurangi kebutuhan harian (TDEE). Hijau = defisit (berat cenderung turun), merah = surplus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
                val recent = p.days.takeLast(if (range == 0) p.days.size else range.coerceAtMost(60))
                DivergingBarChart(
                    values = recent.map { if (it.counted) it.balance else null },
                    labels = recent.map { it.date.dayOfMonth.toString() },
                    tooltipLabels = recent.map { Format.dayMonth(it.date) },
                    emptyText = "Belum ada hari lengkap yang tercatat",
                )
                if (p.skippedDays > 0) {
                    Text(
                        "${p.skippedDays} hari tidak dihitung (tanpa catatan / catatan terlalu sedikit) dan dianggap netral.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }

        val last7 = p.days.takeLast(7).reversed()
        if (last7.isNotEmpty()) {
            HawCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("7 hari terakhir", style = MaterialTheme.typography.titleMedium)
                    Row {
                        listOf("Tanggal" to 1.3f, "Masuk" to 1f, "TDEE" to 1f, "Selisih" to 1f, "Perkiraan" to 1.1f).forEach { (h, w) ->
                            Text(h, Modifier.weight(w), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = cs.outlineVariant)
                    last7.forEach { d ->
                        Row {
                            Text(Format.dayMonth(d.date), Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall)
                            Text(d.intake?.let { Format.grouped(it.roundToInt()) } ?: "-", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(Format.grouped(d.tdee.roundToInt()), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(
                                if (d.counted) (if (d.balance > 0) "+" else "") + Format.grouped(d.balance.roundToInt()) else "netral",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    !d.counted -> cs.onSurfaceVariant
                                    d.balance > 0 -> cs.error
                                    else -> cs.primary
                                },
                            )
                            Text(Format.oneDecimal(d.estimatedWeight), Modifier.weight(1.1f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Text(
            "Perkiraan memakai rumus keseimbangan energi (±7.700 kkal ≈ 1 kg) dengan TDEE yang dihitung ulang setiap hari. " +
                "Timbangan harian bisa naik-turun 0,5–1 kg karena air, garam, dan isi pencernaan, jadi bandingkan trennya, bukan angka harian.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun ProjectionText(p: WeightProjection) {
    val avg = p.recentDailyBalance
    val text = when {
        avg == null -> "Catat makan lengkap minimal 3 hari untuk melihat proyeksi."
        else -> buildString {
            val perWeek = avg * 7 / 7700
            append(
                when {
                    perWeek < -0.02 -> "Dengan rata-rata defisit ${Format.grouped((-avg).roundToInt())} kkal/hari, berat diperkirakan turun ±${Format.oneDecimal(-perWeek)} kg/minggu."
                    perWeek > 0.02 -> "Dengan rata-rata surplus ${Format.grouped(avg.roundToInt())} kkal/hari, berat diperkirakan naik ±${Format.oneDecimal(perWeek)} kg/minggu."
                    else -> "Asupan rata-rata Anda seimbang dengan kebutuhan, berat diperkirakan stabil."
                },
            )
            p.forecast.lastOrNull()?.let { (d, w) -> append(" Perkiraan pada ${Format.dayMonth(d)}: ${Format.kg(w)}.") }
            when {
                p.targetWeight != null && p.targetDate != null ->
                    append(" Target ${Format.kg(p.targetWeight)} diperkirakan tercapai sekitar ${Format.shortDate(p.targetDate)}.")
                p.targetWeight != null ->
                    append(" Dengan pola saat ini, berat belum bergerak ke arah target ${Format.kg(p.targetWeight)}.")
            }
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

private data class ProjectionChart(
    val labels: List<String>,
    val fullLabels: List<String>,
    val estimate: List<Double?>,
    val actual: List<Double?>,
    val forecast: List<Double?>,
)

/** Menyusun sumbu X: [awal/rentang] … hari ini … +28 hari proyeksi. */
private fun buildChart(p: WeightProjection, rangeDays: Int): ProjectionChart {
    val today = p.forecast.firstOrNull()?.first ?: p.days.lastOrNull()?.date?.plusDays(1) ?: p.startDate
    val rangeStart = today.minusDays(rangeDays.toLong())
    val first = if (rangeDays == 0 || rangeStart.isBefore(p.startDate)) p.startDate else rangeStart
    val last = p.forecast.lastOrNull()?.first ?: today
    val dates = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()

    // Berat perkiraan di awal hari: awal = berat awal; hari berikutnya = akhir hari sebelumnya.
    val estimateByDate = HashMap<LocalDate, Double>()
    estimateByDate[p.startDate] = p.startWeight
    p.days.forEach { estimateByDate[it.date.plusDays(1)] = it.estimatedWeight }
    val actualByDate = p.actualWeights.associate { LocalDate.ofEpochDay(it.epochDay) to it.weightKg }
    val forecastByDate = p.forecast.toMap()

    return ProjectionChart(
        labels = dates.map { Format.dayMonth(it) },
        fullLabels = dates.map { Format.dayMonth(it) },
        estimate = dates.map { d -> if (d.isAfter(today)) null else estimateByDate[d] },
        actual = dates.map { actualByDate[it] },
        forecast = dates.map { d -> if (d.isBefore(today)) null else forecastByDate[d] },
    )
}
