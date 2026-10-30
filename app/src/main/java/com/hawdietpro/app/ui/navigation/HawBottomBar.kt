package com.hawdietpro.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import com.hawdietpro.app.ui.components.BentoCard
import com.hawdietpro.app.ui.components.IconTile
import com.hawdietpro.app.ui.components.bouncyClick
import com.hawdietpro.app.ui.theme.Teal500
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
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
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

/** Navigasi bawah mengambang: Beranda · Riwayat · [ + ] · Analisa · Profil. */
@Composable
fun HawBottomBar(currentRoute: (String) -> Boolean, onNavigate: (String) -> Unit, onAdd: () -> Unit) {
    val hc = HawTheme.colors
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, cs.background.copy(alpha = 0.92f), cs.background)))
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
    ) {
        Surface(
            color = hc.card,
            shape = RoundedCornerShape(30.dp),
            shadowElevation = 14.dp,
            border = BorderStroke(1.dp, hc.cardBorder),
            modifier = Modifier.fillMaxWidth().height(66.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val items = TopLevelDestination.entries
                items.take(2).forEach { BarItem(it, currentRoute(it.route), onNavigate, Modifier.weight(1f)) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(cs.primary, hc.accentEnd)))
                            .bouncyClick(onClick = onAdd),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Catat makanan", tint = cs.onPrimary, modifier = Modifier.size(28.dp))
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
    val indicator by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "navInd")
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onNavigate(dest.route) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 52.dp, height = 28.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(width = 52.dp * indicator, height = 28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            )
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

enum class QuickAction { CAMERA, GALLERY, BARCODE, RELOG, MANUAL, EXERCISE, WEIGHT }

/** Lembar aksi cepat dari tombol "+". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(onDismiss: () -> Unit, onAction: (QuickAction) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        QuickAddContent(onAction)
    }
}

@Composable
fun QuickAddContent(onAction: (QuickAction) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hc = HawTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Catat sekarang", style = MaterialTheme.typography.headlineSmall)
        Text("Pilih cara tercepat untukmu", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        // Aksi utama: foto (paling sering dipakai).
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(hc.heroGradient)
                .bouncyClick { onAction(QuickAction.CAMERA) }
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CameraAlt, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Foto makanan", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("AI mengenali menu & menghitung gizi", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White)
        }
        val tiles = listOf(
            Triple(QuickAction.GALLERY, Icons.Filled.PhotoLibrary, Triple("Galeri", "Analisa foto lama", cs.tertiary)),
            Triple(QuickAction.BARCODE, Icons.Filled.QrCodeScanner, Triple("Scan barcode", "Produk kemasan", hc.food)),
            Triple(QuickAction.RELOG, Icons.Filled.Replay, Triple("Makan lagi", "Favorit & terakhir", cs.primary)),
            Triple(QuickAction.MANUAL, Icons.Filled.EditNote, Triple("Manual", "Cari ±3.500 makanan", MacroColors.Carbs)),
            Triple(QuickAction.EXERCISE, Icons.AutoMirrored.Filled.DirectionsRun, Triple("Olahraga", "Tambah jatah makan", hc.exercise)),
            Triple(QuickAction.WEIGHT, Icons.Filled.MonitorWeight, Triple("Berat badan", "Timbang & tren", MacroColors.Protein)),
        )
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (action, icon, info) ->
                    val (title, subtitle, tint) = info
                    BentoCard(Modifier.weight(1f), onClick = { onAction(action) }, padding = 14.dp, corner = 22.dp) {
                        IconTile(icon, tint, size = 40.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}
