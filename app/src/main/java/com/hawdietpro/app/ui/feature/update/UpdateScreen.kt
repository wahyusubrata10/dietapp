package com.hawdietpro.app.ui.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.data.prefs.AppPreferences
import com.hawdietpro.app.data.update.ReleaseInfo
import com.hawdietpro.app.data.update.UpdateRepository
import com.hawdietpro.app.data.update.UpdateState
import com.hawdietpro.app.ui.components.Banner
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.HawScaffold
import com.hawdietpro.app.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updates: UpdateRepository,
    private val scheduler: WorkScheduler,
    private val prefs: AppPreferences,
) : ViewModel() {
    val state: StateFlow<UpdateState> = updates.state
    val versionName = updates.currentVersionName
    val versionCode = updates.currentVersionCode

    fun check() {
        viewModelScope.launch { updates.check() }
    }

    /** Unduh sekarang (abaikan batasan Wi-Fi karena diminta langsung oleh pengguna). */
    fun download() = scheduler.enqueueUpdateDownload(wifiOnly = false)

    fun canInstall() = updates.canInstallPackages()
    fun permissionIntent() = updates.unknownSourcesSettingsIntent()

    fun install(path: String) {
        viewModelScope.launch {
            runCatching { updates.install(path) }.onFailure { updates.reportInstallError("Gagal membuka installer: ${it.message}") }
        }
    }

    fun skip(release: ReleaseInfo) {
        viewModelScope.launch { prefs.setSkippedUpdate(release.versionCode) }
        updates.skip(release)
    }
}

@Composable
fun UpdateScreen(onBack: () -> Unit, viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canInstall by remember { mutableStateOf(viewModel.canInstall()) }
    LifecycleResumeEffect(Unit) {
        canInstall = viewModel.canInstall()
        onPauseOrDispose { }
    }

    HawScaffold(title = "Update aplikasi", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HawCard {
                Column {
                    Text("Versi terpasang", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(viewModel.versionName, style = MaterialTheme.typography.titleLarge)
                    Text("build ${viewModel.versionCode}", style = MaterialTheme.typography.bodySmall)
                }
            }

            when (val s = state) {
                UpdateState.Idle, is UpdateState.UpToDate -> {
                    if (s is UpdateState.UpToDate) {
                        Banner(text = "Aplikasi sudah versi terbaru.", icon = Icons.Filled.CheckCircle)
                    }
                    Button(onClick = viewModel::check, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cek update sekarang")
                    }
                }
                UpdateState.Checking -> Row { CircularProgressIndicator(); Spacer(Modifier.width(12.dp)); Text("Memeriksa GitHub Releases…") }
                is UpdateState.Available -> {
                    ReleaseCard(s.release)
                    Button(onClick = viewModel::download, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Filled.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Unduh update (${(s.release.apkSize / 1024 / 1024.0).let { "%.1f".format(it) }} MB)")
                    }
                    TextButton(onClick = { viewModel.skip(s.release) }) { Text("Lewati versi ini") }
                }
                is UpdateState.Downloading -> {
                    ReleaseCard(s.release)
                    Text("Mengunduh… ${(s.progress * 100).roundToInt()}%")
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Text("Unduhan berjalan di background, Anda boleh menutup layar ini.", style = MaterialTheme.typography.bodySmall)
                }
                is UpdateState.ReadyToInstall -> {
                    ReleaseCard(s.release)
                    if (!canInstall) {
                        Banner(
                            title = "Izinkan pemasangan",
                            text = "Sekali saja: izinkan Haw Diet Pro memasang aplikasi (update) agar tombol Install berfungsi.",
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        OutlinedButton(onClick = { context.startActivity(viewModel.permissionIntent()) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Buka pengaturan izin")
                        }
                    }
                    Button(
                        onClick = { viewModel.install(s.apkPath) },
                        enabled = canInstall,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Filled.InstallMobile, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Install update")
                    }
                    Text(
                        "Data Anda tetap aman: update dipasang di atas versi lama dengan tanda tangan yang sama.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is UpdateState.Error -> {
                    Banner(
                        text = s.message,
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    s.release?.let { ReleaseCard(it) }
                    Button(onClick = { if (s.release != null) viewModel.download() else viewModel.check() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (s.release != null) "Coba unduh lagi" else "Cek lagi")
                    }
                }
            }
        }
    }
}

@Composable
private fun Row(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
private fun ReleaseCard(release: ReleaseInfo) {
    HawCard(color = MaterialTheme.colorScheme.primaryContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Versi baru: ${release.versionName}", style = MaterialTheme.typography.titleMedium)
            Text("build ${release.versionCode}", style = MaterialTheme.typography.bodySmall)
            if (release.changelog.isNotBlank()) {
                Text("Perubahan:", style = MaterialTheme.typography.labelLarge)
                Text(release.changelog.replace("## Perubahan", "").trim(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
