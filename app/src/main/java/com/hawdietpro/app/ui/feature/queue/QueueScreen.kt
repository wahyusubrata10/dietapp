package com.hawdietpro.app.ui.feature.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import com.hawdietpro.app.work.PendingProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
    val online: StateFlow<Boolean> = pending.online
    val current: StateFlow<Pair<Long, Long>?> = pending.current

    init {
        pending.refresh()
    }

    fun retry(id: Long) {
        viewModelScope.launch { pending.retry(id) }
    }

    fun retryAll() {
        viewModelScope.launch { pending.retryAll() }
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
    val online by viewModel.online.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()
    // Detik berjalan untuk foto yang sedang dianalisa.
    val now by produceState(System.currentTimeMillis(), current) {
        while (current != null) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
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
                    if (online || current != null) "Foto diproses satu per satu secara otomatis. Hasilnya langsung tercatat dan bisa diedit."
                    else "Tidak ada internet. Foto akan diproses otomatis begitu tersambung.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(items, key = { it.id }) { item ->
                val processingSince = current?.takeIf { it.first == item.id }?.second
                // DIPROSES tanpa proses yang berjalan = sisa proses yang terhenti → dianggap menunggu.
                val status = PendingStatus.parse(item.status).let {
                    if (processingSince != null) PendingStatus.DIPROSES
                    else if (it == PendingStatus.DIPROSES) PendingStatus.MENUNGGU else it
                }
                val type = MealType.parse(item.mealType)
                HawCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MealPhoto(item.photoPath)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${type.label} · ${Format.relativeDay(Instant.ofEpochMilli(item.capturedAt).atZone(ZoneId.systemDefault()).toLocalDate())} ${Format.time(item.capturedAt)}", style = MaterialTheme.typography.titleSmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (status == PendingStatus.DIPROSES) {
                                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(
                                        statusText(item, status, online, processingSince?.let { ((now - it) / 1000).coerceAtLeast(0) }),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (status == PendingStatus.GAGAL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        item.lastError?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { viewModel.retry(item.id) }, enabled = status != PendingStatus.DIPROSES) {
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

private fun statusText(item: PendingAnalysisEntity, status: PendingStatus, online: Boolean, elapsedSeconds: Long?): String {
    val base = when {
        status == PendingStatus.DIPROSES -> status.label + (elapsedSeconds?.let { " ${it} dtk" } ?: "")
        status == PendingStatus.GAGAL -> "Gagal · ketuk Coba lagi atau Isi manual"
        item.attempts >= PendingProcessor.MAX_ATTEMPTS -> "Berhenti otomatis · ketuk Coba lagi"
        !online -> "Menunggu koneksi internet"
        item.attempts > 0 -> "Akan dicoba lagi otomatis"
        else -> status.label
    }
    return base + if (item.attempts > 0 && status != PendingStatus.DIPROSES) " · dicoba ${item.attempts}×" else ""
}
