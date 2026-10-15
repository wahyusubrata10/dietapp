package com.hawdietpro.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hawdietpro.app.ui.components.IconBadge
import com.hawdietpro.app.ui.theme.HawTheme
import com.hawdietpro.app.ui.theme.MacroColors

/** Navigasi bawah: Beranda · Riwayat · [ + ] · Analisa · Profil. */
@Composable
fun HawBottomBar(currentRoute: (String) -> Boolean, onNavigate: (String) -> Unit, onAdd: () -> Unit) {
    Surface(
        color = HawTheme.colors.card,
        shadowElevation = 12.dp,
        tonalElevation = 0.dp,
    ) {
        Column {
            HorizontalDivider(color = HawTheme.colors.cardBorder)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().height(68.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val items = TopLevelDestination.entries
                items.take(2).forEach { BarItem(it, currentRoute(it.route), onNavigate, Modifier.weight(1f)) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Surface(
                        onClick = onAdd,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 8.dp,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, contentDescription = "Catat makanan", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
                        }
                    }
                }
                items.drop(2).forEach { BarItem(it, currentRoute(it.route), onNavigate, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BarItem(dest: TopLevelDestination, selected: Boolean, onNavigate: (String) -> Unit, modifier: Modifier) {
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "navColor",
    )
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onNavigate(dest.route) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 56.dp, height = 30.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(width = 56.dp, height = 30.dp)) {}
            }
            Icon(if (selected) dest.selectedIcon else dest.unselectedIcon, contentDescription = dest.label, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            dest.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = color,
        )
    }
}

enum class QuickAction { CAMERA, GALLERY, RELOG, MANUAL, WEIGHT }

/** Lembar aksi cepat dari tombol "+". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(onDismiss: () -> Unit, onAction: (QuickAction) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = HawTheme.colors.card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Catat sekarang", style = MaterialTheme.typography.titleLarge)
            Text("Pilih cara mencatat", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            ActionRow(Icons.Filled.CameraAlt, MaterialTheme.colorScheme.primary, "Foto makanan", "AI mengenali menu & menghitung gizi") { onAction(QuickAction.CAMERA) }
            ActionRow(Icons.Filled.PhotoLibrary, MaterialTheme.colorScheme.tertiary, "Pilih dari galeri", "Analisa foto yang sudah ada") { onAction(QuickAction.GALLERY) }
            ActionRow(Icons.Filled.Replay, MaterialTheme.colorScheme.secondary, "Makan lagi", "Favorit & menu terakhir, 1 ketukan") { onAction(QuickAction.RELOG) }
            ActionRow(Icons.Filled.EditNote, MacroColors.Carbs, "Input manual", "Cari dari ±290 makanan Indonesia") { onAction(QuickAction.MANUAL) }
            ActionRow(Icons.Filled.MonitorWeight, MacroColors.Protein, "Catat berat badan", "Timbang & lihat tren") { onAction(QuickAction.WEIGHT) }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
