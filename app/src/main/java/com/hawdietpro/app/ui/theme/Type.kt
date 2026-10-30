package com.hawdietpro.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hawdietpro.app.R

/** Plus Jakarta Sans: font open-source (OFL) karya desainer Indonesia, nyaman & modern. */
val JakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, spacing: Double = 0.0) = TextStyle(
    fontFamily = JakartaSans,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
)

val HawTypography = Typography(
    displayLarge = style(52, 60, FontWeight.ExtraBold, -1.0),
    displayMedium = style(42, 50, FontWeight.ExtraBold, -0.8),
    displaySmall = style(34, 42, FontWeight.ExtraBold, -0.5),
    headlineLarge = style(30, 38, FontWeight.ExtraBold, -0.4),
    headlineMedium = style(26, 34, FontWeight.Bold, -0.3),
    headlineSmall = style(22, 30, FontWeight.Bold, -0.2),
    titleLarge = style(20, 28, FontWeight.Bold, -0.1),
    titleMedium = style(16, 24, FontWeight.Bold),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 21, FontWeight.Normal),
    bodySmall = style(12, 17, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.SemiBold),
    labelSmall = style(11, 15, FontWeight.Medium, 0.2),
)

/** Angka besar dengan digit lebar seragam (tabular) agar tidak "bergoyang" saat beranimasi. */
val NumberDisplay = TextStyle(
    fontFamily = JakartaSans,
    fontSize = 56.sp,
    lineHeight = 60.sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-1.5).sp,
    fontFeatureSettings = "tnum",
)

val NumberLarge = TextStyle(
    fontFamily = JakartaSans,
    fontSize = 26.sp,
    lineHeight = 30.sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.5).sp,
    fontFeatureSettings = "tnum",
)

val NumberMedium = TextStyle(
    fontFamily = JakartaSans,
    fontSize = 18.sp,
    lineHeight = 22.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.2).sp,
    fontFeatureSettings = "tnum",
)
