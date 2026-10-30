package com.hawdietpro.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.hawdietpro.app.domain.nutrition.DayStatus

/** Warna konsisten untuk status hari (kalender heatmap, rekap, legenda). */
@Composable
fun DayStatus.color(): Color = when (this) {
    DayStatus.KOSONG -> MaterialTheme.colorScheme.surfaceContainerHighest
    DayStatus.KURANG -> MaterialTheme.colorScheme.tertiary
    DayStatus.SESUAI -> MaterialTheme.colorScheme.primary
    DayStatus.LEBIH -> MaterialTheme.colorScheme.error
}

/** Warna teks di atas sel berwarna status. */
@Composable
fun DayStatus.onColor(): Color = when (this) {
    DayStatus.KOSONG -> MaterialTheme.colorScheme.onSurface
    DayStatus.KURANG -> MaterialTheme.colorScheme.onTertiary
    DayStatus.SESUAI -> MaterialTheme.colorScheme.onPrimary
    DayStatus.LEBIH -> MaterialTheme.colorScheme.onError
}
