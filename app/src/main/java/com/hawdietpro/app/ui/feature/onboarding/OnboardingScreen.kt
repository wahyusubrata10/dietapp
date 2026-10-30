package com.hawdietpro.app.ui.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import com.hawdietpro.app.ui.components.AnimatedCount
import com.hawdietpro.app.ui.components.BentoCard
import com.hawdietpro.app.ui.components.IconTile
import com.hawdietpro.app.ui.components.bouncyClick
import com.hawdietpro.app.ui.theme.NumberMedium
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import com.hawdietpro.app.data.backup.BackupRepository
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.compose.material3.Surface
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.hawdietpro.app.ui.components.IconBadge
import com.hawdietpro.app.ui.theme.HawTheme
import com.hawdietpro.app.ui.theme.MacroColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hawdietpro.app.R
import com.hawdietpro.app.ui.feature.profile.ActivityPicker
import com.hawdietpro.app.ui.feature.profile.BodyFields
import com.hawdietpro.app.ui.feature.profile.GoalPicker
import com.hawdietpro.app.ui.feature.profile.PersonalFields
import com.hawdietpro.app.ui.feature.profile.ProfileForm
import com.hawdietpro.app.ui.feature.profile.ProfileFormViewModel
import com.hawdietpro.app.ui.feature.profile.TargetSummary

private const val STEPS = 7

/** Onboarding: data diri → tubuh → aktivitas → tujuan → target → API key. Selesai = profil tersimpan. */
@Composable
fun OnboardingScreen(
    viewModel: ProfileFormViewModel = hiltViewModel(),
    restoreViewModel: RestoreViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val restore by restoreViewModel.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(restoreViewModel::restore)
    }

    BackHandler(enabled = step > 0) { step-- }

    val canNext = when (step) {
        1 -> form.personalValid
        2 -> form.bodyValid
        5 -> form.preview != null
        else -> true
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        if (step > 0) StepProgress(step, onBack = { step-- })
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                // Geser ke kiri saat maju, ke kanan saat mundur.
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(320)) { it / 3 * dir } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(280)) { -it / 3 * dir } + fadeOut(tween(200)))
            },
            modifier = Modifier.weight(1f),
            label = "step",
        ) { s ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (s) {
                    0 -> {
                        Welcome()
                        RestoreCard(restore) {
                            pickBackup.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed", "application/json"))
                        }
                    }
                    1 -> StepPage("Kenalan dulu", "Data ini dipakai untuk menghitung kebutuhan kalori Anda. Semua tersimpan hanya di HP ini.") {
                        PersonalFields(form, viewModel::update)
                    }
                    2 -> StepPage("Tinggi & berat badan", null) { BodyFields(form, viewModel::update) }
                    3 -> StepPage("Seberapa aktif keseharianmu?", "Pilih aktivitas harian di luar olahraga. Olahraga dicatat terpisah dan menambah jatah makan di hari itu.") { ActivityPicker(form, viewModel::update) }
                    4 -> StepPage("Apa tujuan Anda?", null) { GoalPicker(form, viewModel::update) }
                    5 -> StepPage("Target harian Anda", "Dihitung dengan batas aman — tidak pernah ekstrem.") {
                        form.preview?.let { TargetSummary(it, form, viewModel::update) }
                    }
                    else -> ApiKeyStep(form, viewModel::update)
                }
            }
        }
        // Pratinjau target yang langsung berubah saat aktivitas/tujuan dipilih.
        val preview = form.preview
        AnimatedVisibility(step in 3..4 && preview != null) {
            preview?.let {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🎯", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Text("Perkiraan target harian", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    AnimatedCount(it.calories, NumberMedium, MaterialTheme.colorScheme.primary, suffix = " kkal")
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val enabled = canNext && !form.saving
            val cs = MaterialTheme.colorScheme
            Box(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) Brush.linearGradient(listOf(cs.primary, HawTheme.colors.accentEnd))
                        else Brush.linearGradient(listOf(cs.surfaceContainerHighest, cs.surfaceContainerHighest)),
                    )
                    .then(if (enabled) Modifier.bouncyClick { if (step < STEPS - 1) step++ else viewModel.save() } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (step) {
                        0 -> "Mulai sekarang"
                        STEPS - 1 -> if (form.apiKey.isBlank()) "Lewati & selesai" else "Selesai"
                        else -> "Lanjut"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) cs.onPrimary else cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Welcome() {
    val hc = HawTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(hc.heroGradient).padding(28.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(110.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Haw Diet Pro", style = MaterialTheme.typography.headlineLarge, color = hc.heroContent)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Foto makananmu, langsung tahu kalori & gizinya. Paham masakan Indonesia.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = hc.heroContentSub,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Feature(Icons.Filled.CameraAlt, MaterialTheme.colorScheme.primary, "Foto → gizi", "Nasi, lauk, sayur & sambal dihitung per elemen", Modifier.weight(1f))
            Feature(Icons.Filled.AutoAwesome, MaterialTheme.colorScheme.tertiary, "Skor gizi A–E", "Lengkap dengan saran realistis", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Feature(Icons.Filled.BarChart, MaterialTheme.colorScheme.secondary, "Kalori adaptif", "Belajar dari timbanganmu", Modifier.weight(1f))
            Feature(Icons.Filled.Lock, MacroColors.Fat, "Gratis & privat", "Tanpa akun, data hanya di HP", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, tint: Color, title: String, subtitle: String, modifier: Modifier = Modifier) {
    BentoCard(modifier, padding = 14.dp, corner = 22.dp) {
        IconTile(icon, tint, size = 40.dp)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
    }
}

/** Progres bersegmen + tombol kembali + "Langkah x dari y". */
@Composable
private fun StepProgress(step: Int, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
            Text("Langkah $step dari ${STEPS - 1}", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(STEPS - 1) { i ->
                val active = i < step
                val alpha by animateFloatAsState(if (active) 1f else 0f, tween(300), label = "seg")
                Box(
                    Modifier.weight(1f).height(6.dp).clip(CircleShape).background(cs.surfaceContainerHighest),
                ) {
                    Box(Modifier.fillMaxWidth(alpha).height(6.dp).clip(CircleShape).background(cs.primary))
                }
            }
        }
    }
}

@Composable
private fun StepPage(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    content()
}

@Composable
private fun ApiKeyStep(form: ProfileForm, onChange: ((ProfileForm) -> ProfileForm) -> Unit) {
    val uri = LocalUriHandler.current
    Text("Hubungkan AI Gemini (gratis)", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Analisa foto memakai Google Gemini dengan API key milik Anda sendiri — gratis dari Google AI Studio, tanpa kartu kredit.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "1. Buka Google AI Studio & login dengan akun Google\n2. Klik \"Create API key\"\n3. Salin key lalu tempel di bawah",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
        Spacer(Modifier.width(8.dp))
        Text("Buka Google AI Studio")
    }
    OutlinedTextField(
        value = form.apiKey,
        onValueChange = { v -> onChange { it.copy(apiKey = v.trim()) } },
        label = { Text("API key Gemini") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        supportingText = { Text("Disimpan terenkripsi di HP. Bisa diisi/diubah nanti di Pengaturan.") },
    )
    Text(
        "Tanpa API key, Anda tetap bisa input manual dari database makanan lokal.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Pindah HP: pulihkan semua data dari file backup ZIP tanpa mengisi profil dulu. */
@Composable
private fun RestoreCard(state: RestoreUi, onPick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pindah dari HP lama?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                "Pulihkan semua catatan, foto, berat, olahraga & profil dari file backup ZIP (Profil → Backup & ekspor di HP lama).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = onPick, enabled = !state.busy) { Text("Pulihkan dari backup") }
        }
    }
}

data class RestoreUi(val busy: Boolean = false, val message: String? = null)

@HiltViewModel
class RestoreViewModel @Inject constructor(private val backup: BackupRepository) : ViewModel() {
    private val _state = MutableStateFlow(RestoreUi())
    val state: StateFlow<RestoreUi> = _state.asStateFlow()

    /** Setelah berhasil, profil terisi sehingga aplikasi otomatis masuk ke Beranda. */
    fun restore(uri: Uri) {
        _state.value = RestoreUi(busy = true)
        viewModelScope.launch {
            _state.value = try {
                val r = backup.importZip(uri, replaceAll = true)
                RestoreUi(message = if (r.meals == 0 && r.weights == 0) "File backup kosong atau tanpa profil. Lanjutkan dengan mengisi data diri." else null)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                RestoreUi(message = "Gagal memulihkan: ${e.message ?: "file tidak valid"}")
            }
        }
    }
}
