package com.hawdietpro.app.ui.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.data.update.UpdateState
import com.hawdietpro.app.domain.config.Announcement
import com.hawdietpro.app.domain.exercise.ExerciseCatalog
import com.hawdietpro.app.domain.insight.Insight
import com.hawdietpro.app.domain.model.Exercise
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.nutrition.AdaptiveTdee
import com.hawdietpro.app.domain.nutrition.NutritionLimits
import com.hawdietpro.app.domain.nutrition.WeightProjection
import com.hawdietpro.app.ui.MainViewModel
import com.hawdietpro.app.ui.components.AnimatedCount
import com.hawdietpro.app.ui.components.ArcGauge
import com.hawdietpro.app.ui.components.BentoCard
import com.hawdietpro.app.ui.components.GradeBadge
import com.hawdietpro.app.ui.components.IconTile
import com.hawdietpro.app.ui.components.LoadingBox
import com.hawdietpro.app.ui.components.MealPhoto
import com.hawdietpro.app.ui.components.MiniRing
import com.hawdietpro.app.ui.components.NoticeCard
import com.hawdietpro.app.ui.components.ProgressBar
import com.hawdietpro.app.ui.components.SectionTitle
import com.hawdietpro.app.ui.components.Sparkline
import com.hawdietpro.app.ui.components.StatusChip
import com.hawdietpro.app.ui.components.bouncyClick
import com.hawdietpro.app.ui.components.enterAnimation
import com.hawdietpro.app.ui.components.mealTypeColor
import com.hawdietpro.app.ui.feature.exercise.ExerciseSheet
import com.hawdietpro.app.ui.feature.suggest.MenuSuggestionSheet
import com.hawdietpro.app.ui.theme.HawTheme
import com.hawdietpro.app.ui.theme.MacroColors
import com.hawdietpro.app.ui.theme.NumberDisplay
import com.hawdietpro.app.ui.theme.NumberLarge
import com.hawdietpro.app.ui.theme.NumberMedium
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Porsi kalori yang disarankan per waktu makan (dari target harian). */
private val MEAL_SHARE = mapOf(
    MealType.SARAPAN to 0.25,
    MealType.MAKAN_SIANG to 0.35,
    MealType.MAKAN_MALAM to 0.30,
    MealType.CAMILAN to 0.10,
)

private val MEAL_HOUR = mapOf(
    MealType.SARAPAN to 7,
    MealType.MAKAN_SIANG to 12,
    MealType.MAKAN_MALAM to 19,
    MealType.CAMILAN to 16,
)

/** Semua aksi Beranda; dipisah agar tampilan bisa dirender tanpa ViewModel (pratinjau/snapshot). */
data class HomeActions(
    val onSelectDate: (LocalDate) -> Unit = {},
    val onAdd: (MealType?) -> Unit = {},
    val onManual: () -> Unit = {},
    val onBarcode: () -> Unit = {},
    val onAddExercise: () -> Unit = {},
    val onEditExercise: (Exercise) -> Unit = {},
    val onOpenMeal: (Long) -> Unit = {},
    val onCopyPrevious: (MealType) -> Unit = {},
    val onSuggest: () -> Unit = {},
    val onOpenQueue: () -> Unit = {},
    val onOpenUpdate: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenWeight: () -> Unit = {},
    val onOpenProfile: () -> Unit = {},
    val onOpenAchievements: () -> Unit = {},
    val onDismissAnnouncement: (String) -> Unit = {},
)

@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    onOpenCamera: (MealType?) -> Unit,
    onManual: (MealType?, Long?) -> Unit,
    onOpenMeal: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenUpdate: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWeight: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenBarcode: () -> Unit = {},
    showMessage: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val update by mainViewModel.updateState.collectAsStateWithLifecycle()
    val announcement by mainViewModel.announcement.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshDate()
        onPauseOrDispose { }
    }
    RequestNotificationPermissionOnce()

    var showSuggest by rememberSaveable { mutableStateOf(false) }
    if (showSuggest) MenuSuggestionSheet(onDismiss = { showSuggest = false })

    var showExercise by rememberSaveable { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<Exercise?>(null) }
    if (showExercise) {
        ExerciseSheet(
            date = state.date,
            editing = editingExercise,
            onDismiss = { showExercise = false; editingExercise = null },
            onMessage = showMessage,
        )
    }

    if (state.loading) {
        LoadingBox()
        return
    }

    fun timeFor(type: MealType?): Long {
        val hour = MEAL_HOUR[type ?: MealType.MAKAN_SIANG] ?: 12
        return state.date.atTime(LocalTime.of(hour, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    HomeContent(
        state = state,
        update = update,
        announcement = announcement,
        actions = HomeActions(
            onSelectDate = viewModel::select,
            onAdd = { type -> if (state.isToday) onOpenCamera(type) else onManual(type, timeFor(type)) },
            onManual = { if (state.isToday) onManual(null, null) else onManual(null, timeFor(null)) },
            onBarcode = onOpenBarcode,
            onAddExercise = { editingExercise = null; showExercise = true },
            onEditExercise = { editingExercise = it; showExercise = true },
            onOpenMeal = onOpenMeal,
            onCopyPrevious = viewModel::copyFromPreviousDay,
            onSuggest = { showSuggest = true },
            onOpenQueue = onOpenQueue,
            onOpenUpdate = onOpenUpdate,
            onOpenSettings = onOpenSettings,
            onOpenWeight = onOpenWeight,
            onOpenProfile = onOpenProfile,
            onOpenAchievements = onOpenAchievements,
            onDismissAnnouncement = mainViewModel::dismissAnnouncement,
        ),
    )
}

@Composable
fun HomeContent(
    state: HomeState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    update: UpdateState = UpdateState.Idle,
    announcement: Announcement? = null,
) {
    val hc = HawTheme.colors
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                // Kilau lembut di pojok kanan atas.
                drawRect(Brush.radialGradient(listOf(hc.glow, Color.Transparent), center = Offset(size.width * 0.9f, 0f), radius = size.width * 0.95f))
            },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") { TopHeader(state, actions.onOpenProfile, Modifier.enterAnimation(0)) }
            item(key = "strip") { WeekStrip(state.days, state.date, state.target?.calories, actions.onSelectDate, Modifier.enterAnimation(1)) }

            noticeItems(state, update, announcement, actions)

            item(key = "hero") { HeroCard(state, actions.onSuggest, Modifier.enterAnimation(2)) }
            item(key = "quick") { QuickActions(state.isToday, actions, Modifier.enterAnimation(3)) }

            item(key = "meals_title") {
                SectionTitle(
                    if (state.isToday) "Makanan hari ini" else "Makanan ${Format.dayMonth(state.date)}",
                    subtitle = if (state.meals.isEmpty()) "Belum ada catatan" else "${state.meals.size} catatan · ${Format.kcal(state.total.calories)}",
                    modifier = Modifier.enterAnimation(4),
                )
            }
            val types = MealType.entries
            types.forEachIndexed { index, type ->
                item(key = "type_${type.name}") {
                    MealTimelineRow(
                        type = type,
                        meals = state.byType[type].orEmpty(),
                        suggested = state.target?.let { (it.calories * (MEAL_SHARE[type] ?: 0.25)).roundToInt() },
                        previous = state.previousDayByType[type].orEmpty(),
                        isLast = index == types.lastIndex,
                        actions = actions,
                        modifier = Modifier.enterAnimation(5 + index),
                    )
                }
            }

            item(key = "bento") {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).enterAnimation(9), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExerciseTile(state.exercises, actions, Modifier.weight(1f).fillMaxHeight())
                    WeightTile(state.projection, actions.onOpenWeight, Modifier.weight(1f).fillMaxHeight())
                }
            }
            item(key = "energy") { EnergyCard(state.adaptive, state.target?.calories, actions.onOpenWeight, Modifier.enterAnimation(10)) }
            if (state.meals.isNotEmpty()) {
                item(key = "limits") { LimitsCard(state.total.sugar, state.total.sodium, Modifier.enterAnimation(11)) }
            }

            if (state.insights.isNotEmpty()) {
                item(key = "insight_title") { SectionTitle("Insight") }
                item(key = "insights") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.insights, key = { it.id }) { InsightCard(it, Modifier.width(280.dp)) }
                    }
                }
            }
            state.tip?.let { tip ->
                item(key = "tip") { NoticeCard("Tips hari ini", tip, Icons.Filled.TipsAndUpdates, MacroColors.Carbs) }
            }
        }
    }
}

// ─────────────────────────────── Header & tanggal ───────────────────────────────

private fun greeting(): String = when (LocalTime.now().hour) {
    in 4..10 -> "Selamat pagi"
    in 11..14 -> "Selamat siang"
    in 15..17 -> "Selamat sore"
    else -> "Selamat malam"
}

@Composable
private fun TopHeader(state: HomeState, onOpenProfile: () -> Unit, modifier: Modifier = Modifier) {
    val name = state.name
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "${Format.dayName(state.date)}, ${Format.dayMonth(state.date)} · ${greeting()}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (name.isNotBlank()) "Halo, ${name.substringBefore(' ')} 👋" else "Halo 👋",
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.streak > 0) {
            StatusChip("🔥 ${state.streak} hari", HawTheme.colors.warning, Modifier.padding(end = 8.dp))
        }
        val initials = name.trim().split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "🙂" }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, HawTheme.colors.exercise)))
                .bouncyClick(onClick = onOpenProfile),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

@Composable
private fun WeekStrip(days: List<DayChip>, selected: LocalDate, target: Int?, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(days.size) {
        if (days.isNotEmpty()) listState.scrollToItem(days.lastIndex)
    }
    LazyRow(modifier, state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(days, key = { it.date.toEpochDay() }) { day ->
            DayPill(day, day.date == selected, target, onClick = { onSelect(day.date) })
        }
    }
}

@Composable
private fun DayPill(day: DayChip, selected: Boolean, target: Int?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hc = HawTheme.colors
    val bg by animateColorAsState(if (selected) cs.primary else hc.card, label = "dayBg")
    val fg = if (selected) cs.onPrimary else cs.onSurface
    val sub = if (selected) cs.onPrimary.copy(alpha = 0.75f) else cs.onSurfaceVariant
    // Sama dengan kartu utama: jatah = target + kalori olahraga hari itu.
    val budget = target?.let { it + day.exercise }
    val ratio = if (budget != null && budget > 0) (day.calories / budget).toFloat() else if (day.calories > 0) 1f else 0f
    val ring = when {
        selected -> cs.onPrimary
        ratio > 1.1f -> cs.error
        else -> cs.primary
    }
    val track = if (selected) cs.onPrimary.copy(alpha = 0.22f) else cs.surfaceContainerHighest
    val isToday = day.date == LocalDate.now()
    Column(
        Modifier
            .width(50.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(BorderStroke(1.dp, if (selected) Color.Transparent else if (isToday) cs.primary.copy(alpha = 0.6f) else hc.cardBorder), RoundedCornerShape(18.dp))
            .bouncyClick(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(Format.dayName(day.date), style = MaterialTheme.typography.labelSmall, color = sub)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(32.dp)) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                if (ratio > 0f) drawArc(ring, -90f, 360f * ratio.coerceAtMost(1f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text("${day.date.dayOfMonth}", style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────────── Pemberitahuan ───────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.noticeItems(
    state: HomeState,
    update: UpdateState,
    announcement: Announcement?,
    actions: HomeActions,
) {
    when (update) {
        is UpdateState.Available, is UpdateState.Downloading, is UpdateState.ReadyToInstall -> item(key = "update") {
            val text = when (update) {
                is UpdateState.ReadyToInstall -> "Versi ${update.release.versionName} siap dipasang. Ketuk untuk install."
                is UpdateState.Downloading -> "Mengunduh versi ${update.release.versionName}… ${(update.progress * 100).roundToInt()}%"
                is UpdateState.Available -> "Versi ${update.release.versionName} tersedia. Ketuk untuk melihat perubahan."
                else -> ""
            }
            NoticeCard("Update tersedia", text, Icons.Filled.SystemUpdate, MaterialTheme.colorScheme.tertiary, onClick = actions.onOpenUpdate)
        }
        else -> Unit
    }
    announcement?.let { a ->
        item(key = "announcement") {
            NoticeCard(
                a.title,
                a.message,
                Icons.Filled.Campaign,
                if (a.level == "warning") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                trailing = { TextButton(onClick = { actions.onDismissAnnouncement(a.id) }) { Text("Tutup") } },
            )
        }
    }
    if (!state.hasApiKey) {
        item(key = "apikey") {
            NoticeCard(
                "Isi API key Gemini",
                "Analisa foto butuh API key gratis dari Google AI Studio. Ketuk untuk mengisi.",
                Icons.Filled.Key,
                MaterialTheme.colorScheme.error,
                onClick = actions.onOpenSettings,
            )
        }
    }
    if (state.newAchievements.isNotEmpty()) {
        item(key = "badges") {
            val fresh = state.newAchievements
            NoticeCard(
                if (fresh.size == 1) "Badge baru: ${fresh.first().title}" else "${fresh.size} badge baru terbuka!",
                fresh.joinToString("  ") { it.emoji } + "  · Ketuk untuk melihat koleksi",
                Icons.Filled.EmojiEvents,
                HawTheme.colors.warning,
                onClick = actions.onOpenAchievements,
            )
        }
    }
    if (state.pendingCount > 0) {
        item(key = "pending") {
            NoticeCard(
                if (state.pendingFailed > 0) "${state.pendingFailed} foto gagal dianalisa" else "${state.pendingCount} foto sedang dianalisa",
                if (state.pendingFailed > 0) "Ketuk untuk coba lagi atau isi manual." else "Hasil tercatat otomatis & muncul notifikasi.",
                Icons.Filled.CloudQueue,
                if (state.pendingFailed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                onClick = actions.onOpenQueue,
            )
        }
    }
}

// ─────────────────────────────── Hero ───────────────────────────────

@Composable
private fun HeroCard(state: HomeState, onSuggest: () -> Unit, modifier: Modifier = Modifier) {
    val hc = HawTheme.colors
    val budget = state.budget ?: 2000
    val eaten = state.total.calories
    val remaining = state.remaining ?: (budget - eaten.roundToInt())
    val over = remaining < 0
    val target = state.target
    val statusText = when {
        eaten <= 0.0 -> "Mulai catat"
        over -> "Melebihi jatah"
        else -> "${(eaten / budget * 100).roundToInt()}% terpakai"
    }
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(hc.heroGradient)
            .drawBehind {
                // Gelembung cahaya agar gradasi terasa "hidup".
                drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width * 0.15f, size.height * 0.1f), radius = size.width * 0.6f))
                drawRect(Brush.radialGradient(listOf(Color(0xFF7CF5C9).copy(alpha = 0.18f), Color.Transparent), center = Offset(size.width, size.height * 0.85f), radius = size.width * 0.7f))
            }
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.isToday) "Sisa kalori hari ini" else "Sisa kalori ${Format.dayMonth(state.date)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = hc.heroContent,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(statusText, Color.White, onColor = true, icon = if (over) Icons.Filled.LocalFireDepartment else Icons.Outlined.Flag)
            }
            ArcGauge(
                progress = (eaten / budget).toFloat(),
                bonus = (state.exerciseKcal / budget).toFloat(),
                size = 232.dp,
                stroke = 18.dp,
                track = hc.heroTrack,
                fill = Brush.linearGradient(listOf(Color(0xFFB8FFE2), Color.White)),
                bonusColor = Color(0xFFB9C2FF),
                overColor = Color(0xFFFFB4A6),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedCount(abs(remaining), NumberDisplay, if (over) Color(0xFFFFD2C9) else hc.heroContent)
                    Text(if (over) "kkal berlebih" else "kkal tersisa", style = MaterialTheme.typography.labelLarge, color = hc.heroContentSub)
                }
            }
            Row(Modifier.fillMaxWidth().offset(y = (-26).dp), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroStat(Icons.Filled.Restaurant, "Masuk", Format.kcalNumber(eaten))
                HeroStat(Icons.AutoMirrored.Filled.DirectionsRun, "Olahraga", if (state.exerciseKcal > 0) "+${Format.kcalNumber(state.exerciseKcal)}" else "0")
                HeroStat(Icons.Outlined.Flag, "Jatah", Format.grouped(budget))
            }
            if (target != null) {
                Row(Modifier.fillMaxWidth().offset(y = (-10).dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    HeroMacroRing("Protein", state.total.protein, target.proteinG, Color(0xFFFF9C8F))
                    HeroMacroRing("Karbo", state.total.carbs, target.carbsG, Color(0xFFFFD27A))
                    HeroMacroRing("Lemak", state.total.fat, target.fatG, Color(0xFFA9C4FF))
                    HeroMacroRing("Serat", state.total.fiber, target.fiberG, Color(0xFFB9F6CA))
                }
            }
            if (state.isToday && state.hasApiKey) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.13f))
                        .bouncyClick(onClick = onSuggest)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = hc.heroContent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Bingung mau makan apa?", style = MaterialTheme.typography.labelLarge, color = hc.heroContent, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                remaining > 0 -> "Saran menu AI untuk sisa ${Format.grouped(remaining)} kkal"
                                else -> "Saran menu ringan, jatah hari ini sudah terpenuhi"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = hc.heroContentSub,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = hc.heroContent)
                }
            }
        }
    }
}

@Composable
private fun HeroStat(icon: ImageVector, label: String, value: String) {
    val hc = HawTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = hc.heroContentSub, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = hc.heroContentSub)
        }
        Text(value, style = NumberMedium, color = hc.heroContent)
    }
}

@Composable
private fun HeroMacroRing(label: String, value: Double, target: Int, color: Color) {
    val hc = HawTheme.colors
    val fraction = if (target > 0) (value / target).toFloat() else 0f
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(74.dp)) {
        MiniRing(fraction, color, size = 50.dp, stroke = 5.dp, track = hc.heroTrack) {
            Text("${(fraction * 100).roundToInt().coerceAtMost(999)}%", style = MaterialTheme.typography.labelMedium, color = hc.heroContent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = hc.heroContent)
        Text("${value.roundToInt()}/${target} g", style = MaterialTheme.typography.labelSmall, color = hc.heroContentSub)
    }
}

// ─────────────────────────────── Aksi cepat ───────────────────────────────

@Composable
private fun QuickActions(isToday: Boolean, actions: HomeActions, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val hc = HawTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickTile(Icons.Filled.CameraAlt, if (isToday) "Foto" else "Tambah", cs.primary, Modifier.weight(1f), primary = true) { actions.onAdd(null) }
        QuickTile(Icons.Filled.QrCodeScanner, "Barcode", hc.food, Modifier.weight(1f), onClick = actions.onBarcode)
        QuickTile(Icons.Filled.EditNote, "Manual", MacroColors.Carbs, Modifier.weight(1f), onClick = actions.onManual)
        QuickTile(Icons.AutoMirrored.Filled.DirectionsRun, "Olahraga", hc.exercise, Modifier.weight(1f), onClick = actions.onAddExercise)
    }
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, tint: Color, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    BentoCard(
        modifier,
        onClick = onClick,
        color = if (primary) tint else null,
        padding = 10.dp,
        corner = 20.dp,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (primary) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                }
            } else {
                IconTile(icon, tint, size = 40.dp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────── Linimasa makan ───────────────────────────────

@Composable
private fun MealTimelineRow(
    type: MealType,
    meals: List<Meal>,
    suggested: Int?,
    previous: List<Meal>,
    isLast: Boolean,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val color = mealTypeColor(type)
    val line = MaterialTheme.colorScheme.outlineVariant
    val done = meals.isNotEmpty()
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Simpul linimasa + garis penghubung.
        Column(Modifier.width(40.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (done) color else color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Text(type.emoji, style = MaterialTheme.typography.titleSmall) }
            if (!isLast) {
                Canvas(Modifier.width(2.dp).weight(1f).padding(vertical = 4.dp)) {
                    drawLine(
                        line,
                        Offset(size.width / 2, 0f),
                        Offset(size.width / 2, size.height),
                        strokeWidth = size.width,
                        pathEffect = if (done) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 12.dp)) {
            BentoCard(padding = 12.dp, corner = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(type.label, style = MaterialTheme.typography.titleSmall)
                        val kcal = meals.sumOf { it.total.calories }
                        Text(
                            when {
                                done -> Format.kcal(kcal) + (suggested?.let { " dari ±${Format.grouped(it)}" } ?: "")
                                suggested != null -> "Saran ±${Format.grouped(suggested)} kkal"
                                else -> "Belum dicatat"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (done) color else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.15f))
                            .bouncyClick { actions.onAdd(type) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, contentDescription = "Tambah ${type.label}", tint = color, modifier = Modifier.size(20.dp)) }
                }
                if (!done && previous.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(color.copy(alpha = 0.09f))
                            .clickable { actions.onCopyPrevious(type) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, tint = color, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Sama seperti kemarin? " + previous.joinToString(", ") { it.menuName },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text("Salin", style = MaterialTheme.typography.labelLarge, color = color)
                    }
                }
                meals.forEach { meal ->
                    Spacer(Modifier.height(10.dp))
                    MealLine(meal) { actions.onOpenMeal(meal.id) }
                }
            }
        }
    }
}

@Composable
private fun MealLine(meal: Meal, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            MealPhoto(meal.photoPath, size = 52.dp, corner = 14.dp)
            meal.grade?.let { GradeBadge(it, size = 20.dp, modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(meal.menuName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Format.time(meal.eatenAt)} · P ${meal.total.protein.roundToInt()} · K ${meal.total.carbs.roundToInt()} · L ${meal.total.fat.roundToInt()} g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Format.kcalNumber(meal.total.calories), style = NumberMedium)
            Text("kkal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─────────────────────────────── Bento ───────────────────────────────

@Composable
private fun ExerciseTile(exercises: List<Exercise>, actions: HomeActions, modifier: Modifier = Modifier) {
    val accent = HawTheme.colors.exercise
    BentoCard(modifier, onClick = actions.onAddExercise, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.AutoMirrored.Filled.DirectionsRun, accent, size = 36.dp)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(28.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, "Tambah olahraga", tint = accent, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Olahraga", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val total = exercises.sumOf { it.calories }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (total > 0) "+${Format.kcalNumber(total)}" else "0", style = NumberLarge, color = if (total > 0) accent else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(4.dp))
            Text("kkal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
        if (exercises.isEmpty()) {
            Text("Kalori olahraga menambah jatah makan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Spacer(Modifier.height(4.dp))
            exercises.take(2).forEach { e ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { actions.onEditExercise(e) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ExerciseCatalog.find(e.activityId)?.emoji ?: "💪", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(6.dp))
                    Text("${e.name} · ${e.durationMin}m", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (exercises.size > 2) Text("+${exercises.size - 2} lainnya", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeightTile(p: WeightProjection?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MacroColors.Protein
    BentoCard(modifier, onClick = onClick, padding = 14.dp) {
        val change = p?.estimatedChange ?: 0.0
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.MonitorWeight, accent, size = 36.dp)
            Spacer(Modifier.weight(1f))
            if (p != null) {
                val trendIcon = when {
                    change < -0.05 -> Icons.AutoMirrored.Filled.TrendingDown
                    change > 0.05 -> Icons.AutoMirrored.Filled.TrendingUp
                    else -> Icons.AutoMirrored.Filled.TrendingFlat
                }
                StatusChip(signedKg(change), if (change <= 0) HawTheme.colors.success else HawTheme.colors.warning, icon = trendIcon)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Berat (perkiraan)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (p == null) {
            Text("Catat berat", style = NumberLarge)
            Text("Untuk melihat tren & perkiraan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Format.oneDecimal(p.estimatedToday), style = NumberLarge)
                Spacer(Modifier.width(4.dp))
                Text("kg", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
            }
            val series = (listOf(p.startWeight) + p.days.map { it.estimatedWeight }).takeLast(21)
            Spacer(Modifier.height(6.dp))
            Sparkline(series, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth().height(34.dp))
            Text("sejak ${Format.dayMonth(p.startDate)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun signedKg(v: Double): String = (if (v > 0) "+" else if (v < 0) "−" else "") + Format.oneDecimal(abs(v)) + " kg"

/** Kebutuhan kalori adaptif: seberapa banyak tubuh membakar menurut data timbangan + catatan makan. */
@Composable
fun EnergyCard(a: AdaptiveTdee?, targetCalories: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (a == null) return
    val hc = HawTheme.colors
    val accent = hc.food
    val statusColor = when (a.status) {
        AdaptiveTdee.Status.AKURAT -> hc.success
        AdaptiveTdee.Status.BELAJAR -> hc.warning
        AdaptiveTdee.Status.BELUM_CUKUP -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    BentoCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.Bolt, accent, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Kebutuhan kalorimu", style = MaterialTheme.typography.titleSmall)
                Text("Dipelajari dari timbangan & catatan makan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusChip(a.status.label, statusColor)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedCount(a.tdee, NumberLarge, MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(4.dp))
            Text("kkal/hari", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
            Spacer(Modifier.weight(1f))
            targetCalories?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text("Target makan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${Format.grouped(it)} kkal", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        if (a.status != AdaptiveTdee.Status.BELUM_CUKUP) {
            val diff = a.differenceFromFormula
            Text(
                "Rumus ${Format.grouped(a.formulaTdee)} kkal · data ${if (diff >= 0) "+" else "−"}${Format.grouped(abs(diff))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressBar(a.confidence.toFloat(), statusColor.takeIf { a.status != AdaptiveTdee.Status.BELUM_CUKUP } ?: MaterialTheme.colorScheme.primary, Modifier.weight(1f), height = 6.dp)
            Spacer(Modifier.width(10.dp))
            Text("Keyakinan ${(a.confidence * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        a.hint?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Batas harian gula & garam (Kemenkes): ringkas, satu kartu. */
@Composable
private fun LimitsCard(sugar: Double, sodium: Double, modifier: Modifier = Modifier) {
    BentoCard(modifier.fillMaxWidth(), padding = 14.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LimitBar("🍬 Gula", sugar, NutritionLimits.SUGAR_G, "g", Modifier.weight(1f))
            LimitBar("🧂 Natrium", sodium, NutritionLimits.SODIUM_MG, "mg", Modifier.weight(1f))
        }
    }
}

@Composable
private fun LimitBar(label: String, value: Double, limit: Double, unit: String, modifier: Modifier) {
    val ratio = (value / limit).toFloat()
    val color = when {
        ratio > 1f -> MaterialTheme.colorScheme.error
        ratio > 0.75f -> HawTheme.colors.warning
        else -> HawTheme.colors.success
    }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Format.grouped(value.roundToInt()), style = NumberMedium, color = color)
            Text(" / ${Format.grouped(limit.roundToInt())} $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(ratio, color, height = 6.dp)
    }
}

@Composable
fun InsightCard(insight: Insight, modifier: Modifier = Modifier) {
    val (accent, icon) = when (insight.severity) {
        "positive" -> HawTheme.colors.success to Icons.Filled.EmojiEvents
        "warning" -> HawTheme.colors.warning to Icons.Filled.Lightbulb
        else -> MaterialTheme.colorScheme.tertiary to Icons.Filled.Lightbulb
    }
    BentoCard(modifier, padding = 14.dp, corner = 20.dp) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(icon, accent, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Text(insight.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        }
    }
}

@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < 33) return
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
