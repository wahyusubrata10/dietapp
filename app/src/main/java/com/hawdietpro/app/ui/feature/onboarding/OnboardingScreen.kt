package com.hawdietpro.app.ui.feature.onboarding

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
fun OnboardingScreen(viewModel: ProfileFormViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }

    BackHandler(enabled = step > 0) { step-- }

    val canNext = when (step) {
        1 -> form.personalValid
        2 -> form.bodyValid
        5 -> form.preview != null
        else -> true
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        if (step > 0) {
            LinearProgressIndicator(
                progress = { step / (STEPS - 1).toFloat() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.weight(1f),
            label = "step",
        ) { s ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (s) {
                    0 -> Welcome()
                    1 -> StepPage("Kenalan dulu", "Data ini dipakai untuk menghitung kebutuhan kalori Anda. Semua tersimpan hanya di HP ini.") {
                        PersonalFields(form, viewModel::update)
                    }
                    2 -> StepPage("Tinggi & berat badan", null) { BodyFields(form, viewModel::update) }
                    3 -> StepPage("Seberapa aktif Anda?", "Pilih yang paling mendekati keseharian Anda.") { ActivityPicker(form, viewModel::update) }
                    4 -> StepPage("Apa tujuan Anda?", null) { GoalPicker(form, viewModel::update) }
                    5 -> StepPage("Target harian Anda", "Dihitung dengan batas aman — tidak pernah ekstrem.") {
                        form.preview?.let { TargetSummary(it, form, viewModel::update) }
                    }
                    else -> ApiKeyStep(form, viewModel::update)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Kembali") }
            }
            Button(
                onClick = { if (step < STEPS - 1) step++ else viewModel.save() },
                enabled = canNext && !form.saving,
                modifier = Modifier.weight(if (step > 0) 1.5f else 1f).height(52.dp),
            ) {
                Text(
                    when (step) {
                        0 -> "Mulai"
                        STEPS - 1 -> if (form.apiKey.isBlank()) "Lewati & selesai" else "Selesai"
                        else -> "Lanjut"
                    },
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
        Feature(Icons.Filled.CameraAlt, MaterialTheme.colorScheme.primary, "Foto → menu dikenali", "Nasi, lauk, sayur & sambal dihitung per elemen")
        Feature(Icons.Filled.AutoAwesome, MaterialTheme.colorScheme.tertiary, "Skor gizi A–E", "Lengkap dengan saran yang realistis")
        Feature(Icons.Filled.BarChart, MaterialTheme.colorScheme.secondary, "Analisa & perkiraan berat", "Harian, mingguan, bulanan + grafik")
        Feature(Icons.Filled.Lock, MacroColors.Fat, "Gratis & privat", "Tanpa akun, semua data tersimpan di HP")
    }
}

@Composable
private fun Feature(icon: ImageVector, tint: Color, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
