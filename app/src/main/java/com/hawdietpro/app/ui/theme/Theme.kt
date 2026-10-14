package com.hawdietpro.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = SurfaceLight,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Orange40,
    onSecondary = SurfaceLight,
    secondaryContainer = Orange90,
    onSecondaryContainer = Orange10,
    tertiary = Indigo40,
    onTertiary = SurfaceLight,
    tertiaryContainer = Indigo90,
    onTertiaryContainer = Indigo10,
    error = Red40,
    background = NeutralLight,
    onBackground = OnSurfaceLight,
    surface = NeutralLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFF0F5F1),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFEAF0EB),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFE4EAE5),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFDEE4DF),
    outline = OutlineLight,
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Orange80,
    onSecondary = Orange10,
    secondaryContainer = Orange30,
    onSecondaryContainer = Orange90,
    tertiary = Indigo80,
    onTertiary = Indigo10,
    tertiaryContainer = Indigo30,
    onTertiaryContainer = Indigo90,
    error = Red80,
    background = NeutralDark,
    onBackground = OnSurfaceDark,
    surface = NeutralDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF0A0F0C),
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF1B211E),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF252B28),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF303633),
    outline = OutlineDark,
)

private val HawShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** [darkTheme] null = ikuti sistem. */
@Composable
fun HawDietTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val useDark = darkTheme ?: isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = HawTypography,
        shapes = HawShapes,
        content = content,
    )
}
