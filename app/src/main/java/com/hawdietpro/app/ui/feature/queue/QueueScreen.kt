package com.hawdietpro.app.ui.feature.queue

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.data.local.entity.PendingAnalysisEntity
import com.hawdietpro.app.data.repository.PendingRepository
import com.hawdietpro.app.data.repository.PendingStatus
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.ui.components.EmptyState
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.HawScaffold
import com.hawdietpro.app.ui.components.MealPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(private val pending: PendingRepository) : ViewModel() {
    val items: StateFlow<List<PendingAnalysisEntity>> = pending.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry(id: Long) {
        viewModelScope.launch { pending.retry(id) }
    }

    fun retryAll() {
        viewModelScope.launch { items.value.forEach { pending.retry(it.id) } }
    }

    fun remove(id: Long) {
        viewModelScope.launch { pending.remove(id, deletePhoto = true) }
    }
}

@Composable
fun QueueScreen(
    onBack: () -> Unit,
    onFillManually: (id: Long, photoPath: String, type: MealType, time: Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    HawScaffold(
        title = "Antrian analisa",
        onBack = onBack,
        actions = {
            if (items.isNotEmpty()) TextButton(onClick = viewModel::retryAll) { Text("Proses semua") }
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(
                Icons.Filled.CloudDone,
                "Antrian kosong",
                "Foto yang diambil saat offline atau saat kuota AI habis akan menunggu di sini dan diproses otomatis.",
                Modifier.padding(padding),
            )
            return@HawScaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Foto diproses otomatis saat ada internet. Hasilnya langsung tercatat dan bisa diedit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(items, key = { it.id }) { item ->
                val status = PendingStatus.parse(item.status)
                val type = MealType.parse(item.mealType)
                HawCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MealPhoto(item.photoPath)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${type.label} · ${Format.relativeDay(Instant.ofEpochMilli(item.capturedAt).atZone(ZoneId.systemDefault()).toLocalDate())} ${Format.time(item.capturedAt)}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    status.label + if (item.attempts > 0) " · dicoba ${item.attempts}×" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (status == PendingStatus.GAGAL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        item.lastError?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { viewModel.retry(item.id) }) {
                                Icon(Icons.Filled.Refresh, null)
                                Text(" Coba lagi")
                            }
                            TextButton(onClick = { onFillManually(item.id, item.photoPath, type, item.capturedAt) }) {
                                Icon(Icons.Filled.EditNote, null)
                                Text(" Isi manual")
                            }
                            TextButton(onClick = { viewModel.remove(item.id) }) {
                                Icon(Icons.Filled.Delete, null)
                                Text(" Hapus")
                            }
                        }
                        if (item.lastError?.contains("API key") == true) {
                            Button(onClick = onOpenSettings) { Text("Buka Pengaturan API key") }
                        }
                    }
                }
            }
        }
    }
}
