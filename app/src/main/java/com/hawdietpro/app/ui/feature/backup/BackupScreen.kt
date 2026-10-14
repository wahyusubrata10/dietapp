package com.hawdietpro.app.ui.feature.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.data.backup.BackupRepository
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.HawScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(private val backup: BackupRepository) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private fun run(onDone: (String) -> Unit, block: suspend () -> String) {
        _busy.value = true
        viewModelScope.launch {
            val msg = runCatching { block() }.getOrElse { "Gagal: ${it.message ?: it.javaClass.simpleName}" }
            _busy.value = false
            onDone(msg)
        }
    }

    fun exportZip(uri: Uri, onDone: (String) -> Unit) = run(onDone) { "Backup tersimpan: ${backup.exportZip(uri)} catatan makan + foto" }
    fun importZip(uri: Uri, replace: Boolean, onDone: (String) -> Unit) = run(onDone) {
        val r = backup.importZip(uri, replace)
        "Dipulihkan: ${r.meals} catatan makan, ${r.weights} catatan berat, ${r.photos} foto"
    }
    fun exportCsv(uri: Uri, onDone: (String) -> Unit) = run(onDone) { "CSV tersimpan: ${backup.exportMealsCsv(uri)} baris" }
    fun exportWeightCsv(uri: Uri, onDone: (String) -> Unit) = run(onDone) { "CSV berat tersimpan: ${backup.exportWeightCsv(uri)} baris" }
}

@Composable
fun BackupScreen(onBack: () -> Unit, showMessage: (String) -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val today = LocalDate.now()

    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.exportZip(it, showMessage) }
    }
    val importZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.exportCsv(it, showMessage) }
    }
    val exportWeight = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.exportWeightCsv(it, showMessage) }
    }

    HawScaffold(title = "Backup & ekspor", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Section(
                Icons.Filled.FileDownload,
                "Backup lengkap (ZIP)",
                "Semua catatan makan, foto, log berat, profil, dan makanan buatan Anda. Simpan di Google Drive / penyimpanan lain.",
            ) {
                Button(onClick = { exportZip.launch("haw-diet-backup-$today.zip") }, enabled = !busy) { Text("Buat backup") }
            }
            Section(
                Icons.Filled.FileUpload,
                "Pulihkan dari backup",
                "Pilih file ZIP hasil backup Haw Diet Pro.",
            ) {
                OutlinedButton(onClick = { importZip.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }, enabled = !busy) {
                    Text("Pilih file backup")
                }
            }
            Section(
                Icons.Filled.TableChart,
                "Ekspor CSV (Excel)",
                "Satu baris per elemen makanan. Pemisah titik koma & desimal koma agar langsung rapi di Excel/Google Sheets.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportCsv.launch("haw-diet-riwayat-$today.csv") }, enabled = !busy) { Text("Ekspor riwayat makan") }
                    OutlinedButton(onClick = { exportWeight.launch("haw-diet-berat-$today.csv") }, enabled = !busy) { Text("Ekspor log berat") }
                }
            }
            Text(
                "Keamanan update: sebelum skema database berubah karena update aplikasi, salinan database otomatis dibuat di penyimpanan internal aplikasi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Pulihkan backup") },
            text = { Text("Gabungkan dengan data sekarang, atau ganti semua data dengan isi backup?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importZip(uri, replace = false, onDone = showMessage)
                    pendingImport = null
                }) { Text("Gabungkan") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.importZip(uri, replace = true, onDone = showMessage)
                    pendingImport = null
                }) { Text("Ganti semua", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

@Composable
private fun Section(icon: ImageVector, title: String, desc: String, action: @Composable () -> Unit) {
    HawCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.layout.Row {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action()
        }
    }
}
