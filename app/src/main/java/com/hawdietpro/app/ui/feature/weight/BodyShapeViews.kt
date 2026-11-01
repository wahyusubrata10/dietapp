package com.hawdietpro.app.ui.feature.weight

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.domain.body.BodyMeshBuilder
import com.hawdietpro.app.domain.body.BodyParams
import com.hawdietpro.app.domain.body.BodyRenderer
import com.hawdietpro.app.domain.body.BodySnapshot
import com.hawdietpro.app.domain.body.BodyTimeline
import com.hawdietpro.app.domain.nutrition.BmiClass
import com.hawdietpro.app.domain.nutrition.TdeeCalculator
import com.hawdietpro.app.domain.nutrition.WeightProjection
import com.hawdietpro.app.ui.components.HawCard
import com.hawdietpro.app.ui.components.LocalReduceMotion
import com.hawdietpro.app.ui.components.NoticeCard
import com.hawdietpro.app.ui.components.SmoothEasing
import com.hawdietpro.app.ui.theme.BodyColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Pilihan "berat target" disimpan sebagai nilai khusus di slot tanggal. */
private const val TARGET_SLOT = Long.MIN_VALUE

/** Sudut istirahat: sedikit serong agar bentuk depan & samping sama-sama terlihat. */
private const val REST_YAW = 0.45f

/** Radian per piksel geseran jari. */
private const val DRAG_SPEED = 0.012f

/** Batas tinggi bitmap render (piksel) agar putaran tetap mulus di HP lama. */
private const val MAX_RENDER_HEIGHT = 640f

/**
 * Perbandingan bentuk tubuh 3D pada dua titik waktu (mis. awal vs hari ini, atau hari ini vs proyeksi).
 * Bentuk dihitung dari berat pada tanggal itu, tinggi & jenis kelamin di profil — perkiraan visual,
 * bukan hasil pindai tubuh.
 */
@Composable
fun BodyShapeSection(p: WeightProjection) {
    val cs = MaterialTheme.colorScheme
    if (p.heightCm !in 100.0..250.0) {
        NoticeCard("Tinggi badan belum diisi", "Isi tinggi badan di profil untuk melihat bentuk tubuh 3D.", Icons.Filled.Height, cs.tertiary)
        return
    }
    val today = BodyTimeline.today(p)
    var slotA by rememberSaveable { mutableLongStateOf(p.startDate.toEpochDay()) }
    var slotB by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    // Pengguna baru (awal = hari ini): bandingkan dengan proyeksi / target agar tetap bermakna.
    LaunchedEffect(p.startDate, today) {
        if (slotA == slotB && slotA == today.toEpochDay()) {
            slotB = if (p.forecast.isNotEmpty()) BodyTimeline.lastDate(p).toEpochDay() else if (p.targetWeight != null) TARGET_SLOT else slotB
        }
    }
    val a = remember(p, slotA) { snapshot(p, slotA) }
    val b = remember(p, slotB) { snapshot(p, slotB) }

    val reduceMotion = LocalReduceMotion.current
    var introDone by rememberSaveable { mutableStateOf(false) }
    val yaw = remember { Animatable(if (reduceMotion || introDone) REST_YAW else REST_YAW - 2 * PI.toFloat()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        // Satu putaran perkenalan agar terlihat bahwa model bisa diputar.
        try {
            if (!introDone && !reduceMotion) yaw.animateTo(REST_YAW, tween(2200, easing = SmoothEasing))
        } finally {
            // Tetap ditandai selesai walau putaran disela jari pengguna.
            introDone = true
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HawCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SlotPicker(p, slotA, BodyColors.Compare, { slotA = it }, Modifier.weight(1f))
                    SlotPicker(p, slotB, BodyColors.Current, { slotB = it }, Modifier.weight(1f))
                }

                val description = "Visualisasi 3D bentuk tubuh. Kiri ${slotLabel(p, slotA)}, ${a?.let { Format.kg(it.weightKg) } ?: "-"}. " +
                    "Kanan ${slotLabel(p, slotB)}, ${b?.let { Format.kg(it.weightKg) } ?: "-"}. Geser ke samping untuk memutar."
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.verticalGradient(listOf(cs.surfaceContainerHigh, cs.surfaceContainerLow)))
                        .semantics {
                            contentDescription = description
                            customActions = listOf(
                                CustomAccessibilityAction("Putar 45 derajat") {
                                    scope.launch { yaw.animateTo(yaw.value + PI.toFloat() / 4, tween(400)) }
                                    true
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            val tracker = VelocityTracker()
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    tracker.resetTracking()
                                    scope.launch { yaw.stop() }
                                },
                                onDragEnd = {
                                    val v = tracker.calculateVelocity().x * DRAG_SPEED
                                    scope.launch { yaw.animateDecay(v, exponentialDecay(frictionMultiplier = 1.6f)) }
                                },
                            ) { change, dx ->
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                scope.launch { yaw.snapTo(yaw.value + dx * DRAG_SPEED) }
                            }
                        }
                        // Ketuk dua kali: kembali ke sudut awal.
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { scope.launch { yaw.animateTo(nearestRest(yaw.value), tween(500, easing = SmoothEasing)) } })
                        },
                ) {
                    Row(Modifier.fillMaxSize()) {
                        val params = { s: BodySnapshot? -> s?.let { BodyParams(p.heightCm, it.weightKg, p.sex) } }
                        BodyFigure(params(a), { yaw.value }, BodyColors.Compare, p.heightCm, Modifier.weight(1f).fillMaxHeight())
                        BodyFigure(params(b), { yaw.value }, BodyColors.Current, p.heightCm, Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Sync, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Geser untuk memutar 360°", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SnapshotInfo(a, p, Modifier.weight(1f))
                    SnapshotInfo(b, p, Modifier.weight(1f))
                }
                if (a != null && b != null) {
                    Text(deltaText(a, b, p), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text(
            "Bentuk tubuh diperkirakan dari berat, tinggi, dan jenis kelamin: perut cenderung bertambah lebih dulu pada pria, " +
                "pinggul & paha pada wanita. Ini gambaran rata-rata, bukan hasil pindai — bentuk asli dipengaruhi otot, usia, dan genetik.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** Sudut istirahat terdekat (kelipatan 360° + sudut awal) agar tidak berputar jauh. */
private fun nearestRest(current: Float): Float {
    val turn = 2 * PI.toFloat()
    return REST_YAW + turn * ((current - REST_YAW) / turn).roundToInt()
}

private fun snapshot(p: WeightProjection, slot: Long): BodySnapshot? =
    if (slot == TARGET_SLOT) BodyTimeline.target(p) else BodyTimeline.at(p, LocalDate.ofEpochDay(slot))

private fun slotLabel(p: WeightProjection, slot: Long): String {
    if (slot == TARGET_SLOT) return "Berat target"
    val date = LocalDate.ofEpochDay(slot)
    val today = BodyTimeline.today(p)
    return when {
        date == today -> "Hari ini"
        date.isAfter(today) -> "${Format.dayMonth(date)} (proyeksi)"
        else -> Format.dayMonth(date)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotPicker(p: WeightProjection, slot: Long, color: Color, onPick: (Long) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }
    val today = BodyTimeline.today(p)
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(cs.surfaceContainerHigh)
                .clickable(onClickLabel = "Pilih tanggal pembanding") { menu = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(slotLabel(p, slot), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, null, tint = cs.onSurfaceVariant)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val options = buildList {
                add("Awal pencatatan · ${Format.dayMonth(p.startDate)}" to p.startDate.toEpochDay())
                listOf(30L to "30 hari lalu", 7L to "7 hari lalu").forEach { (d, label) ->
                    val date = today.minusDays(d)
                    if (date.isAfter(p.startDate)) add("$label · ${Format.dayMonth(date)}" to date.toEpochDay())
                }
                add("Hari ini" to today.toEpochDay())
                if (p.forecast.isNotEmpty()) {
                    add("2 minggu lagi (proyeksi)" to today.plusDays(14).toEpochDay())
                    add("4 minggu lagi (proyeksi)" to BodyTimeline.lastDate(p).toEpochDay())
                }
                p.targetWeight?.let { add("Berat target · ${Format.kg(it)}" to TARGET_SLOT) }
            }
            options.forEach { (label, value) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onPick(value); menu = false })
            }
            DropdownMenuItem(text = { Text("Pilih tanggal…") }, onClick = { menu = false; calendar = true })
        }
    }
    if (calendar) {
        val first = p.startDate
        val last = BodyTimeline.lastDate(p)
        val initial = if (slot == TARGET_SLOT) today else LocalDate.ofEpochDay(slot)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !d.isBefore(first) && !d.isAfter(last)
                }

                override fun isSelectableYear(year: Int): Boolean = year in first.year..last.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { calendar = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }
                    calendar = false
                }) { Text("Pilih") }
            },
            dismissButton = { TextButton(onClick = { calendar = false }) { Text("Batal") } },
        ) { DatePicker(state = state) }
    }
}

/** Menyimpan bitmap render agar tidak dibuat ulang setiap frame. */
private class RenderTarget {
    var bitmap: Bitmap? = null
    var image: ImageBitmap? = null

    fun get(w: Int, h: Int): Pair<Bitmap, ImageBitmap> {
        val b = bitmap
        if (b == null || b.width != w || b.height != h) {
            val nb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap = nb
            image = nb.asImageBitmap()
        }
        return bitmap!! to image!!
    }
}

@Composable
private fun BodyFigure(params: BodyParams?, yaw: () -> Float, color: Color, heightCm: Double, modifier: Modifier) {
    if (params == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Tidak ada data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val renderer = remember(params) { BodyRenderer(BodyMeshBuilder.build(params)) }
    val target = remember { RenderTarget() }
    val argb = color.toArgb()
    Canvas(modifier) {
        val scale = min(1f, MAX_RENDER_HEIGHT / size.height)
        val w = (size.width * scale).toInt().coerceAtLeast(1)
        val h = (size.height * scale).toInt().coerceAtLeast(1)
        // Skala sama untuk kedua tubuh (tinggi sama) sehingga perbedaan lebar terlihat jujur.
        // Dibatasi juga oleh lebar: rentang lengan pose-A ±64% tinggi badan (+ ruang saat diputar).
        val pxPerCm = min(h * 0.84f / heightCm.toFloat(), w * 0.94f / (0.64f * heightCm.toFloat()))
        val floor = h * 0.9f
        val shadowW = size.width * 0.62f
        drawOval(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent)),
            topLeft = Offset((size.width - shadowW) / 2, floor / scale - 10.dp.toPx()),
            size = Size(shadowW, 20.dp.toPx()),
        )
        val (bitmap, image) = target.get(w, h)
        val pixels = renderer.render(w, h, yaw(), 0.08f, pxPerCm, floor, argb)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        drawImage(image, srcOffset = IntOffset.Zero, srcSize = IntSize(w, h), dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low)
    }
}

@Composable
private fun SnapshotInfo(s: BodySnapshot?, p: WeightProjection, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (s == null) {
            Text("-", style = MaterialTheme.typography.titleMedium)
            return@Column
        }
        val bmi = TdeeCalculator.bmi(s.weightKg, p.heightCm)
        val cls = BmiClass.of(bmi)
        Text(Format.kg(s.weightKg), style = MaterialTheme.typography.titleMedium)
        Text(s.source.label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("BMI ${Format.oneDecimal(bmi)}", style = MaterialTheme.typography.labelMedium, color = cls.color())
        Text(cls.label, style = MaterialTheme.typography.labelSmall, color = cls.color(), textAlign = TextAlign.Center)
        Text(
            "Pinggang ±${BodyTimeline.waistCm(s.weightKg, p.heightCm, p.sex).roundToInt()} cm",
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun deltaText(a: BodySnapshot, b: BodySnapshot, p: WeightProjection): String {
    val dKg = b.weightKg - a.weightKg
    if (abs(dKg) < 0.05) return "Berat kedua titik sama, bentuk tubuh tidak berubah."
    val dBmi = TdeeCalculator.bmi(b.weightKg, p.heightCm) - TdeeCalculator.bmi(a.weightKg, p.heightCm)
    val dWaist = BodyTimeline.waistCm(b.weightKg, p.heightCm, p.sex) - BodyTimeline.waistCm(a.weightKg, p.heightCm, p.sex)
    fun signed(v: Double, unit: String) = (if (v > 0) "+" else "−") + Format.oneDecimal(abs(v)) + unit
    return "Dari kiri ke kanan: berat ${signed(dKg, " kg")}, BMI ${signed(dBmi, "")}, perkiraan pinggang ${signed(dWaist, " cm")}."
}
