package com.hawdietpro.app.ui.feature.weight

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.domain.nutrition.AdaptiveTdee
import com.hawdietpro.app.ui.components.BentoCard
import com.hawdietpro.app.ui.feature.home.EnergyCard
import kotlin.math.abs

/** Kebutuhan kalori adaptif: nilai, rincian data, sakelar pakai/tidak, dan penjelasan singkat. */
@Composable
fun AdaptiveSection(a: AdaptiveTdee, enabled: Boolean, targetCalories: Int?, onToggle: (Boolean) -> Unit) {
    var showHow by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EnergyCard(a, targetCalories, onClick = { showHow = !showHow })
        BentoCard(Modifier.fillMaxWidth(), padding = 14.dp, corner = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pakai untuk target & perkiraan", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (enabled) "Target harian & perkiraan berat mengikuti data tubuhmu."
                        else "Mati: target memakai rumus (tingkat aktivitas di profil).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (a.status != AdaptiveTdee.Status.BELUM_CUKUP) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stat("Tren timbangan", a.trendKgPerWeek?.let { (if (it > 0) "+" else if (it < 0) "−" else "") + Format.oneDecimal(abs(it)) + " kg/mgg" } ?: "-", Modifier.weight(1f))
                    Stat("Rata-rata makan", a.avgIntake?.let { "${Format.grouped(it)} kkal" } ?: "-", Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stat("Timbangan", "${a.weighIns}x dalam ${a.spanDays} hari", Modifier.weight(1f))
                    Stat("Hari tercatat", "${a.loggedDays} dari ${a.spanDays}", Modifier.weight(1f))
                }
            }
            TextButton(onClick = { showHow = !showHow }) { Text(if (showHow) "Tutup penjelasan" else "Bagaimana cara menghitungnya?") }
            AnimatedVisibility(showHow) {
                Text(
                    "Aplikasi membandingkan rata-rata kalori yang kamu makan dengan tren timbangan " +
                        "(1 kg ≈ 7.700 kkal). Kalau beratmu turun lebih cepat dari perkiraan rumus, artinya tubuhmu membakar lebih banyak, " +
                        "dan sebaliknya. Kalori olahraga yang dicatat dihitung terpisah, jadi tidak terhitung dua kali. " +
                        "Hasilnya dicampur dengan rumus sesuai tingkat keyakinan, dan dibatasi agar tetap masuk akal. " +
                        "Tips: timbang 2–3x seminggu di pagi hari setelah ke toilet, dan catat semua makan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
