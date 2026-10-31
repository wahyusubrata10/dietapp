package com.hawdietpro.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Grafik Compose Canvas buatan sendiri: ringan, konsisten dengan tema, dan bisa di-tap untuk
// melihat nilai. Semua grafik memakai ChartColors dari MaterialTheme agar rapi di mode gelap.
// ---------------------------------------------------------------------------------------------

private data class ChartStyle(
    val text: Color,
    val grid: Color,
    val tooltipBg: Color,
    val tooltipText: Color,
    val label: TextStyle,
    val tooltip: TextStyle,
)

@Composable
private fun chartStyle(): ChartStyle {
    val cs = MaterialTheme.colorScheme
    return ChartStyle(
        text = cs.onSurfaceVariant,
        grid = cs.outlineVariant.copy(alpha = 0.6f),
        tooltipBg = cs.inverseSurface,
        tooltipText = cs.inverseOnSurface,
        label = TextStyle(fontSize = 10.sp, color = cs.onSurfaceVariant),
        tooltip = TextStyle(fontSize = 12.sp, color = cs.inverseOnSurface, fontWeight = FontWeight.SemiBold),
    )
}

/** Skala sumbu Y yang "bulat" (0, 500, 1000, …). */
private data class Scale(val min: Double, val max: Double, val step: Double) {
    val range get() = (max - min).takeIf { it > 0 } ?: 1.0
    fun ticks(): List<Double> {
        val out = ArrayList<Double>()
        var v = min
        while (v <= max + step * 0.001) {
            out += v
            v += step
        }
        return out
    }
}

private fun niceStep(range: Double, ticks: Int): Double {
    if (range <= 0) return 1.0
    val raw = range / ticks
    val mag = 10.0.pow(floor(log10(raw)))
    val norm = raw / mag
    val nice = when {
        norm <= 1 -> 1.0
        norm <= 2 -> 2.0
        norm <= 2.5 -> 2.5
        norm <= 5 -> 5.0
        else -> 10.0
    }
    return nice * mag
}

private fun niceScale(lo: Double, hi: Double, ticks: Int = 4, includeZero: Boolean = true): Scale {
    var a = if (includeZero) min(0.0, lo) else lo
    var b = if (includeZero) max(0.0, hi) else hi
    if (a == b) {
        a -= 1.0
        b += 1.0
    }
    val step = niceStep(b - a, ticks)
    return Scale(floor(a / step) * step, ceil(b / step) * step, step)
}

private fun defaultFormat(v: Double): String =
    if (abs(v) >= 1000) "%.1fk".format(v / 1000).replace(".0k", "k").replace('.', ',')
    else if (v == floor(v)) v.toInt().toString()
    else "%.1f".format(v).replace('.', ',')

private fun DrawScope.drawYAxis(
    scale: Scale,
    plot: Rect,
    measurer: TextMeasurer,
    style: ChartStyle,
    format: (Double) -> String,
) {
    for (t in scale.ticks()) {
        val y = plot.bottom - ((t - scale.min) / scale.range * plot.height).toFloat()
        drawLine(style.grid, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1f)
        val layout = measurer.measure(format(t), style.label)
        drawText(layout, topLeft = Offset(plot.left - layout.size.width - 6f, y - layout.size.height / 2f))
    }
}

private fun DrawScope.drawXLabels(
    labels: List<String>,
    xOf: (Int) -> Float,
    plot: Rect,
    measurer: TextMeasurer,
    style: ChartStyle,
) {
    if (labels.isEmpty()) return
    val gap = 6.dp.toPx()
    fun left(i: Int, width: Int) = (xOf(i) - width / 2f).coerceIn(plot.left - 8f, size.width - width.toFloat())
    // Jarak antar label dari lebar label sebenarnya, agar tidak saling menimpa (mis. "13 Okt18 Okt").
    val widest = max(measurer.measure(labels.first(), style.label).size.width, measurer.measure(labels.last(), style.label).size.width)
    val step = if (labels.size > 1) (xOf(labels.lastIndex) - xOf(0)) / labels.lastIndex else Float.MAX_VALUE
    val every = max(1, ceil((widest + gap) / step.coerceAtLeast(1f)).toInt())
    val lastLayout = measurer.measure(labels.last(), style.label)
    val lastLeft = left(labels.lastIndex, lastLayout.size.width)
    var prevRight = Float.NEGATIVE_INFINITY
    for (i in labels.indices step every) {
        if (i == labels.lastIndex) break
        val layout = measurer.measure(labels[i], style.label)
        val x = left(i, layout.size.width)
        // Label terakhir selalu tampil; label lain yang terlalu dekat dengannya dilewati.
        if (x < prevRight + gap || x + layout.size.width > lastLeft - gap) continue
        drawText(layout, topLeft = Offset(x, plot.bottom + 6f))
        prevRight = x + layout.size.width
    }
    if (lastLeft >= prevRight + gap || labels.size == 1) drawText(lastLayout, topLeft = Offset(lastLeft, plot.bottom + 6f))
}

private fun DrawScope.drawTooltip(text: String, anchor: Offset, measurer: TextMeasurer, style: ChartStyle) {
    val layout = measurer.measure(text, style.tooltip)
    val padH = 8.dp.toPx()
    val padV = 5.dp.toPx()
    val w = layout.size.width + padH * 2
    val h = layout.size.height + padV * 2
    val x = (anchor.x - w / 2).coerceIn(0f, size.width - w)
    val y = (anchor.y - h - 8.dp.toPx()).coerceAtLeast(0f)
    drawRoundRect(style.tooltipBg, Offset(x, y), Size(w, h), CornerRadius(6.dp.toPx()))
    drawText(layout, topLeft = Offset(x + padH, y + padV))
}

private fun DrawScope.drawTargetLine(y: Float, plot: Rect, color: Color, label: String?, measurer: TextMeasurer, style: ChartStyle) {
    drawLine(
        color,
        Offset(plot.left, y),
        Offset(plot.right, y),
        strokeWidth = 2.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
    )
    label?.let {
        val layout = measurer.measure(it, style.label.copy(color = color, fontWeight = FontWeight.SemiBold))
        drawText(layout, topLeft = Offset(plot.right - layout.size.width, y - layout.size.height - 2f))
    }
}

private fun plotRect(size: Size, leftPad: Float, bottomPad: Float, topPad: Float) =
    Rect(leftPad, topPad, size.width - 4f, size.height - bottomPad)

/**
 * Grafik "tumbuh" sekali saat pertama tampil / datanya berubah.
 * Status tersimpan (saveable) agar grafik tidak tumbuh ulang setiap kali di-scroll kembali ke layar.
 */
@Composable
private fun rememberEntrance(key: Any?): Float {
    var playedFor by rememberSaveable { mutableStateOf<Int?>(null) }
    val hash = key.hashCode()
    val anim = remember(key) { Animatable(if (playedFor == hash) 1f else 0f) }
    LaunchedEffect(key) {
        anim.animateTo(1f, tween(700, easing = SmoothEasing))
        playedFor = hash
    }
    return anim.value
}

// ------------------------------------------ Bar chart ------------------------------------------

/**
 * Grafik batang (mis. kalori per hari) + garis target putus-putus.
 * Batang yang melewati target × [overFactor] diberi [overColor].
 */
@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    target: Double? = null,
    targetLabel: String? = null,
    barColor: Color = MaterialTheme.colorScheme.primary,
    overColor: Color? = MaterialTheme.colorScheme.error,
    overFactor: Double = 1.1,
    tooltipLabels: List<String> = labels,
    valueFormat: (Double) -> String = ::defaultFormat,
    emptyText: String = "Belum ada data",
) {
    if (values.isEmpty() || values.all { it <= 0.0 }) {
        ChartPlaceholder(emptyText, modifier, height)
        return
    }
    val style = chartStyle()
    val measurer = rememberTextMeasurer()
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val progress = rememberEntrance(values)
    val targetColor = MaterialTheme.colorScheme.tertiary

    Canvas(
        modifier.fillMaxWidth().height(height).pointerInput(values) {
            detectTapGestures { pos ->
                val plot = plotRect(Size(size.width.toFloat(), size.height.toFloat()), 40.dp.toPx(), 22.dp.toPx(), 28.dp.toPx())
                val slot = plot.width / values.size
                val i = ((pos.x - plot.left) / slot).toInt()
                selected = if (i in values.indices && selected != i) i else null
            }
        },
    ) {
        val plot = plotRect(size, 40.dp.toPx(), 22.dp.toPx(), 28.dp.toPx())
        val scale = niceScale(0.0, max(values.max(), target ?: 0.0) * 1.05)
        drawYAxis(scale, plot, measurer, style, valueFormat)
        val slot = plot.width / values.size
        val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
        fun yOf(v: Double) = plot.bottom - ((v - scale.min) / scale.range * plot.height).toFloat()
        values.forEachIndexed { i, v ->
            if (v <= 0) return@forEachIndexed
            val cx = plot.left + slot * i + slot / 2
            val top = plot.bottom - (plot.bottom - yOf(v)) * progress
            val over = target != null && overColor != null && v > target * overFactor
            val base = if (over) overColor!! else barColor
            val color = if (selected == null || selected == i) base else base.copy(alpha = 0.35f)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(color, color.copy(alpha = color.alpha * 0.7f)), top, plot.bottom),
                topLeft = Offset(cx - barW / 2, top),
                size = Size(barW, plot.bottom - top),
                cornerRadius = CornerRadius(min(barW / 2, 6.dp.toPx())),
            )
        }
        target?.let { drawTargetLine(yOf(it), plot, targetColor, targetLabel, measurer, style) }
        drawXLabels(labels, { plot.left + slot * it + slot / 2 }, plot, measurer, style)
        selected?.let { i ->
            val cx = plot.left + slot * i + slot / 2
            drawTooltip("${tooltipLabels.getOrElse(i) { "" }}: ${valueFormat(values[i])}", Offset(cx, yOf(values[i])), measurer, style)
        }
    }
}

// ------------------------------------------ Line chart -----------------------------------------

data class LineSeries(
    /** Nilai per indeks X; null = tidak ada data di titik itu. */
    val values: List<Double?>,
    val color: Color,
    val name: String,
    val dashed: Boolean = false,
    val fill: Boolean = false,
    val dots: Boolean = false,
    val strokeWidth: Dp = 2.5.dp,
)

/** Grafik garis halus (kurva) multi-seri dengan area gradasi, titik, target, dan tooltip. */
@Composable
fun LineChart(
    series: List<LineSeries>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    target: Double? = null,
    targetLabel: String? = null,
    tooltipLabels: List<String> = labels,
    valueFormat: (Double) -> String = ::defaultFormat,
    includeZero: Boolean = false,
    emptyText: String = "Belum ada data",
) {
    val all = series.flatMap { s -> s.values.filterNotNull() }
    val n = series.maxOfOrNull { it.values.size } ?: 0
    if (all.isEmpty() || n < 2) {
        ChartPlaceholder(if (all.isEmpty()) emptyText else "Butuh minimal 2 data untuk grafik", modifier, height)
        return
    }
    val style = chartStyle()
    val measurer = rememberTextMeasurer()
    var selected by remember(series) { mutableStateOf<Int?>(null) }
    val progress = rememberEntrance(series)
    val targetColor = MaterialTheme.colorScheme.tertiary

    Canvas(
        modifier.fillMaxWidth().height(height).pointerInput(series) {
            detectTapGestures { pos ->
                val plot = plotRect(Size(size.width.toFloat(), size.height.toFloat()), 40.dp.toPx(), 22.dp.toPx(), 28.dp.toPx())
                val step = plot.width / (n - 1)
                val i = ((pos.x - plot.left) / step).roundToInt().coerceIn(0, n - 1)
                selected = if (selected != i) i else null
            }
        },
    ) {
        val plot = plotRect(size, 40.dp.toPx(), 22.dp.toPx(), 28.dp.toPx())
        val lo = min(all.min(), target ?: all.min())
        val hi = max(all.max(), target ?: all.max())
        val pad = max((hi - lo) * 0.12, if (includeZero) 0.0 else 0.5)
        val scale = niceScale(lo - if (includeZero) 0.0 else pad, hi + pad, includeZero = includeZero)
        drawYAxis(scale, plot, measurer, style, valueFormat)
        val step = plot.width / (n - 1)
        fun xOf(i: Int) = plot.left + step * i
        fun yOf(v: Double) = plot.bottom - ((v - scale.min) / scale.range * plot.height).toFloat()

        // Batasi gambar sesuai animasi masuk (kiri → kanan).
        val visibleRight = plot.left + plot.width * progress

        series.forEach { s ->
            val pts = s.values.mapIndexedNotNull { i, v -> v?.let { Offset(xOf(i), yOf(it)) } }
            if (pts.isEmpty()) return@forEach
            val path = smoothPath(pts)
            clipRectSafe(plot.left - 4f, 0f, visibleRight + 4f, size.height) {
                if (s.fill && pts.size >= 2) {
                    val area = Path().apply {
                        addPath(path)
                        lineTo(pts.last().x, plot.bottom)
                        lineTo(pts.first().x, plot.bottom)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.28f), s.color.copy(alpha = 0.02f)), plot.top, plot.bottom))
                }
                if (pts.size >= 2) {
                    drawPath(
                        path,
                        s.color,
                        style = Stroke(
                            width = s.strokeWidth.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 9f)) else null,
                        ),
                    )
                }
                if (s.dots || pts.size == 1) {
                    pts.forEach {
                        drawCircle(s.color, 4.dp.toPx(), it)
                        drawCircle(Color.White.copy(alpha = 0.9f), 1.8.dp.toPx(), it)
                    }
                }
            }
        }
        target?.let { drawTargetLine(yOf(it), plot, targetColor, targetLabel, measurer, style) }
        drawXLabels(labels, ::xOf, plot, measurer, style)
        selected?.let { i ->
            val x = xOf(i)
            drawLine(style.grid, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1.5f)
            val parts = series.mapNotNull { s -> s.values.getOrNull(i)?.let { "${s.name} ${valueFormat(it)}" } }
            val anchorY = series.mapNotNull { s -> s.values.getOrNull(i)?.let(::yOf) }.minOrNull() ?: plot.top
            series.forEach { s -> s.values.getOrNull(i)?.let { drawCircle(s.color, 5.dp.toPx(), Offset(x, yOf(it))) } }
            if (parts.isNotEmpty()) {
                drawTooltip("${tooltipLabels.getOrElse(i) { "" }} · ${parts.joinToString(" · ")}", Offset(x, anchorY), measurer, style)
            }
        }
    }
}

private inline fun DrawScope.clipRectSafe(left: Float, top: Float, right: Float, bottom: Float, block: DrawScope.() -> Unit) {
    if (right <= left) return
    drawContext.canvas.save()
    drawContext.canvas.clipRect(left, top, right, bottom)
    block()
    drawContext.canvas.restore()
}

/** Kurva halus melewati semua titik (monotone-ish cubic). */
private fun smoothPath(pts: List<Offset>): Path = Path().apply {
    if (pts.isEmpty()) return@apply
    moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) {
        val p0 = pts[i - 1]
        val p1 = pts[i]
        val midX = (p0.x + p1.x) / 2
        cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
    }
}

// --------------------------------------- Stacked bar chart -------------------------------------

/** Batang bertumpuk (mis. kalori dari protein/karbo/lemak per hari). */
@Composable
fun StackedBarChart(
    stacks: List<List<Double>>,
    colors: List<Color>,
    names: List<String>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    tooltipLabels: List<String> = labels,
    valueFormat: (Double) -> String = ::defaultFormat,
    emptyText: String = "Belum ada data",
) {
    val totals = stacks.map { it.sum() }
    if (totals.isEmpty() || totals.all { it <= 0 }) {
        ChartPlaceholder(emptyText, modifier, height)
        return
    }
    val style = chartStyle()
    val measurer = rememberTextMeasurer()
    var selected by remember(stacks) { mutableStateOf<Int?>(null) }
    val progress = rememberEntrance(stacks)
    val gap = MaterialTheme.colorScheme.surfaceContainerLow

    Canvas(
        modifier.fillMaxWidth().height(height).pointerInput(stacks) {
            detectTapGestures { pos ->
                val plot = plotRect(Size(size.width.toFloat(), size.height.toFloat()), 40.dp.toPx(), 22.dp.toPx(), 28.dp.toPx())
                val slot = plot.width / stacks.size
                val i = ((pos.x - plot.left) / slot).toInt()
                selected = if (i in stacks.indices && selected != i) i else null
            }
        },
    ) {
        val plot = plotRect(size, 40.dp.toPx(), 22.dp.toPx(), 28.dp.toPx())
        val scale = niceScale(0.0, totals.max() * 1.05)
        drawYAxis(scale, plot, measurer, style, valueFormat)
        val slot = plot.width / stacks.size
        val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
        stacks.forEachIndexed { i, parts ->
            var bottom = plot.bottom
            val cx = plot.left + slot * i + slot / 2
            parts.forEachIndexed { k, v ->
                if (v <= 0) return@forEachIndexed
                val h = (v / scale.range * plot.height).toFloat() * progress
                val c = colors[k % colors.size].let { if (selected == null || selected == i) it else it.copy(alpha = 0.35f) }
                drawRect(c, Offset(cx - barW / 2, bottom - h), Size(barW, h))
                drawLine(gap, Offset(cx - barW / 2, bottom - h), Offset(cx + barW / 2, bottom - h), 1.5f)
                bottom -= h
            }
        }
        drawXLabels(labels, { plot.left + slot * it + slot / 2 }, plot, measurer, style)
        selected?.let { i ->
            val cx = plot.left + slot * i + slot / 2
            val top = plot.bottom - (totals[i] / scale.range * plot.height).toFloat()
            val detail = stacks[i].mapIndexed { k, v -> "${names.getOrElse(k) { "" }} ${valueFormat(v)}" }.joinToString(" · ")
            drawTooltip("${tooltipLabels.getOrElse(i) { "" }}: $detail", Offset(cx, top), measurer, style)
        }
    }
}

// -------------------------------------- Diverging bar chart ------------------------------------

/** Batang ke atas/bawah dari garis nol (mis. surplus/defisit kalori harian). */
@Composable
fun DivergingBarChart(
    values: List<Double?>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    positiveColor: Color = MaterialTheme.colorScheme.error,
    negativeColor: Color = MaterialTheme.colorScheme.primary,
    tooltipLabels: List<String> = labels,
    valueFormat: (Double) -> String = { (if (it > 0) "+" else "") + defaultFormat(it) },
    emptyText: String = "Belum ada data",
) {
    val present = values.filterNotNull()
    if (present.isEmpty()) {
        ChartPlaceholder(emptyText, modifier, height)
        return
    }
    val style = chartStyle()
    val measurer = rememberTextMeasurer()
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val progress = rememberEntrance(values)

    Canvas(
        modifier.fillMaxWidth().height(height).pointerInput(values) {
            detectTapGestures { pos ->
                val plot = plotRect(Size(size.width.toFloat(), size.height.toFloat()), 44.dp.toPx(), 22.dp.toPx(), 28.dp.toPx())
                val slot = plot.width / values.size
                val i = ((pos.x - plot.left) / slot).toInt()
                selected = if (i in values.indices && values[i] != null && selected != i) i else null
            }
        },
    ) {
        val plot = plotRect(size, 44.dp.toPx(), 22.dp.toPx(), 28.dp.toPx())
        val m = max(abs(present.min()), abs(present.max())).coerceAtLeast(100.0) * 1.1
        val scale = niceScale(-m, m, ticks = 4)
        drawYAxis(scale, plot, measurer, style) { (if (it > 0) "+" else "") + defaultFormat(it) }
        fun yOf(v: Double) = plot.bottom - ((v - scale.min) / scale.range * plot.height).toFloat()
        val zero = yOf(0.0)
        drawLine(style.text.copy(alpha = 0.6f), Offset(plot.left, zero), Offset(plot.right, zero), 1.5f)
        val slot = plot.width / values.size
        val barW = (slot * 0.62f).coerceAtMost(26.dp.toPx())
        values.forEachIndexed { i, v ->
            if (v == null || v == 0.0) return@forEachIndexed
            val cx = plot.left + slot * i + slot / 2
            val y = zero + (yOf(v) - zero) * progress
            val base = if (v > 0) positiveColor else negativeColor
            val c = if (selected == null || selected == i) base else base.copy(alpha = 0.35f)
            drawRoundRect(c, Offset(cx - barW / 2, min(y, zero)), Size(barW, abs(zero - y)), CornerRadius(4.dp.toPx()))
        }
        drawXLabels(labels, { plot.left + slot * it + slot / 2 }, plot, measurer, style)
        selected?.let { i ->
            val v = values[i] ?: return@let
            drawTooltip("${tooltipLabels.getOrElse(i) { "" }}: ${valueFormat(v)}", Offset(plot.left + slot * i + slot / 2, min(yOf(v), zero)), measurer, style)
        }
    }
}

// ------------------------------------------ Donut chart ----------------------------------------

data class DonutSlice(val label: String, val value: Double, val color: Color)

/** Donut di tengah, legenda rapi di bawahnya (tidak berdesakan di layar sempit). */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerTitle: String,
    centerSubtitle: String,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    showLegend: Boolean = true,
    legendValue: (DonutSlice, Double) -> String = { _, pct -> "${pct.roundToInt()}%" },
) {
    val total = slices.sumOf { it.value }
    val progress = rememberEntrance(slices)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val stroke = this.size.minDimension * 0.15f
                val inset = stroke / 2
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                if (total > 0) {
                    var start = -90f
                    slices.forEach { s ->
                        val sweep = (s.value / total * 360f).toFloat() * progress
                        if (sweep > 0.5f) {
                            drawArc(s.color, start + 1f, (sweep - 2f).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                        }
                        start += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerTitle, style = MaterialTheme.typography.titleLarge)
                Text(centerSubtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showLegend) {
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                slices.forEach { s ->
                    val pct = if (total > 0) s.value / total * 100 else 0.0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                        Spacer(Modifier.width(10.dp))
                        Text(s.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(legendValue(s, pct), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }
}

// --------------------------------------------- Misc --------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartLegend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier, dashedLast: Boolean = false) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { i, (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dashedLast && i == items.lastIndex) {
                    Canvas(Modifier.width(16.dp).height(10.dp)) {
                        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                    }
                } else {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                }
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChartPlaceholder(text: String, modifier: Modifier, height: Dp) {
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

// Kompatibilitas: dipakai layar lama.

@Composable
fun CalorieTrendChart(values: List<Double>, labels: List<String>, target: Int?, modifier: Modifier = Modifier, height: Dp = 220.dp) {
    BarChart(
        values = values,
        labels = labels,
        modifier = modifier,
        height = height,
        target = target?.toDouble(),
        targetLabel = target?.let { "Target" },
        emptyText = "Belum ada data kalori di periode ini",
    )
}

@Composable
fun LineTrendChart(values: List<Double>, labels: List<String>, modifier: Modifier = Modifier, height: Dp = 200.dp, emptyText: String = "Belum ada data") {
    LineChart(
        series = listOf(LineSeries(values, MaterialTheme.colorScheme.primary, "", fill = true, dots = true)),
        labels = labels,
        modifier = modifier,
        height = height,
        emptyText = emptyText,
    )
}
