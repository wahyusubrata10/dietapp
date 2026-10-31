package com.hawdietpro.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * true bila pengguna mematikan animasi di Android (Opsi pengembang / Aksesibilitas → hapus animasi).
 * Efek dekoratif (intro berjenjang, confetti, kilau, pemindai, pantulan tekan) lalu dilewati.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Membaca skala animasi sistem; 0 = animasi dimatikan. */
fun systemReducesMotion(context: android.content.Context): Boolean = runCatching {
    android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

/** Kurva gerak bergaya iOS: cepat di awal, mendarat sangat halus. */
val SmoothEasing = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)

/** Durasi standar transisi layar (ms). */
const val NAV_DURATION = 380

/**
 * Animasi masuk berjenjang hanya diputar saat layar pertama kali tampil.
 * Item yang muncul lagi karena di-scroll tidak dianimasikan ulang (penyebab scroll terasa patah).
 */
val LocalIntroActive = compositionLocalOf { false }

@Composable
fun IntroScope(durationMs: Long = 900, content: @Composable () -> Unit) {
    // Disimpan (saveable) agar kembali ke layar ini tidak memutar intro lagi.
    var done by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(done) {
        if (!done) {
            delay(durationMs)
            done = true
        }
    }
    CompositionLocalProvider(LocalIntroActive provides (!done && !LocalReduceMotion.current), content = content)
}

/**
 * Umpan balik sentuh global: konten sedikit mengecil & meredup saat ditekan, lalu memantul kembali
 * dengan pegas (menggantikan riak Material untuk semua `Modifier.clickable`).
 */
object PressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressIndicationNode(interactionSource)
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = javaClass.hashCode()
}

private class PressIndicationNode(private val interactionSource: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val scale = Animatable(1f)
    private val dim = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            var pressJob: Job? = null
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        pressJob?.cancel()
                        pressJob = launch {
                            launch { scale.animateTo(0.965f, spring(stiffness = Spring.StiffnessHigh)) }
                            dim.animateTo(1f, tween(90))
                        }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        val pressing = pressJob
                        launch {
                            // Ketukan cepat tetap terlihat: tunggu fase tekan selesai dulu.
                            pressing?.join()
                            launch { scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
                            dim.animateTo(0f, tween(260))
                        }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val s = scale.value
        if (s == 1f && dim.value == 0f) {
            drawContent()
            return
        }
        scale(s, pivot = center) { this@draw.drawContent() }
        if (dim.value > 0f) drawRect(Color.Black.copy(alpha = 0.06f * dim.value))
    }
}

/**
 * Seperti `animateFloatAsState`, tetapi nilai terakhir yang tampil ikut disimpan. Saat layar dibuka
 * lagi (mis. kembali dari menyimpan makanan) angka & cincin bergerak dari nilai lama ke nilai baru,
 * bukan langsung melompat. Item yang sekadar di-scroll keluar-masuk tidak dianimasikan ulang.
 */
@Composable
fun animateFromLast(target: Float, durationMs: Int = 900): State<Float> {
    var last by rememberSaveable { mutableFloatStateOf(target) }
    val anim = remember { Animatable(last) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(target) {
        if (reduce) anim.snapTo(target) else anim.animateTo(target, tween(durationMs, easing = FastOutSlowInEasing))
        last = target
    }
    return anim.asState()
}

/** Transisi isi tab: geser ke arah tab yang dipilih + pudar (seperti segmented control iOS). */
fun tabTransition(from: Int, to: Int): androidx.compose.animation.ContentTransform {
    val dir = if (to >= from) 1 else -1
    return (
        androidx.compose.animation.slideInHorizontally(tween(320, easing = SmoothEasing)) { it / 6 * dir } +
            androidx.compose.animation.fadeIn(tween(260))
        ) togetherWith (
        androidx.compose.animation.slideOutHorizontally(tween(320, easing = SmoothEasing)) { -it / 6 * dir } +
            androidx.compose.animation.fadeOut(tween(160))
        )
}
