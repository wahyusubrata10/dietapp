package com.hawdietpro.app.ui.feature.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.data.repository.AchievementRepository
import com.hawdietpro.app.domain.achievement.Achievement
import com.hawdietpro.app.domain.achievement.AchievementGroup
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.HawScaffold
import com.hawdietpro.app.ui.components.LoadingBox
import com.hawdietpro.app.ui.components.Pill
import com.hawdietpro.app.ui.components.SectionHeader
import com.hawdietpro.app.ui.components.SoftProgress
import com.hawdietpro.app.ui.theme.HawTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class AchievementsUi(
    val loading: Boolean = true,
    val items: List<Achievement> = emptyList(),
    /** Badge yang baru terbuka sejak terakhir layar ini dibuka (ditandai "Baru"). */
    val fresh: Set<String> = emptySet(),
) {
    val unlockedCount: Int get() = items.count { it.unlocked }
}

@HiltViewModel
class AchievementsViewModel @Inject constructor(private val repo: AchievementRepository) : ViewModel() {

    private val fresh = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<AchievementsUi> = combine(repo.observe(), fresh) { list, f ->
        AchievementsUi(loading = false, items = list, fresh = f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AchievementsUi())

    init {
        viewModelScope.launch {
            fresh.value = repo.observeNew().first().map { it.id }.toSet()
            repo.markAllSeen()
        }
    }
}

@Composable
fun AchievementsScreen(onBack: () -> Unit, viewModel: AchievementsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HawScaffold(title = "Pencapaian", onBack = onBack) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@HawScaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SummaryCard(state) }
            AchievementGroup.entries.forEach { group ->
                val list = state.items.filter { it.group == group }
                    .sortedWith(compareByDescending<Achievement> { it.unlocked }.thenByDescending { it.fraction })
                if (list.isEmpty()) return@forEach
                item(key = "h_${group.name}") {
                    SectionHeader(group.label, subtitle = "${list.count { it.unlocked }} dari ${list.size} terbuka")
                }
                items(list.chunked(2), key = { row -> row.joinToString { it.id } }) { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { a -> BadgeTile(a, isNew = a.id in state.fresh, modifier = Modifier.weight(1f).fillMaxHeight()) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            item {
                Text(
                    "Badge dihitung dari seluruh catatan Anda, jadi tetap ada setelah restore backup. Streak memakai rekor terpanjang.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(state: AchievementsUi) {
    val hc = HawTheme.colors
    val total = state.items.size.coerceAtLeast(1)
    val next = state.items.filter { !it.unlocked }.maxByOrNull { it.fraction }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(hc.heroGradient).padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏅", fontSize = 40.sp)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${state.unlockedCount} / ${state.items.size}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = hc.heroContent,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text("badge terbuka", style = MaterialTheme.typography.bodyMedium, color = hc.heroContentSub)
                }
            }
            SoftProgress(state.unlockedCount.toFloat() / total, color = hc.heroContent, track = hc.heroTrack, height = 10.dp)
            next?.let {
                Text(
                    "Berikutnya: ${it.emoji} ${it.title} — ${progressText(it)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = hc.heroContent,
                )
            }
        }
    }
}

@Composable
private fun BadgeTile(a: Achievement, isNew: Boolean, modifier: Modifier = Modifier) {
    val accent = if (a.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    HawCard(modifier = modifier, contentPadding = 14.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(60.dp).clip(CircleShape)
                    .background(if (a.unlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(a.emoji, fontSize = 28.sp, modifier = Modifier.alpha(if (a.unlocked) 1f else 0.35f))
            }
            Spacer(Modifier.height(8.dp))
            if (isNew) {
                Pill("Baru", MaterialTheme.colorScheme.tertiary, filled = true)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                a.title,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                color = if (a.unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                a.description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.height(10.dp))
            if (a.unlocked) {
                Text("✓ Terbuka", style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.Bold)
            } else {
                SoftProgress(a.fraction, color = MaterialTheme.colorScheme.primary, height = 6.dp)
                Spacer(Modifier.height(4.dp))
                Text(progressText(a), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun progressText(a: Achievement): String {
    fun fmt(v: Double) = when (a.unit) {
        "kg" -> String.format(Locale("id", "ID"), "%.1f", v)
        "kkal" -> String.format(Locale("id", "ID"), "%,d", v.toInt())
        else -> v.toInt().toString()
    }
    return "${fmt(a.progress.coerceAtMost(a.goal))} / ${fmt(a.goal)} ${a.unit}"
}
