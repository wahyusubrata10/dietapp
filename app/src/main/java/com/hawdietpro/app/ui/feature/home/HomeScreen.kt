package com.hawdietpro.app.ui.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.data.update.UpdateState
import com.hawdietpro.app.domain.insight.Insight
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.ui.MainViewModel
import com.hawdietpro.app.ui.components.Banner
import com.hawdietpro.app.ui.components.CalorieRing
import com.hawdietpro.app.ui.components.EmojiBadge
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.HeroMacro
import com.hawdietpro.app.ui.components.LoadingBox
import com.hawdietpro.app.ui.components.MealRow
import com.hawdietpro.app.ui.components.SectionHeader
import com.hawdietpro.app.ui.components.SoftProgress
import com.hawdietpro.app.domain.nutrition.NutritionLimits
import com.hawdietpro.app.ui.components.mealTypeColor
import com.hawdietpro.app.ui.feature.suggest.MenuSuggestionSheet
import com.hawdietpro.app.ui.feature.weight.WeightEstimateCompact
import com.hawdietpro.app.ui.theme.HawTheme
import com.hawdietpro.app.ui.theme.MacroColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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

    if (state.loading) {
        LoadingBox()
        return
    }

    fun addFor(type: MealType?) {
        if (state.isToday) {
            onOpenCamera(type)
        } else {
            val hour = MEAL_HOUR[type ?: MealType.MAKAN_SIANG] ?: 12
            onManual(type, state.date.atTime(LocalTime.of(hour, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Header(state.name, onOpenProfile) }

        item {
            DateStrip(
                days = state.days,
                selected = state.date,
                target = state.target?.calories,
                onSelect = viewModel::select,
            )
        }

        when (val u = update) {
            is UpdateState.Available, is UpdateState.Downloading, is UpdateState.ReadyToInstall -> item {
                val text = when (u) {
                    is UpdateState.ReadyToInstall -> "Versi ${u.release.versionName} siap dipasang. Ketuk untuk install."
                    is UpdateState.Downloading -> "Mengunduh versi ${u.release.versionName}… ${(u.progress * 100).roundToInt()}%"
                    is UpdateState.Available -> "Versi ${u.release.versionName} tersedia. Ketuk untuk melihat perubahan."
                    else -> ""
                }
                Banner(
                    text = text,
                    title = "Update tersedia",
                    icon = Icons.Filled.SystemUpdate,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = onOpenUpdate,
                )
            }
            else -> Unit
        }

        announcement?.let { a ->
            item {
                Banner(
                    text = a.message,
                    title = a.title,
                    icon = Icons.Filled.Campaign,
                    container = if (a.level == "warning") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    content = if (a.level == "warning") MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    actions = { TextButton(onClick = { mainViewModel.dismissAnnouncement(a.id) }) { Text("Tutup") } },
                )
            }
        }

        if (!state.hasApiKey) {
            item {
                Banner(
                    title = "Isi API key Gemini",
                    text = "Analisa foto butuh API key gratis dari Google AI Studio. Ketuk untuk mengisi.",
                    icon = Icons.Filled.Key,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = onOpenSettings,
                )
            }
        }

        if (state.newAchievements.isNotEmpty()) {
            item {
                val fresh = state.newAchievements
                Banner(
                    title = if (fresh.size == 1) "Badge baru: ${fresh.first().emoji} ${fresh.first().title}" else "${fresh.size} badge baru terbuka!",
                    text = if (fresh.size == 1) fresh.first().description + ". Ketuk untuk melihat koleksi." else fresh.joinToString("  ") { it.emoji } + "  · Ketuk untuk melihat.",
                    icon = Icons.Filled.EmojiEvents,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = onOpenAchievements,
                )
            }
        }

        item { HeroCard(state, onSuggest = { showSuggest = true }) }

        if (state.meals.isNotEmpty()) item { LimitsCard(state.total.sugar, state.total.sodium) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(
                    Icons.Filled.CameraAlt,
                    if (state.isToday) "Foto makanan" else "Tambah",
                    primary = true,
                    modifier = Modifier.weight(1.4f),
                ) { addFor(null) }
                QuickAction(Icons.Filled.EditNote, "Manual", modifier = Modifier.weight(1f)) {
                    if (state.isToday) onManual(null, null) else addFor(null)
                }
                QuickAction(Icons.Filled.MonitorWeight, "Berat", modifier = Modifier.weight(1f), onClick = onOpenWeight)
            }
        }

        if (state.pendingCount > 0) {
            item {
                Banner(
                    title = "${state.pendingCount} foto di antrian",
                    text = "Akan dianalisa otomatis saat online. Ketuk untuk melihat.",
                    icon = Icons.Filled.CloudQueue,
                    onClick = onOpenQueue,
                )
            }
        }

        state.projection?.takeIf { state.isToday && (it.days.isNotEmpty() || it.latestActual != null) }?.let { proj ->
            item { WeightEstimateCompact(proj, onClick = onOpenWeight) }
        }

        item {
            SectionHeader(
                if (state.isToday) "Makanan hari ini" else "Makanan ${Format.dayMonth(state.date)}",
                subtitle = "${state.meals.size} catatan · ${Format.kcal(state.total.calories)}",
            )
        }

        MealType.entries.forEach { type ->
            val list = state.byType[type].orEmpty()
            item(key = "type_${type.name}") {
                MealTypeCard(
                    type = type,
                    meals = list,
                    suggested = state.target?.let { (it.calories * (MEAL_SHARE[type] ?: 0.25)).roundToInt() },
                    onAdd = { addFor(type) },
                    onOpenMeal = onOpenMeal,
                    previous = state.previousDayByType[type].orEmpty(),
                    onCopyPrevious = { viewModel.copyFromPreviousDay(type) },
                )
            }
        }

        if (state.insights.isNotEmpty()) {
            item { SectionHeader("Insight") }
            items(state.insights, key = { it.id }) { InsightCard(it) }
        }

        state.tip?.let { tip ->
            item { Banner(text = tip, title = "Tips hari ini", icon = Icons.Filled.TipsAndUpdates) }
        }
    }
}

// ─────────────────────────────── Bagian-bagian ───────────────────────────────

private fun greeting(): String = when (LocalTime.now().hour) {
    in 4..10 -> "Selamat pagi"
    in 11..14 -> "Selamat siang"
    in 15..17 -> "Selamat sore"
    else -> "Selamat malam"
}

@Composable
private fun Header(name: String, onOpenProfile: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                greeting() + if (name.isNotBlank()) "," else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (name.isNotBlank()) "${name.substringBefore(' ')} 👋" else "👋",
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(onClick = onOpenProfile, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(46.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    name.trim().split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "🙂" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun DateStrip(days: List<DayChip>, selected: LocalDate, target: Int?, onSelect: (LocalDate) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(days.size) {
        if (days.isNotEmpty()) listState.scrollToItem(days.lastIndex)
    }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(days, key = { it.date.toEpochDay() }) { day ->
            DayItem(day, day.date == selected, target, onClick = { onSelect(day.date) })
        }
    }
}

@Composable
private fun DayItem(day: DayChip, selected: Boolean, target: Int?, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(if (selected) primary else HawTheme.colors.card, label = "dayBg")
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val sub = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    val ratio = if (target != null && target > 0) (day.calories / target).toFloat() else if (day.calories > 0) 1f else 0f
    val ringColor = when {
        ratio > 1.1f -> if (selected) Color.White else MaterialTheme.colorScheme.error
        else -> if (selected) Color.White else primary
    }
    val track = if (selected) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHighest
    val isToday = day.date == LocalDate.now()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = bg,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, HawTheme.colors.cardBorder),
        modifier = Modifier.width(52.dp),
    ) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Format.dayName(day.date), style = MaterialTheme.typography.labelSmall, color = sub)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(34.dp)) {
                    val stroke = 3.dp.toPx()
                    val inset = stroke / 2
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                    if (ratio > 0f) drawArc(ringColor, -90f, 360f * ratio.coerceAtMost(1f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text("${day.date.dayOfMonth}", style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HeroCard(state: HomeState, onSuggest: () -> Unit) {
    val hc = HawTheme.colors
    val target = state.target
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(hc.heroGradient)
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CalorieRing(
                    consumed = state.total.calories,
                    target = target?.calories ?: 2000,
                    size = 148.dp,
                    stroke = 13.dp,
                    trackColor = hc.heroTrack,
                    progressColor = Color.White,
                    overColor = Color(0xFFFFC9C2),
                    textColor = hc.heroContent,
                    subTextColor = hc.heroContentSub,
                )
                Spacer(Modifier.width(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroStat("Kalori masuk", Format.kcal(state.total.calories))
                    HeroStat("Target harian", Format.kcal((target?.calories ?: 0).toDouble()))
                    HeroStat("Waktu makan", "${state.meals.size}× dicatat")
                }
            }
            if (target != null) {
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HeroMacro("Protein", state.total.protein, target.proteinG, MacroColors.Protein, hc.heroContent, hc.heroContentSub, hc.heroTrack, Modifier.weight(1f))
                    HeroMacro("Karbo", state.total.carbs, target.carbsG, MacroColors.Carbs, hc.heroContent, hc.heroContentSub, hc.heroTrack, Modifier.weight(1f))
                    HeroMacro("Lemak", state.total.fat, target.fatG, Color(0xFF9FC1FF), hc.heroContent, hc.heroContentSub, hc.heroTrack, Modifier.weight(1f))
                    HeroMacro("Serat", state.total.fiber, target.fiberG, Color(0xFFB9F6CA), hc.heroContent, hc.heroContentSub, hc.heroTrack, Modifier.weight(1f))
                }
            }
            if (state.isToday && state.hasApiKey) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    onClick = onSuggest,
                    shape = RoundedCornerShape(16.dp),
                    color = hc.heroTrack,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = hc.heroContent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Bingung mau makan apa?", style = MaterialTheme.typography.labelLarge, color = hc.heroContent, fontWeight = FontWeight.Bold)
                            val remaining = target?.let { it.calories - state.total.calories.roundToInt() }
                            Text(
                                when {
                                    remaining == null -> "Minta saran menu dari AI"
                                    remaining > 0 -> "Saran menu AI untuk sisa ${Format.grouped(remaining)} kkal"
                                    else -> "Saran menu ringan, target hari ini sudah terpenuhi"
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
}

/** Batas harian gula & garam (Kemenkes): ringkas, satu kartu. */
@Composable
private fun LimitsCard(sugar: Double, sodium: Double) {
    HawCard(contentPadding = 14.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("${Format.grouped(value.roundToInt())}/${Format.grouped(limit.roundToInt())} $unit", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(6.dp))
        SoftProgress(ratio, color, height = 7.dp)
    }
}

@Composable
private fun HeroStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = HawTheme.colors.heroContentSub)
        Text(value, style = MaterialTheme.typography.titleMedium, color = HawTheme.colors.heroContent)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (primary) MaterialTheme.colorScheme.secondary else HawTheme.colors.card,
        contentColor = if (primary) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
        border = if (primary) null else androidx.compose.foundation.BorderStroke(1.dp, HawTheme.colors.cardBorder),
        modifier = modifier.height(56.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MealTypeCard(
    type: MealType,
    meals: List<com.hawdietpro.app.domain.model.Meal>,
    suggested: Int?,
    onAdd: () -> Unit,
    onOpenMeal: (Long) -> Unit,
    previous: List<com.hawdietpro.app.domain.model.Meal> = emptyList(),
    onCopyPrevious: () -> Unit = {},
) {
    val color = mealTypeColor(type)
    val kcal = meals.sumOf { it.total.calories }
    HawCard(contentPadding = 14.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(type.emoji, color, size = 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(type.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            meals.isNotEmpty() -> "${Format.kcal(kcal)}" + (suggested?.let { " dari saran ±${Format.grouped(it)}" } ?: "")
                            suggested != null -> "Saran ±${Format.grouped(suggested)} kkal"
                            else -> "Belum dicatat"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(
                    onClick = onAdd,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = color.copy(alpha = 0.14f), contentColor = color),
                ) { Icon(Icons.Filled.Add, contentDescription = "Tambah ${type.label}") }
            }
            if (meals.isEmpty() && previous.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = onCopyPrevious,
                    shape = MaterialTheme.shapes.medium,
                    color = color.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ContentCopy, null, tint = color, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Sama seperti kemarin?", style = MaterialTheme.typography.labelLarge, color = color)
                            Text(
                                previous.joinToString(", ") { it.menuName } + " · " + Format.kcal(previous.sumOf { it.total.calories }),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text("Salin", style = MaterialTheme.typography.labelLarge, color = color)
                    }
                }
            }
            if (meals.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                meals.forEachIndexed { i, meal ->
                    if (i > 0) HorizontalDivider(color = HawTheme.colors.cardBorder)
                    MealRow(meal, onClick = { onOpenMeal(meal.id) }, showType = false)
                }
            }
        }
    }
}

@Composable
fun InsightCard(insight: Insight, modifier: Modifier = Modifier) {
    val (container, content) = when (insight.severity) {
        "positive" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "warning" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
    }
    Banner(text = insight.message, icon = Icons.Filled.Lightbulb, container = container, content = content, modifier = modifier)
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
