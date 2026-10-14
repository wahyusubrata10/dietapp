package com.hawdietpro.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hawdietpro.app.ui.theme.HawTheme

/** Warna tombol segmen yang konsisten: terpilih = hijau utama (bukan warna sekunder). */
@Composable
fun hawSegmentedColors(): SegmentedButtonColors {
    val cs = MaterialTheme.colorScheme
    return SegmentedButtonDefaults.colors(
        activeContainerColor = cs.primary.copy(alpha = 0.16f),
        activeContentColor = cs.primary,
        activeBorderColor = cs.primary.copy(alpha = 0.6f),
        inactiveContainerColor = HawTheme.colors.card,
        inactiveContentColor = cs.onSurfaceVariant,
        inactiveBorderColor = HawTheme.colors.cardBorder,
    )
}

/** Baris tile statistik dengan tinggi seragam (tile tertinggi menentukan tinggi semua). */
@Composable
fun StatRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
