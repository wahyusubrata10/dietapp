package com.hawdietpro.app.ui.feature.profile

import com.hawdietpro.app.ui.components.StatRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import com.hawdietpro.app.ui.components.IconBadge
import com.hawdietpro.app.ui.components.Pill
import com.hawdietpro.app.ui.theme.HawTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.BuildConfig
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.data.repository.PendingRepository
import com.hawdietpro.app.data.repository.ProfileRepository
import com.hawdietpro.app.data.update.UpdateRepository
import com.hawdietpro.app.data.update.UpdateState
import com.hawdietpro.app.domain.model.UserProfile
import com.hawdietpro.app.domain.nutrition.DailyTarget
import com.hawdietpro.app.domain.nutrition.TdeeCalculator
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.HawScaffold
import com.hawdietpro.app.ui.components.StatTile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ProfileUi(
    val profile: UserProfile? = null,
    val target: DailyTarget? = null,
    val pendingCount: Int = 0,
    val updateAvailable: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    profiles: ProfileRepository,
    pending: PendingRepository,
    updates: UpdateRepository,
) : ViewModel() {
    val state: StateFlow<ProfileUi> = combine(profiles.observeProfile(), pending.observe(), updates.state) { p, q, u ->
        ProfileUi(
            profile = p,
            target = p?.let(TdeeCalculator::calculate),
            pendingCount = q.size,
            updateAvailable = u is UpdateState.Available || u is UpdateState.ReadyToInstall || u is UpdateState.Downloading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUi())
}

@Composable
fun ProfileScreen(
    onEditProfile: () -> Unit,
    onSettings: () -> Unit,
    onWeight: () -> Unit,
    onQueue: () -> Unit,
    onUpdate: () -> Unit,
    onBackup: () -> Unit,
    onAchievements: () -> Unit,
    onRelog: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HawScaffold(title = "Profil", topLevel = true) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val p = state.profile
            val t = state.target
            if (p != null && t != null) {
                item { ProfileHero(p, t, onEditProfile) }
                item {
                    StatRow {
                        StatTile("Target", Format.grouped(t.calories), Modifier.weight(1f), sub = "kkal/hari", accent = MaterialTheme.colorScheme.primary)
                        StatTile("BMI", Format.oneDecimal(t.bmi), Modifier.weight(1f), sub = t.bmiCategory, accent = MaterialTheme.colorScheme.tertiary)
                        StatTile("TDEE", Format.grouped(t.tdee), Modifier.weight(1f), sub = "kkal", accent = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            item {
                MenuGroup("Data & kebiasaan") {
                    MenuItem(Icons.Filled.Edit, "Data diri & target", "Umur, tinggi, berat, aktivitas, tujuan", onEditProfile)
                    MenuItem(Icons.Filled.MonitorWeight, "Log berat badan", "Catat & lihat tren berat", onWeight)
                    MenuItem(Icons.Filled.EmojiEvents, "Pencapaian", "Badge kebiasaan & rekor streak", onAchievements)
                    MenuItem(Icons.Filled.Replay, "Makan lagi & favorit", "Catat ulang menu yang sering dimakan", onRelog)
                    MenuItem(
                        Icons.Filled.CloudQueue,
                        "Antrian analisa",
                        if (state.pendingCount > 0) "${state.pendingCount} foto menunggu" else "Tidak ada antrian",
                        onQueue,
                        badge = state.pendingCount.takeIf { it > 0 }?.toString(),
                    )
                }
            }
            item {
                MenuGroup("Aplikasi") {
                    MenuItem(Icons.Filled.Settings, "Pengaturan", "API key Gemini, model AI, tema", onSettings)
                    MenuItem(Icons.Filled.Backup, "Backup & ekspor", "ZIP (data + foto), CSV untuk Excel", onBackup)
                    MenuItem(
                        Icons.Filled.SystemUpdate,
                        "Update aplikasi",
                        "Versi ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                        onUpdate,
                        badge = if (state.updateAvailable) "Baru" else null,
                    )
                }
            }
            item {
                Text(
                    "Haw Diet Pro · gratis & offline-first. Data tersimpan hanya di HP ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
fun MenuItem(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit, badge: String? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { s -> @Composable { Text(s) } },
        leadingContent = { IconBadge(icon, MaterialTheme.colorScheme.primary, size = 38.dp) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                badge?.let {
                    Badge { Text(it) }
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ProfileHero(p: UserProfile, t: DailyTarget, onEdit: () -> Unit) {
    val hc = HawTheme.colors
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(hc.heroGradient).clickable(onClick = onEdit).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    p.name.trim().split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "🙂" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = hc.heroContent,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleLarge, color = hc.heroContent, maxLines = 1)
                Text(
                    "${p.sex.label} · ${p.age} th · ${Format.oneDecimal(p.heightCm)} cm · ${Format.kg(p.weightKg)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = hc.heroContentSub,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(p.goal.label, Color.White)
                    p.targetWeightKg?.let { Pill("Target ${Format.kg(it)}", Color.White) }
                }
            }
            Icon(Icons.Filled.Edit, contentDescription = "Edit profil", tint = hc.heroContent)
        }
    }
}

@Composable
private fun MenuGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = HawTheme.colors.card,
            border = BorderStroke(1.dp, HawTheme.colors.cardBorder),
        ) {
            Column { content() }
        }
    }
}
