package com.hawdietpro.app.ui.feature.wrap

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.data.prefs.AppPreferences
import com.hawdietpro.app.data.repository.EnergyRepository
import com.hawdietpro.app.data.repository.ExerciseRepository
import com.hawdietpro.app.data.repository.MealRepository
import com.hawdietpro.app.data.repository.WeightRepository
import com.hawdietpro.app.domain.insight.WeeklyWrap
import com.hawdietpro.app.domain.insight.WeeklyWrapCalculator
import com.hawdietpro.app.domain.nutrition.DayStatus
import com.hawdietpro.app.ui.components.AnimatedCount
import com.hawdietpro.app.ui.components.LoadingBox
import com.hawdietpro.app.ui.components.MiniRing
import com.hawdietpro.app.ui.theme.NumberDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

@HiltViewModel
class WrapViewModel @Inject constructor(
    meals: MealRepository,
    exercises: ExerciseRepository,
    weights: WeightRepository,
    energy: EnergyRepository,
    prefs: AppPreferences,
    savedState: SavedStateHandle,
) : ViewModel() {
    val weekStart: LocalDate = savedState.get<String>("week")?.toLongOrNull()?.let(LocalDate::ofEpochDay)
        ?: WeeklyWrapCalculator.lastWeekStart(LocalDate.now())

    val state: StateFlow<WeeklyWrap?> = combine(
        meals.observeRange(weekStart, weekStart.plusDays(6)),
        exercises.observeRange(weekStart, weekStart.plusDays(6)),
        weights.observeAll(),
        energy.observeTarget(),
        energy.observeAdaptive(),
    ) { m, e, w, target, adaptive ->
        val burned = ExerciseRepository.dailyTotals(e)
        WeeklyWrapCalculator.compute(
            weekStart = weekStart,
            meals = m,
            budgetFor = { d -> target?.let { it.calories + (burned[d] ?: 0.0).roundToInt() } },
            tdee = adaptive?.tdee ?: target?.tdee,
            exerciseByDay = burned,
            weights = w,
            proteinTarget = target?.proteinG,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Rekap minggu ini dianggap sudah dilihat (banner di Beranda hilang).
        viewModelScope.launch { prefs.setLastWrapWeek(weekStart.toEpochDay()) }
    }
}

@Composable
fun WrapScreen(onClose: () -> Unit, viewModel: WrapViewModel = hiltViewModel()) {
    val wrap by viewModel.state.collectAsStateWithLifecycle()
    val w = wrap
    if (w == null) {
        LoadingBox()
        return
    }
    WrapContent(w, onClose)
}

/** Slide-slide rekap; [initialPage] dipakai pratinjau/snapshot. */
@Composable
fun WrapContent(w: WeeklyWrap, onClose: () -> Unit, initialPage: Int = 0) {
    val pages = if (w.hasData) WRAP_PAGES else 1
    val pager = rememberPagerState(initialPage = initialPage.coerceIn(0, pages - 1)) { pages }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    // Rekam isi layar agar slide bisa dibagikan sebagai gambar.
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
        ) { page ->
            if (!w.hasData) EmptySlide(w) else WrapSlide(w, page)
        }
        // Indikator halaman + tombol tutup.
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(pages) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (i <= pager.currentPage) 0.95f else 0.3f)),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Haw Diet Pro · Rekap", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = Color.White) }
            }
        }
        if (w.hasData) {
            Button(
                onClick = {
                    scope.launch {
                        runCatching { shareBitmap(context, layer.toImageBitmap().asAndroidBitmap()) }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
            ) {
                Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Bagikan", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private const val WRAP_PAGES = 5

private val SlideGradients = listOf(
    listOf(Color(0xFF0B4A37), Color(0xFF13896A), Color(0xFF2CC598)),
    listOf(Color(0xFF1B1F6B), Color(0xFF3F3FC2), Color(0xFF7A6CF0)),
    listOf(Color(0xFF7A2E0B), Color(0xFFD25B17), Color(0xFFFF9E4F)),
    listOf(Color(0xFF6B0F3A), Color(0xFFC0306E), Color(0xFFFF7BA9)),
    listOf(Color(0xFF07343F), Color(0xFF0E7490), Color(0xFF22C3D6)),
)

@Composable
private fun SlideFrame(index: Int, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(SlideGradients[index % SlideGradients.size]))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 28.dp, end = 28.dp, top = 90.dp, bottom = 96.dp),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

@Composable
private fun Big(text: String, size: Int = 56) {
    Text(text, style = NumberDisplay.copy(fontSize = size.sp, lineHeight = (size + 4).sp), color = Color.White)
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f))
}

@Composable
private fun Kicker(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f), letterSpacing = 1.5.sp)
}

private fun range(w: WeeklyWrap) = "${Format.dayMonth(w.weekStart)} – ${Format.dayMonth(w.weekEnd)}"

@Composable
fun WrapSlide(w: WeeklyWrap, page: Int) {
    SlideFrame(page) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            WrapPageContent(w, page)
            // Tanda kecil agar gambar yang dibagikan tetap menyebut aplikasinya.
            Text("— Haw Diet Pro", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun WrapPageContent(w: WeeklyWrap, page: Int) {
    run {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (page) {
                0 -> {
                    Kicker("Rekap minggu · ${range(w)}")
                    Text("🗓️", fontSize = 56.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedCount(w.daysLogged, NumberDisplay.copy(fontSize = 96.sp, lineHeight = 100.sp), Color.White)
                        Text(" /7 hari", style = MaterialTheme.typography.headlineSmall, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 14.dp))
                    }
                    Caption("kamu mencatat makan. ${w.mealCount} makanan, ${w.photoCount} lewat foto.")
                    Caption(
                        when {
                            w.daysLogged == 7 -> "Sempurna! Tidak ada hari yang terlewat. 🔥"
                            w.daysLogged >= 5 -> "Konsisten banget, pertahankan!"
                            else -> "Minggu depan coba catat lebih rutin ya."
                        },
                    )
                }
                1 -> {
                    Kicker("Energi")
                    val deficit = w.energyBalance < 0
                    Caption(if (deficit) "Total defisit" else "Total surplus")
                    Big(Format.grouped(abs(w.energyBalance)) + " kkal")
                    Caption("≈ ${if (deficit) "−" else "+"}${Format.oneDecimal(abs(w.estimatedKg))} kg (perkiraan dari kalori)")
                    Spacer(Modifier.height(6.dp))
                    StatusWeek(w)
                    Text(
                        "▢ putih = sesuai · pudar = kurang · merah = lebih · gelap = tidak dicatat",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Caption("${w.daysOnTarget} hari sesuai jatah kalori (±10%).")
                }
                2 -> {
                    Kicker("Makanan")
                    Caption("Menu paling sering")
                    Text(
                        w.topMenu ?: "-",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        maxLines = 3,
                    )
                    if (w.topMenuCount > 0) Caption("${w.topMenuCount}× minggu ini")
                    Spacer(Modifier.height(6.dp))
                    Caption("⭐ ${w.goodGradeMeals} makanan dengan skor gizi A/B")
                    w.bestDay?.let { Caption("🏆 Hari terbaik: ${Format.dayName(it)}, ${Format.dayMonth(it)} (paling pas dengan jatah)") }
                }
                3 -> {
                    Kicker("Gizi rata-rata per hari")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val target = w.proteinTarget ?: 0
                        MiniRing(if (target > 0) w.avgProtein.toFloat() / target else 0f, Color.White, size = 120.dp, stroke = 12.dp, track = Color.White.copy(alpha = 0.2f)) {
                            Text("${w.avgProtein} g", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(20.dp))
                        Column {
                            Text("Protein", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            w.proteinTarget?.let { Caption("target $it g") }
                        }
                    }
                    Caption("🌾 Serat ${w.avgFiber} g/hari" + if (w.avgFiber < 25) " (target 25 g: tambah sayur & buah)" else " 👍")
                    Caption("🍽️ Rata-rata ${Format.grouped(w.avgCalories)} kkal per hari tercatat")
                }
                else -> {
                    Kicker("Gerak & berat")
                    Caption("Kalori olahraga")
                    Big("+" + Format.grouped(w.totalExercise) + " kkal", 48)
                    Spacer(Modifier.height(6.dp))
                    Caption("Perubahan timbangan")
                    Big(
                        w.weightChange?.let { (if (it > 0) "+" else if (it < 0) "−" else "") + Format.oneDecimal(abs(it)) + " kg" } ?: "–",
                        48,
                    )
                    if (w.weightChange == null) Caption("Timbang minimal 2× seminggu agar terlihat di sini.")
                    Spacer(Modifier.height(6.dp))
                    Caption("Terima kasih sudah konsisten. Sampai jumpa di rekap minggu depan! 💚")
                }
            }
        }
    }
}

@Composable
private fun StatusWeek(w: WeeklyWrap) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        w.statuses.forEachIndexed { i, st ->
            val day = w.weekStart.plusDays(i.toLong())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when (st) {
                                DayStatus.SESUAI -> Color.White
                                DayStatus.KURANG -> Color.White.copy(alpha = 0.55f)
                                DayStatus.LEBIH -> Color(0xFFFFB4A6)
                                DayStatus.KOSONG -> Color.White.copy(alpha = 0.12f)
                            },
                        ),
                )
                Text(Format.dayName(day).take(3), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun EmptySlide(w: WeeklyWrap) {
    SlideFrame(0) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Kicker("Rekap minggu · ${range(w)}")
            Text("📭", fontSize = 56.sp)
            Text("Belum ada catatan minggu ini", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Caption("Catat makanmu setiap hari, dan rekap seru akan muncul setiap Senin.")
        }
    }
}

private suspend fun shareBitmap(context: Context, bitmap: Bitmap) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        File(dir, "rekap-haw-diet.png").also { f -> f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Bagikan rekap").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
