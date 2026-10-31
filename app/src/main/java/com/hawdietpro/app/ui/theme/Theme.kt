package com.hawdietpro.app.ui.theme

import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import com.hawdietpro.app.ui.components.PressIndication
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.hawdietpro.app.data.prefs.AccentColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Emerald600,
    onPrimary = White,
    primaryContainer = Emerald100,
    onPrimaryContainer = Emerald900,
    inversePrimary = Emerald300,
    secondary = Orange500,
    onSecondary = White,
    secondaryContainer = Orange100,
    onSecondaryContainer = Orange900,
    tertiary = Indigo600,
    onTertiary = White,
    tertiaryContainer = Indigo100,
    onTertiaryContainer = Indigo900,
    error = Red600,
    onError = White,
    errorContainer = Red100,
    onErrorContainer = Red900,
    background = Paper,
    onBackground = Ink900,
    surface = Paper,
    onSurface = Ink900,
    surfaceVariant = Color(0xFFE9EEEB),
    onSurfaceVariant = Ink500,
    surfaceTint = Emerald600,
    surfaceContainerLowest = White,
    surfaceContainerLow = White,
    surfaceContainer = Color(0xFFF0F3F1),
    surfaceContainerHigh = Color(0xFFEAEFEC),
    surfaceContainerHighest = Color(0xFFE3E9E5),
    surfaceBright = White,
    surfaceDim = Color(0xFFDCE2DE),
    outline = Ink300,
    outlineVariant = Line,
    inverseSurface = Ink900,
    inverseOnSurface = Color(0xFFEFF3F1),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Emerald300,
    onPrimary = Emerald900,
    primaryContainer = Emerald800,
    onPrimaryContainer = Emerald100,
    inversePrimary = Emerald600,
    secondary = Orange300,
    onSecondary = Orange900,
    secondaryContainer = Color(0xFF5A2D06),
    onSecondaryContainer = Orange100,
    tertiary = Indigo300,
    onTertiary = Indigo900,
    tertiaryContainer = Color(0xFF2B3487),
    onTertiaryContainer = Indigo100,
    error = Red300,
    onError = Red900,
    errorContainer = Color(0xFF6B1712),
    onErrorContainer = Red100,
    background = NightBg,
    onBackground = NightInk,
    surface = NightBg,
    onSurface = NightInk,
    surfaceVariant = NightCardHigh,
    onSurfaceVariant = NightInkSub,
    surfaceTint = Emerald300,
    surfaceContainerLowest = Color(0xFF090C0B),
    surfaceContainerLow = NightCard,
    surfaceContainer = Color(0xFF19201D),
    surfaceContainerHigh = NightCardHigh,
    surfaceContainerHighest = Color(0xFF262E2B),
    surfaceBright = Color(0xFF2C3431),
    surfaceDim = NightBg,
    outline = Color(0xFF5E6863),
    outlineVariant = NightLine,
    inverseSurface = NightInk,
    inverseOnSurface = Ink900,
    scrim = Color(0xFF000000),
)

/** Token tambahan di luar skema Material (gradasi brand, kartu, dsb.). */
@Immutable
data class HawExtendedColors(
    val heroGradient: Brush,
    val heroContent: Color,
    val heroContentSub: Color,
    val heroTrack: Color,
    val card: Color,
    val cardBorder: Color,
    val success: Color,
    val warning: Color,
    /** Kilau lembut di belakang hero & bagian atas layar. */
    val glow: Color = Color.Transparent,
    /** Sorotan tipis di tepi atas kartu (efek kaca halus). */
    val cardHighlight: Color = Color.Transparent,
    /** Kartu sedikit lebih terang (bento tile sekunder). */
    val cardRaised: Color = card,
    /** Aksen olahraga & makanan. */
    val exercise: Color = Color(0xFF7C8CFF),
    val food: Color = Color(0xFFFF8A4C),
    /** Warna akhir gradasi aksen (tombol +). */
    val accentEnd: Color = Teal500,
)

private val LightExtended = HawExtendedColors(
    heroGradient = Brush.linearGradient(listOf(Color(0xFF0B7A55), Emerald600, Teal500)),
    heroContent = White,
    heroContentSub = White.copy(alpha = 0.80f),
    heroTrack = White.copy(alpha = 0.22f),
    card = White,
    cardBorder = Line,
    success = Color(0xFF16A34A),
    warning = Color(0xFFEA8A0C),
    glow = Emerald300.copy(alpha = 0.28f),
    cardHighlight = Color.Transparent,
    cardRaised = Color(0xFFF7FAF8),
    exercise = Indigo600,
    food = Orange500,
)

private val DarkExtended = HawExtendedColors(
    heroGradient = Brush.linearGradient(listOf(Color(0xFF07291F), Color(0xFF0B4A37), Color(0xFF0E6B57))),
    heroContent = Color(0xFFF1FBF6),
    heroContentSub = Color(0xFFF1FBF6).copy(alpha = 0.70f),
    heroTrack = White.copy(alpha = 0.12f),
    card = NightCard,
    cardBorder = NightLine,
    success = Color(0xFF4ADE80),
    warning = Color(0xFFFBBF24),
    glow = Color(0xFF1DB386).copy(alpha = 0.22f),
    cardHighlight = White.copy(alpha = 0.05f),
    cardRaised = NightCardHigh,
    exercise = Color(0xFF8E9BFF),
    food = Color(0xFFFF9A62),
)

val LocalHawColors = staticCompositionLocalOf { LightExtended }

object HawTheme {
    val colors: HawExtendedColors
        @Composable get() = LocalHawColors.current
}

private val HawShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Warna akhir gradasi aksen (tombol +, ujung busur), diturunkan dari warna utama. */
private fun accentEnd(primary: Color, emerald: Boolean): Color = if (emerald) Teal500 else lerp(primary, Color.White, 0.22f)

/** Ganti keluarga warna utama skema dengan aksen pilihan. */
private fun ColorScheme.withPrimary(primary: Color, dark: Boolean): ColorScheme = copy(
    primary = primary,
    onPrimary = if (dark) lerp(primary, Color.Black, 0.82f) else Color.White,
    primaryContainer = if (dark) lerp(primary, Color.Black, 0.65f) else lerp(primary, Color.White, 0.85f),
    onPrimaryContainer = if (dark) lerp(primary, Color.White, 0.8f) else lerp(primary, Color.Black, 0.6f),
    inversePrimary = if (dark) lerp(primary, Color.Black, 0.3f) else lerp(primary, Color.White, 0.4f),
    surfaceTint = primary,
)

/** Latar hitam pekat (AMOLED). */
private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF101010),
    surfaceContainerHigh = Color(0xFF161616),
    surfaceContainerHighest = Color(0xFF1D1D1D),
    surfaceVariant = Color(0xFF161616),
)

/** Token tambahan yang mengikuti warna utama (untuk aksen selain hijau & warna wallpaper). */
private fun extendedFor(scheme: ColorScheme, dark: Boolean, base: HawExtendedColors, emerald: Boolean, amoled: Boolean): HawExtendedColors {
    val p = scheme.primary
    var ext = if (emerald) base else base.copy(
        heroGradient = if (dark) {
            Brush.linearGradient(listOf(lerp(p, Color.Black, 0.86f), lerp(p, Color.Black, 0.72f), lerp(p, Color.Black, 0.58f)))
        } else {
            Brush.linearGradient(listOf(lerp(p, Color.Black, 0.18f), p, lerp(p, Color.White, 0.18f)))
        },
        glow = p.copy(alpha = if (dark) 0.2f else 0.26f),
    )
    ext = ext.copy(accentEnd = accentEnd(p, emerald))
    if (dark && amoled) {
        ext = ext.copy(card = Color(0xFF0B0B0B), cardRaised = Color(0xFF141414), cardBorder = Color(0xFF1E1E1E))
    }
    return ext
}

/**
 * Tema aplikasi. [darkTheme] null = ikuti sistem. [accent] = warna utama pilihan,
 * [dynamicColor] = warna dari wallpaper (Android 12+), [amoled] = latar hitam pekat di mode gelap.
 */
@Composable
fun HawDietTheme(
    darkTheme: Boolean? = null,
    accent: AccentColor = AccentColor.EMERALD,
    amoled: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val useDark = darkTheme ?: isSystemInDarkTheme()
    val context = LocalContext.current
    val dynamicScheme: ColorScheme? = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null
    val emerald = dynamicScheme == null && accent == AccentColor.EMERALD
    var scheme = when {
        dynamicScheme != null -> dynamicScheme
        emerald -> if (useDark) DarkColors else LightColors
        else -> (if (useDark) DarkColors else LightColors).withPrimary(Color(if (useDark) accent.dark else accent.light), useDark)
    }
    if (useDark && amoled) scheme = scheme.amoled()
    val extended = extendedFor(scheme, useDark, if (useDark) DarkExtended else LightExtended, emerald, amoled)
    CompositionLocalProvider(LocalHawColors provides extended) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HawTypography,
            shapes = HawShapes,
        ) {
            // Umpan balik tekan ala iOS (mengecil + memantul) untuk semua elemen yang bisa diketuk.
            CompositionLocalProvider(LocalIndication provides PressIndication, content = content)
        }
    }
}
