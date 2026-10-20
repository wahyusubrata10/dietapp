package com.hawdietpro.app.ui.feature.settings

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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.data.gemini.AiCallLog
import com.hawdietpro.app.data.gemini.AiCallRecord
import com.hawdietpro.app.ui.components.EmptyState
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.HawScaffold
import com.hawdietpro.app.ui.components.Pill
import com.hawdietpro.app.ui.components.StatRow
import com.hawdietpro.app.ui.components.StatTile
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AiLogViewModel @Inject constructor(private val log: AiCallLog) : ViewModel() {
    val records = log.records
    fun clear() = log.clear()
}

private val timeFormat = DateTimeFormatter.ofPattern("d MMM HH.mm", Locale("id", "ID"))

private fun seconds(ms: Long) = String.format(Locale("id", "ID"), "%.1f dtk", ms / 1000.0)

/** Riwayat permintaan AI untuk diagnosa: berapa lama, model mana, dan kenapa gagal. */
@Composable
fun AiLogScreen(onBack: () -> Unit, viewModel: AiLogViewModel = hiltViewModel()) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    HawScaffold(
        title = "Riwayat analisa AI",
        onBack = onBack,
        actions = {
            if (records.isNotEmpty()) {
                IconButton(onClick = viewModel::clear) { Icon(Icons.Filled.DeleteSweep, contentDescription = "Hapus riwayat") }
            }
        },
    ) { padding ->
        if (records.isEmpty()) {
            EmptyState(
                Icons.Filled.Insights,
                "Belum ada riwayat",
                "Setiap analisa foto, saran menu, dan analisa mendalam akan tercatat di sini: lama proses, model yang dipakai, dan penyebab bila gagal.",
                Modifier.padding(padding),
            )
            return@HawScaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Summary(records) }
            items(records) { RecordCard(it) }
            item {
                Text(
                    "Disimpan hanya di HP ini (maks. 60 terakhir), tanpa foto & tanpa isi jawaban.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Summary(records: List<AiCallRecord>) {
    val ok = records.filter { it.success }
    val median = ok.map { it.totalMs }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
    val topError = records.filter { !it.success }.groupingBy { it.error ?: "lainnya" }.eachCount().maxByOrNull { it.value }
    StatRow {
        StatTile("Berhasil", "${ok.size}/${records.size}", Modifier.weight(1f), sub = "${ok.size * 100 / records.size}%")
        StatTile("Waktu tengah", median?.let(::seconds) ?: "-", Modifier.weight(1f), sub = "yang berhasil")
        StatTile("Gagal tersering", topError?.key ?: "-", Modifier.weight(1f), sub = topError?.let { "${it.value}×" } ?: "tidak ada")
    }
}

@Composable
private fun RecordCard(r: AiCallRecord) {
    val cs = MaterialTheme.colorScheme
    HawCard(contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.kind, style = MaterialTheme.typography.titleSmall)
                    Text(
                        Instant.ofEpochMilli(r.time).atZone(ZoneId.systemDefault()).format(timeFormat),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                Text(seconds(r.totalMs), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                if (r.success) Pill("Berhasil", cs.primary) else Pill(r.error ?: "Gagal", cs.error)
            }
            r.attempts.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        a.model,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${seconds(a.durationMs)} · ${a.outcome}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (a.ok) cs.primary else cs.onSurfaceVariant,
                    )
                }
            }
            r.notes.forEach { n ->
                Text("• $n", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
}
