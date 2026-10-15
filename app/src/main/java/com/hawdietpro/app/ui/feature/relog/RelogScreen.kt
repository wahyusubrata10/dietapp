package com.hawdietpro.app.ui.feature.relog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.data.repository.MealRepository
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.ui.components.EmptyState
import com.hawdietpro.app.ui.components.GradeBadge
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.HawScaffold
import com.hawdietpro.app.ui.components.MacroInline
import com.hawdietpro.app.ui.components.MealPhoto
import com.hawdietpro.app.ui.components.SectionHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class RelogUi(val favorites: List<Meal> = emptyList(), val recent: List<Meal> = emptyList(), val loading: Boolean = true)

@HiltViewModel
class RelogViewModel @Inject constructor(private val meals: MealRepository) : ViewModel() {

    val state: StateFlow<RelogUi> = combine(meals.observeFavorites(), meals.observeRecentDistinct(20)) { fav, recent ->
        val favNames = fav.map { it.menuName.trim().lowercase() }.toSet()
        RelogUi(fav, recent.filter { it.menuName.trim().lowercase() !in favNames }, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RelogUi())

    /** Catat ulang sekarang; mengembalikan id baru (untuk "Batal"). */
    suspend fun logNow(meal: Meal): Pair<Long, MealType> {
        val type = MealType.fromHour(LocalTime.now().hour)
        return meals.duplicate(meal, System.currentTimeMillis(), type) to type
    }

    fun undo(id: Long) {
        viewModelScope.launch { meals.delete(id) }
    }

    fun unfavorite(meal: Meal) {
        viewModelScope.launch { meals.setFavorite(meal.id, false) }
    }
}

@Composable
fun RelogScreen(onBack: () -> Unit, viewModel: RelogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun log(meal: Meal) {
        scope.launch {
            val (id, type) = viewModel.logNow(meal)
            val result = snackbar.showSnackbar(
                message = "✓ ${meal.menuName} dicatat ke ${type.label}",
                actionLabel = "Batal",
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(id)
        }
    }

    HawScaffold(title = "Makan lagi", onBack = onBack) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (!state.loading && state.favorites.isEmpty() && state.recent.isEmpty()) {
                EmptyState(
                    Icons.Filled.Replay,
                    "Belum ada menu",
                    "Menu yang pernah Anda catat akan muncul di sini, jadi bisa dicatat ulang dengan 1 ketukan. Tandai ★ di detail makan untuk menjadikannya favorit.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            "Ketuk \"Catat\" untuk mencatat menu yang sama sekarang (${MealType.fromHour(LocalTime.now().hour).label}). Bisa dibatalkan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.favorites.isNotEmpty()) {
                        item { SectionHeader("★ Favorit") }
                        items(state.favorites, key = { "f${it.id}" }) { meal -> RelogRow(meal, favorite = true, onLog = { log(meal) }) }
                    }
                    if (state.recent.isNotEmpty()) {
                        item { SectionHeader("Terakhir dicatat") }
                        items(state.recent, key = { "r${it.id}" }) { meal -> RelogRow(meal, favorite = false, onLog = { log(meal) }) }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun RelogRow(meal: Meal, favorite: Boolean, onLog: () -> Unit) {
    HawCard(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                MealPhoto(meal.photoPath, size = 56.dp)
                meal.grade?.let { GradeBadge(it, size = 20.dp, modifier = Modifier.align(Alignment.BottomEnd)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (favorite) {
                        Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(end = 4.dp))
                    }
                    Text(meal.menuName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "${Format.kcal(meal.total.calories)} · ${meal.items.size} elemen · terakhir ${Format.relativeDay(java.time.Instant.ofEpochMilli(meal.eatenAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()).lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.padding(top = 4.dp))
                MacroInline(meal.total)
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onLog, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                Icon(Icons.Filled.Add, null)
                Text("Catat")
            }
        }
    }
}
