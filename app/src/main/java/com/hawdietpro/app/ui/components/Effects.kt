package com.hawdietpro.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Particle(val angle: Float, val speed: Float, val size: Float, val color: Color, val spin: Float, val wide: Boolean)

/**
 * Hujan confetti sekali jalan (±2,4 dtk). Ganti [key] untuk memicu ulang.
 * Tidak menangkap sentuhan sehingga aman ditaruh di atas konten.
 */
@Composable
fun ConfettiBurst(key: Any, modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    if (LocalReduceMotion.current) {
        LaunchedEffect(key) { onFinished() }
        return
    }
    val colors = listOf(Color(0xFF5FE0A8), Color(0xFFFFD27A), Color(0xFFFF9C8F), Color(0xFFA9C4FF), Color(0xFFFF92B8), Color.White)
    val particles = remember(key) {
        val rnd = Random(key.hashCode())
        List(90) {
            Particle(
                angle = (-PI / 2 + (rnd.nextFloat() - 0.5f) * PI * 0.9).toFloat(),
                speed = 0.55f + rnd.nextFloat() * 0.75f,
                size = 6f + rnd.nextFloat() * 8f,
                color = colors[rnd.nextInt(colors.size)],
                spin = (rnd.nextFloat() - 0.5f) * 1440f,
                wide = rnd.nextBoolean(),
            )
        }
    }
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        t.snapTo(0f)
        t.animateTo(1f, tween(2400, easing = LinearEasing))
        onFinished()
    }
    if (t.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val origin = Offset(size.width / 2, size.height * 0.62f)
        val p = t.value
        particles.forEach { pt ->
            // Lintasan peluru sederhana: meluncur ke atas lalu jatuh karena gravitasi.
            val v = pt.speed * size.height * 1.15f
            val x = origin.x + cos(pt.angle) * v * p
            val y = origin.y + sin(pt.angle) * v * p + 0.5f * size.height * 1.6f * p * p
            val alpha = (1f - ((p - 0.7f) / 0.3f).coerceIn(0f, 1f))
            rotate(pt.spin * p, Offset(x, y)) {
                drawRect(
                    pt.color.copy(alpha = alpha),
                    topLeft = Offset(x - pt.size / 2, y - pt.size / 4),
                    size = if (pt.wide) Size(pt.size, pt.size / 2) else Size(pt.size / 2, pt.size),
                )
            }
        }
    }
}

/** Lingkaran centang beranimasi (umpan balik "tersimpan"). */
@Composable
fun SuccessCheck(key: Any, message: String, modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    val circle = remember(key) { Animatable(0f) }
    val check = remember(key) { Animatable(0f) }
    val fade = remember(key) { Animatable(1f) }
    LaunchedEffect(key) {
        circle.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        check.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
        kotlinx.coroutines.delay(650)
        fade.animateTo(0f, tween(250))
        onFinished()
    }
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Box(modifier.fillMaxSize().graphicsLayer { alpha = fade.value }, contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f))
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(84.dp).graphicsLayer { scaleX = 0.6f + 0.4f * circle.value; scaleY = 0.6f + 0.4f * circle.value }) {
                drawCircle(primary, radius = size.minDimension / 2 * circle.value)
                val path = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.67f)
                    lineTo(size.width * 0.73f, size.height * 0.36f)
                }
                if (check.value > 0f) {
                    // Gambar centang sepanjang progres animasi.
                    val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
                    val partial = Path()
                    measure.getSegment(0f, measure.length * check.value, partial, true)
                    drawPath(partial, onPrimary, style = Stroke(width = size.width * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Kilau berjalan untuk kerangka loading. */
@Composable
private fun shimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    if (LocalReduceMotion.current) return Brush.linearGradient(listOf(base, base))
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(-400f, 1400f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "shimmerX")
    return Brush.linearGradient(listOf(base, highlight, base), start = Offset(x, 0f), end = Offset(x + 400f, 300f))
}

@Composable
private fun SkeletonBlock(modifier: Modifier, brush: Brush, corner: Int = 20) {
    Box(modifier.clip(RoundedCornerShape(corner.dp)).background(brush))
}

/** Kerangka halaman (header, kartu besar, baris kartu) selagi data dimuat. */
@Composable
fun SkeletonScreen(modifier: Modifier = Modifier, statusBarInset: Boolean = true) {
    val brush = shimmerBrush()
    Column(
        modifier.fillMaxSize().then(if (statusBarInset) Modifier.statusBarsPadding() else Modifier).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SkeletonBlock(Modifier.width(180.dp).height(18.dp), brush, 9)
        SkeletonBlock(Modifier.width(240.dp).height(30.dp), brush, 12)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(6) { SkeletonBlock(Modifier.size(width = 50.dp, height = 70.dp), brush, 18) }
        }
        SkeletonBlock(Modifier.fillMaxWidth().height(300.dp), brush, 32)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) { SkeletonBlock(Modifier.weight(1f).height(80.dp), brush) }
        }
        repeat(2) { SkeletonBlock(Modifier.fillMaxWidth().height(96.dp), brush) }
    }
}

