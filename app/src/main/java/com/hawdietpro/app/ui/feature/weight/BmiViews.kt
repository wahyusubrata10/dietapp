package com.hawdietpro.app.ui.feature.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.domain.nutrition.BmiClass
import com.hawdietpro.app.domain.nutrition.BmiMilestone
import com.hawdietpro.app.domain.nutrition.BmiProjection
import com.hawdietpro.app.domain.nutrition.BmiProjector
import com.hawdietpro.app.domain.nutrition.TdeeCalculator
import com.hawdietpro.app.domain.nutrition.WeightProjection
import com.hawdietpro.app.ui.components.ChartLegend
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.LineChart
import com.hawdietpro.app.ui.components.LineSeries
import com.hawdietpro.app.ui.components.NoticeCard
import com.hawdietpro.app.ui.components.StatRow
import com.hawdietpro.app.ui.components.StatTile
import com.hawdietpro.app.ui.components.StatusChip
import com.hawdietpro.app.ui.theme.HawTheme
import com.hawdietpro.app.ui.theme.NumberLarge
import kotlin.math.abs

/** Rentang yang digambar pada skala BMI. */
private const val SCALE_MIN = 15.0
private const val SCALE_MAX = 35.0

@Composable
fun BmiClass.color(): Color = when (this) {
    BmiClass.KURANG -> MaterialTheme.colorScheme.tertiary
    BmiClass.NORMAL -> HawTheme.colors.success
    BmiClass.BERLEBIH -> HawTheme.colors.warning
    BmiClass.OBESITAS_1 -> MaterialTheme.colorScheme.secondary
    BmiClass.OBESITAS_2 -> MaterialTheme.colorScheme.error
}

private fun signed(v: Double, unit: String = "") = (if (v > 0.05) "+" else if (v < -0.05) "−" else "") + Format.oneDecimal(abs(v)) + unit

/** Proyeksi BMI: mengikuti proyeksi berat dari catatan kalori, dibagi kuadrat tinggi badan di profil. */
@Composable
fun BmiSection(p: WeightProjection) {
    val b = remember(p) { BmiProjector.project(p) }
    if (b == null) {
        NoticeCard(
            "Tinggi badan belum diisi",
            "Isi tinggi badan di profil untuk melihat BMI & proyeksinya.",
            Icons.Filled.Height,
            MaterialTheme.colorScheme.tertiary,
        )
        return
    }
    var range by rememberSaveable { mutableIntStateOf(30) }
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HawCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("BMI perkiraan hari ini", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Format.oneDecimal(b.current), style = NumberLarge)
                    Spacer(Modifier.width(10.dp))
                    StatusChip(b.currentClass.label, b.currentClass.color())
                }
                Text(
                    "Dari berat perkiraan ${Format.kg(p.estimatedToday)} dan tinggi ${Format.oneDecimal(b.heightCm)} cm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
                BmiScale(b)
            }
        }

        StatRow {
            StatTile(
                "Berat normal untukmu",
                "${Format.oneDecimal(b.healthyMinKg)}–${Format.oneDecimal(b.healthyMaxKg)}",
                Modifier.weight(1f),
                sub = "kg · BMI 18,5–22,9",
                color = cs.primaryContainer,
            )
            StatTile(
                "Menuju normal",
                if (b.kgToHealthy == 0.0) "Sudah ✓" else signed(b.kgToHealthy, " kg"),
                Modifier.weight(1f),
                sub = when {
                    b.kgToHealthy < 0 -> "turun lagi"
                    b.kgToHealthy > 0 -> "naik lagi"
                    else -> "pertahankan"
                },
            )
        }
        StatRow {
            StatTile(
                "Dari timbangan",
                b.actual?.let { Format.oneDecimal(it) } ?: "-",
                Modifier.weight(1f),
                sub = b.actual?.let { BmiClass.of(it).label } ?: "belum ada catatan",
            )
            StatTile(
                "Perubahan BMI",
                signed(b.current - b.start),
                Modifier.weight(1f),
                sub = "sejak ${Format.dayMonth(p.startDate)}",
            )
        }

        HawCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BMI: perkiraan vs aktual", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(14 to "14 hari", 30 to "30 hari", 90 to "90 hari", 0 to "Semua").forEach { (days, label) ->
                        FilterChip(selected = range == days, onClick = { range = days }, label = { Text(label) })
                    }
                }
                val chart = remember(p, range) { buildChart(p, range) { TdeeCalculator.bmi(it, b.heightCm) } }
                // Garis acuan: batas atas normal, atau batas bawah bila berat masih kurang.
                val reference = if (b.current < BmiProjector.HEALTHY_MIN) BmiProjector.HEALTHY_MIN else BmiProjector.HEALTHY_MAX
                LineChart(
                    series = listOf(
                        LineSeries(chart.estimate, cs.primary, "Perkiraan", fill = true),
                        LineSeries(chart.actual, cs.secondary, "Aktual", dots = true, strokeWidth = 2.dp),
                        LineSeries(chart.forecast, cs.primary.copy(alpha = 0.7f), "Proyeksi", dashed = true),
                    ),
                    labels = chart.labels,
                    tooltipLabels = chart.fullLabels,
                    target = reference,
                    targetLabel = "Batas normal ${Format.oneDecimal(reference)}",
                    valueFormat = { Format.oneDecimal(it) },
                    height = 240.dp,
                )
                ChartLegend(listOf("Perkiraan dari kalori" to cs.primary, "Timbangan" to cs.secondary, "Proyeksi 4 minggu" to cs.primary.copy(alpha = 0.7f)), dashedLast = true)
                Text(projectionText(b, p), style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (b.milestones.isNotEmpty()) {
            HawCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tahapan menuju BMI normal", style = MaterialTheme.typography.titleMedium)
                    b.milestones.forEach { MilestoneRow(it, p.estimatedToday) }
                }
            }
        }

        Text(
            "BMI memakai kategori WHO Asia-Pasifik (normal 18,5–22,9) yang lebih sesuai untuk orang Indonesia. " +
                "BMI tidak membedakan otot dan lemak, sehingga kurang tepat untuk atlet, ibu hamil/menyusui, lansia, dan anak. " +
                "Lingkar pinggang (pria < 90 cm, wanita < 80 cm) membantu melengkapi gambaran.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

private fun projectionText(b: BmiProjection, p: WeightProjection): String = buildString {
    val end = b.forecast.lastOrNull()
    if (end == null) {
        append("Catat makan lengkap minimal 3 hari untuk melihat proyeksi BMI.")
    } else {
        append("Dengan pola saat ini, BMI diperkirakan ${Format.oneDecimal(end.second)} (${BmiClass.of(end.second).label}) pada ${Format.dayMonth(end.first)}.")
        val next = b.milestones.firstOrNull { !it.reached }
        when {
            next?.date != null -> append(" Masuk kategori ${next.reachedClass.label} sekitar ${Format.shortDate(next.date)}.")
            next != null -> append(" Pola saat ini belum mengarah ke kategori ${next.reachedClass.label}.")
        }
    }
    val target = b.target
    val targetWeight = p.targetWeight
    if (target != null && targetWeight != null) {
        append(" Berat target ${Format.kg(targetWeight)} setara BMI ${Format.oneDecimal(target)} (${b.targetClass?.label}).")
        if (target < BmiProjector.HEALTHY_MIN) {
            append(" Target ini di bawah rentang normal; pertimbangkan minimal ${Format.kg(b.healthyMinKg)}.")
        }
    }
}

@Composable
private fun MilestoneRow(m: BmiMilestone, nowKg: Double) {
    val cs = MaterialTheme.colorScheme
    val color = m.reachedClass.color()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (m.reached) Icons.Filled.CheckCircle else Icons.Outlined.Flag,
            contentDescription = null,
            tint = if (m.reached) HawTheme.colors.success else color,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Masuk ${m.reachedClass.label}", style = MaterialTheme.typography.titleSmall)
            Text(
                "BMI ${Format.oneDecimal(m.bmi)} · ${Format.kg(m.weightKg)}" +
                    if (m.reached) "" else " · ${Format.oneDecimal(abs(m.weightKg - nowKg))} kg lagi",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                m.reached -> "Tercapai"
                m.date != null -> "±${Format.shortDate(m.date)}"
                else -> "–"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (m.reached) HawTheme.colors.success else cs.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

/** Skala berwarna 15–35 dengan penanda BMI sekarang (lingkaran) dan target (garis). */
@Composable
private fun BmiScale(b: BmiProjection) {
    val segments = BmiClass.entries.map { it to it.color() }
    val ink = MaterialTheme.colorScheme.onSurface
    val surface = HawTheme.colors.card
    fun fraction(v: Double) = ((v.coerceIn(SCALE_MIN, SCALE_MAX) - SCALE_MIN) / (SCALE_MAX - SCALE_MIN)).toFloat()
    val description = "Skala BMI: sekarang ${Format.oneDecimal(b.current)}, ${b.currentClass.label}" +
        (b.target?.let { ", target ${Format.oneDecimal(it)}" } ?: "")
    Column(Modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val barH = 10.dp.toPx()
            val top = (size.height - barH) / 2
            val gap = 2.dp.toPx()
            segments.forEach { (cls, color) ->
                val x0 = fraction(cls.lower) * size.width
                val x1 = fraction(cls.upper) * size.width
                if (x1 - x0 > gap) {
                    drawRoundRect(color.copy(alpha = 0.75f), Offset(x0 + gap / 2, top), Size(x1 - x0 - gap, barH), CornerRadius(barH / 2))
                }
            }
            b.target?.let { t ->
                val x = fraction(t) * size.width
                drawLine(ink, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            }
            val cx = fraction(b.current) * size.width
            drawCircle(surface, radius = 9.dp.toPx(), center = Offset(cx, size.height / 2))
            drawCircle(ink, radius = 6.dp.toPx(), center = Offset(cx, size.height / 2))
        }
        // Angka batas kategori di bawah skala.
        BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
            listOf(18.5, 23.0, 25.0, 30.0).forEach { v ->
                Box(Modifier.offset(x = maxWidth * fraction(v) - 14.dp).width(28.dp)) {
                    Text(
                        Format.oneDecimal(v),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (b.target != null) {
            Text("Garis = BMI berat target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
