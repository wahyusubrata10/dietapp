package com.hawdietpro.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.ui.theme.HawTheme

// ───────────────────────────── Interaksi ─────────────────────────────

/** Mengecil sedikit saat ditekan (umpan balik sentuh yang terasa "hidup"). */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.965f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Klik dengan efek tekan + getar halus. */
fun Modifier.bouncyClick(haptic: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val feedback = LocalHapticFeedback.current
    this
        .pressScale(interaction)
        .clickable(interactionSource = interaction, indication = null) {
            if (haptic) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Muncul perlahan dari bawah sekali saat pertama tampil (urutan [index] memberi efek berjenjang). */
fun Modifier.enterAnimation(index: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 420, delayMillis = (index * 45).coerceAtMost(360), easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 28.dp.toPx()
    }
}

// ───────────────────────────── Kartu ─────────────────────────────

/**
 * Kartu "bento": sudut besar, sorotan tipis di tepi atas (mode gelap), efek tekan bila bisa diklik.
 */
@Composable
fun BentoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color? = null,
    padding: Dp = 16.dp,
    corner: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hc = HawTheme.colors
    val shape = RoundedCornerShape(corner)
    val bg = color ?: hc.card
    val border = if (color == null) {
        BorderStroke(1.dp, Brush.verticalGradient(listOf(hc.cardHighlight.compositeOver(hc.cardBorder), hc.cardBorder)))
    } else null
    // Warna teks default mengikuti kartu (bukan induknya) agar selalu kontras.
    val contentColor = if (color == null) MaterialTheme.colorScheme.onSurface else androidx.compose.material3.LocalContentColor.current
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides contentColor) {
        Column(
            modifier
                .then(if (onClick != null) Modifier.bouncyClick(onClick = onClick) else Modifier)
                .clip(shape)
                .background(bg)
                .then(if (border != null) Modifier.border(border, shape) else Modifier)
                .padding(padding),
            content = content,
        )
    }
}

private fun Color.compositeOver(other: Color): Color {
    val a = alpha + other.alpha * (1 - alpha)
    if (a == 0f) return Color.Transparent
    return Color(
        red = (red * alpha + other.red * other.alpha * (1 - alpha)) / a,
        green = (green * alpha + other.green * other.alpha * (1 - alpha)) / a,
        blue = (blue * alpha + other.blue * other.alpha * (1 - alpha)) / a,
        alpha = a,
    )
}

/** Judul bagian dengan aksi opsional di kanan ("Lihat semua"). */
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (action != null && onAction != null) {
            Row(
                Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Chip status kecil berwarna. */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null, onColor: Boolean = false) {
    Row(
        modifier
            .clip(CircleShape)
            .background(if (onColor) Color.White.copy(alpha = 0.16f) else color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (onColor) Color.White else color
        icon?.let {
            Icon(it, null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

/** Pemberitahuan ringkas (update, antrian, API key...). */
@Composable
fun NoticeCard(
    title: String,
    text: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    BentoCard(modifier.fillMaxWidth(), onClick = onClick, padding = 12.dp, corner = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = accent, modifier = Modifier.size(21.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            when {
                trailing != null -> trailing()
                onClick != null -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ───────────────────────────── Angka & grafik mini ─────────────────────────────

/** Angka yang "menghitung" ke nilai baru (dengan pemisah ribuan). */
@Composable
fun AnimatedCount(value: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier, prefix: String = "", suffix: String = "") {
    val animated by animateIntAsState(value, tween(900, easing = FastOutSlowInEasing), label = "count")
    Text(prefix + Format.grouped(animated) + suffix, style = style, color = color, modifier = modifier, maxLines = 1)
}

/**
 * Gauge busur 240° (terbuka di bawah). [progress] = porsi terpakai (0..1+), [bonus] = porsi jatah
 * tambahan dari olahraga di ujung busur (digambar dengan warna berbeda).
 */
@Composable
fun ArcGauge(
    progress: Float,
    modifier: Modifier = Modifier,
    bonus: Float = 0f,
    size: Dp = 220.dp,
    stroke: Dp = 16.dp,
    track: Color,
    fill: Brush,
    bonusColor: Color,
    overColor: Color,
    content: @Composable () -> Unit,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing), label = "gauge")
    val over = progress > 1f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arc = Size(this.size.width - sw, this.size.height - sw)
            val tl = Offset(inset, inset)
            val start = 150f
            val sweep = 240f
            drawArc(track, start, sweep, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
            if (bonus > 0f) {
                // Jatah dari olahraga: ditandai di ujung busur.
                val b = bonus.coerceIn(0f, 1f)
                drawArc(bonusColor.copy(alpha = 0.55f), start + sweep * (1 - b), sweep * b, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
            }
            if (animated > 0f) {
                if (over) drawArc(overColor, start, sweep * animated, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
                else drawArc(fill, start, sweep * animated, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Cincin kecil untuk makro/progres. */
@Composable
fun MiniRing(fraction: Float, color: Color, modifier: Modifier = Modifier, size: Dp = 46.dp, stroke: Dp = 5.dp, track: Color = color.copy(alpha = 0.18f), content: @Composable () -> Unit = {}) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "mini")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val arc = Size(this.size.width - sw, this.size.height - sw)
            val tl = Offset(sw / 2, sw / 2)
            drawArc(track, -90f, 360f, false, tl, arc, style = Stroke(sw))
            if (animated > 0f) drawArc(color, -90f, 360f * animated, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Garis tren kecil dengan isian gradasi. */
@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier, strokeWidth: Dp = 2.5.dp) {
    if (values.size < 2) {
        Box(modifier)
        return
    }
    Canvas(modifier) {
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 1e-6 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        val pad = strokeWidth.toPx()
        fun y(v: Double) = (pad + (1 - (v - min) / range) * (size.height - pad * 2)).toFloat()
        val line = Path().apply {
            values.forEachIndexed { i, v -> if (i == 0) moveTo(0f, y(v)) else lineTo(i * stepX, y(v)) }
        }
        val fillPath = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fillPath, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round))
        drawCircle(color, radius = pad * 1.4f, center = Offset(size.width, y(values.last())))
    }
}

/** Batang progres tebal dengan sudut bulat & animasi. */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp, track: Color = color.copy(alpha = 0.16f)) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing), label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track)
            .drawBehind {
                if (animated > 0f) {
                    drawRoundRect(
                        color,
                        size = Size(size.width * animated, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                    )
                }
            },
    )
}

/** Ikon dalam kotak bulat berwarna lembut. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 44.dp, filled: Boolean = false) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(if (filled) tint else tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (filled) Color.White else tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Dua nilai label-angka bertumpuk (dipakai di hero & bento). */
@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface, labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant, valueStyle: TextStyle = MaterialTheme.typography.titleMedium) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = valueStyle, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Tab berbentuk pil di dalam wadah membulat; pil aktif bergeser dengan animasi.
 * Dipakai untuk tab layar (Analisa) maupun pilihan periode.
 */
@Composable
fun PillTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val hc = HawTheme.colors
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(hc.card)
            .border(1.dp, hc.cardBorder, CircleShape)
            .padding(4.dp),
    ) {
        val count = options.size.coerceAtLeast(1)
        val itemWidth = maxWidth / count
        val offset by androidx.compose.animation.core.animateDpAsState(
            itemWidth * selected,
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "pill",
        )
        val height = if (compact) 34.dp else 40.dp
        if (selected in options.indices) {
            Box(
                Modifier
                    .padding(start = offset)
                    .width(itemWidth)
                    .height(height)
                    .clip(CircleShape)
                    .background(cs.primary),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                val active = i == selected
                val color by androidx.compose.animation.animateColorAsState(if (active) cs.onPrimary else cs.onSurfaceVariant, label = "pillText")
                Box(
                    Modifier
                        .width(itemWidth)
                        .height(height)
                        .clip(CircleShape)
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Donat proporsi (mis. energi protein/karbo/lemak) dengan celah antar segmen dan isi di tengah. */
@Composable
fun SegmentDonut(
    segments: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    size: Dp = 124.dp,
    stroke: Dp = 14.dp,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable () -> Unit = {},
) {
    val total = segments.sumOf { it.first.toDouble() }.toFloat()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(total) { progress.snapTo(0f); progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val arc = Size(this.size.width - sw, this.size.height - sw)
            val tl = Offset(sw / 2, sw / 2)
            drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(sw))
            if (total > 0f) {
                val gap = if (segments.count { it.first > 0f } > 1) 6f else 0f
                var start = -90f
                segments.filter { it.first > 0f }.forEach { (v, c) ->
                    val sweep = 360f * v / total
                    drawArc(c, start + gap / 2, ((sweep - gap) * progress.value).coerceAtLeast(0.1f), false, tl, arc, style = Stroke(sw, cap = StrokeCap.Butt))
                    start += sweep
                }
            }
        }
        content()
    }
}
