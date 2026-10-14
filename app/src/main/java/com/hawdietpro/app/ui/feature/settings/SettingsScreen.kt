package com.hawdietpro.app.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.BuildConfig
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.data.gemini.GeminiClient
import com.hawdietpro.app.data.prefs.AppPreferences
import com.hawdietpro.app.data.prefs.SecureStorage
import com.hawdietpro.app.data.prefs.Settings
import com.hawdietpro.app.data.prefs.ThemeMode
import com.hawdietpro.app.data.remote.RemoteConfigStore
import com.hawdietpro.app.data.remote.RemoteConfigSync
import com.hawdietpro.app.data.remote.RemoteFile
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.SectionHeader
import com.hawdietpro.app.ui.components.HawScaffold
import com.hawdietpro.app.ui.feature.profile.MenuItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.SystemUpdate

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val secure: SecureStorage,
    private val gemini: GeminiClient,
    private val store: RemoteConfigStore,
    private val sync: RemoteConfigSync,
) : ViewModel() {

    val settings: StateFlow<Settings> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val hasKey: StateFlow<Boolean> = secure.hasApiKey

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val defaultModel: String get() = store.geminiPrompt.value.model

    fun versions(): List<Pair<String, Int>> = RemoteFile.entries.map { it.fileName to store.currentVersion(it) }

    fun saveKey(key: String, onResult: (String) -> Unit) {
        _busy.value = true
        viewModelScope.launch {
            val result = gemini.verifyApiKey(key)
            if (result.isSuccess) {
                secure.setApiKey(key)
                onResult("API key valid & tersimpan terenkripsi ✅")
            } else {
                onResult(result.exceptionOrNull()?.message ?: "API key tidak valid")
            }
            _busy.value = false
        }
    }

    /** Simpan tanpa verifikasi (mis. sedang offline). */
    fun saveKeyWithoutCheck(key: String) = secure.setApiKey(key)

    fun clearKey() = secure.setApiKey(null)

    fun setModel(model: String) {
        viewModelScope.launch { prefs.setGeminiModelOverride(model) }
    }
    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }
    fun setAutoDownload(v: Boolean) {
        viewModelScope.launch { prefs.setAutoDownloadUpdates(v) }
    }
    fun setWifiOnly(v: Boolean) {
        viewModelScope.launch { prefs.setUpdatesWifiOnly(v) }
    }

    fun syncNow(onResult: (String) -> Unit) {
        _busy.value = true
        viewModelScope.launch {
            val report = sync.sync()
            _busy.value = false
            onResult(
                when {
                    report.updated.isNotEmpty() -> "Diperbarui: ${report.updated.joinToString { it.fileName }}"
                    report.failed.size == RemoteFile.entries.size -> "Gagal sinkron. Periksa koneksi internet."
                    else -> "Semua data sudah versi terbaru"
                },
            )
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onUpdate: () -> Unit,
    onBackup: () -> Unit,
    showMessage: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hasKey by viewModel.hasKey.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current

    var keyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var modelInput by remember(settings.geminiModelOverride) { mutableStateOf(settings.geminiModelOverride.orEmpty()) }

    HawScaffold(title = "Pengaturan", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("API Key Gemini")
            HawCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hasKey) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        if (hasKey) Spacer(Modifier.width(8.dp))
                        Text(
                            if (hasKey) "API key tersimpan (terenkripsi)" else "Belum ada API key",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Text(
                        "Gratis dari Google AI Studio. Key hanya disimpan di HP ini dan dikirim langsung ke Google saat analisa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it.trim() },
                        label = { Text(if (hasKey) "Ganti API key" else "Tempel API key") },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Tampilkan")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                viewModel.saveKey(keyInput) { msg ->
                                    showMessage(msg)
                                    if (msg.contains("✅")) keyInput = ""
                                }
                            },
                            enabled = keyInput.length > 10 && !busy,
                        ) { Text("Simpan & uji") }
                        if (busy) CircularProgressIndicator(Modifier.padding(4.dp))
                        if (hasKey) {
                            TextButton(onClick = {
                                viewModel.clearKey()
                                showMessage("API key dihapus")
                            }) { Text("Hapus") }
                        }
                    }
                    if (keyInput.length > 10 && !busy) {
                        TextButton(onClick = {
                            viewModel.saveKeyWithoutCheck(keyInput)
                            keyInput = ""
                            showMessage("API key disimpan tanpa uji")
                        }) { Text("Simpan tanpa uji (offline)") }
                    }
                    OutlinedButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ambil API key gratis")
                    }
                }
            }

            SectionHeader("Model AI")
            HawCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it.trim() },
                        label = { Text("Nama model (opsional)") },
                        placeholder = { Text(viewModel.defaultModel) },
                        supportingText = { Text("Kosongkan untuk memakai default dari remote config: ${viewModel.defaultModel}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = {
                        viewModel.setModel(modelInput)
                        showMessage(if (modelInput.isBlank()) "Memakai model default" else "Model diubah ke $modelInput")
                    }) { Text("Simpan model") }
                }
            }

            SectionHeader("Tampilan")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(mode.label) }
                }
            }

            SectionHeader("Update aplikasi")
            HawCard {
                Column {
                    ToggleRow("Unduh update otomatis", "APK baru diunduh di background, Anda tinggal tap Install", settings.autoDownloadUpdates, viewModel::setAutoDownload)
                    ToggleRow("Hanya lewat Wi-Fi", "Hemat kuota data", settings.updatesWifiOnly, viewModel::setWifiOnly)
                }
            }
            Column(Modifier.fillMaxWidth()) {
                MenuItem(Icons.Filled.SystemUpdate, "Cek update", "Versi ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", onUpdate)
                MenuItem(Icons.Filled.Backup, "Backup & ekspor", "ZIP & CSV", onBackup)
            }

            SectionHeader("Data remote (prompt AI, DB makanan, aturan analisa)")
            HawCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val versions = remember(settings.lastRemoteSync) { viewModel.versions() }
                    versions.forEach { (name, v) ->
                        Row {
                            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("v$v", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Text(
                        "Sinkron terakhir: " + if (settings.lastRemoteSync > 0) {
                            val t = Instant.ofEpochMilli(settings.lastRemoteSync).atZone(ZoneId.systemDefault())
                            "${Format.relativeDay(t.toLocalDate())} ${Format.time(settings.lastRemoteSync)}"
                        } else "belum pernah",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { viewModel.syncNow(showMessage) }, enabled = !busy) {
                        Icon(Icons.Filled.Sync, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sinkron sekarang")
                    }
                }
            }
            Spacer(Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
