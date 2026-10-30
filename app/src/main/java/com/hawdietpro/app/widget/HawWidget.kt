package com.hawdietpro.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.data.repository.EnergyRepository
import com.hawdietpro.app.data.repository.ExerciseRepository
import com.hawdietpro.app.data.repository.MealRepository
import com.hawdietpro.app.domain.model.sum
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun meals(): MealRepository
    fun exercises(): ExerciseRepository
    fun energy(): EnergyRepository
}

/** Ringkasan hari ini untuk widget. */
data class WidgetData(
    val ready: Boolean,
    val eaten: Int = 0,
    val budget: Int = 0,
    val exercise: Int = 0,
    val protein: Int = 0,
    val carbs: Int = 0,
    val fat: Int = 0,
    val proteinTarget: Int = 0,
    val meals: Int = 0,
) {
    val remaining: Int get() = budget - eaten

    companion object {
        suspend fun load(context: Context): WidgetData = runCatching {
            val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            val today = LocalDate.now()
            val target = ep.energy().getTarget() ?: return WidgetData(ready = false)
            val meals = ep.meals().observeDay(today).first()
            val burned = ep.exercises().observeDay(today).first().sumOf { it.calories }.roundToInt()
            val total = meals.map { it.total }.sum()
            WidgetData(
                ready = true,
                eaten = total.calories.roundToInt(),
                budget = target.calories + burned,
                exercise = burned,
                protein = total.protein.roundToInt(),
                carbs = total.carbs.roundToInt(),
                fat = total.fat.roundToInt(),
                proteinTarget = target.proteinG,
                meals = meals.size,
            )
        }.getOrDefault(WidgetData(ready = false))
    }
}

class HawWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        val ring = ringBitmap(data)
        provideContent { WidgetBody(context, data, ring) }
    }

    companion object {
        val SMALL = DpSize(180.dp, 110.dp)
        val WIDE = DpSize(260.dp, 110.dp)

        suspend fun refresh(context: Context) {
            runCatching { HawWidget().updateAll(context) }
        }
    }
}

class HawWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HawWidget()
}

private val Bg = Color(0xFF0B3B2E)
private val BgButton = Color(0xFF145C47)
private val White = Color(0xFFFFFFFF)
private val Sub = Color(0xB3FFFFFF)
private val Mint = Color(0xFF7CF5C9)
private val Over = Color(0xFFFFB4A6)

@Composable
private fun WidgetBody(context: Context, d: WidgetData, ring: Bitmap) {
    val wide = LocalSize.current.width >= 250.dp
    Column(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(Bg)
            .padding(14.dp)
            .clickable(actionStartActivity(QuickLaunch.HOME.intent(context))),
    ) {
        if (!d.ready) {
            Text("Haw Diet Pro", style = TextStyle(color = ColorProvider(White), fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(6.dp))
            Text("Buka aplikasi untuk mulai mencatat.", style = TextStyle(color = ColorProvider(Sub), fontSize = 12.sp))
            return@Column
        }
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(78.dp), contentAlignment = Alignment.Center) {
                Image(ImageProvider(ring), contentDescription = null, modifier = GlanceModifier.size(78.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        Format.grouped(abs(d.remaining)),
                        style = TextStyle(color = ColorProvider(if (d.remaining < 0) Over else White), fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    )
                    Text(if (d.remaining < 0) "lebih" else "sisa", style = TextStyle(color = ColorProvider(Sub), fontSize = 10.sp))
                }
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text("Hari ini", style = TextStyle(color = ColorProvider(Sub), fontSize = 11.sp))
                Text(
                    "${Format.grouped(d.eaten)} / ${Format.grouped(d.budget)} kkal",
                    style = TextStyle(color = ColorProvider(White), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Text(
                    if (wide) "P ${d.protein}/${d.proteinTarget} g · K ${d.carbs} g · L ${d.fat} g" else "Protein ${d.protein}/${d.proteinTarget} g",
                    style = TextStyle(color = ColorProvider(Sub), fontSize = 11.sp),
                    maxLines = 1,
                )
                if (d.exercise > 0) {
                    Text("Olahraga +${Format.grouped(d.exercise)} kkal", style = TextStyle(color = ColorProvider(Mint), fontSize = 11.sp), maxLines = 1)
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth()) {
            WidgetButton(context, "📷 Foto", QuickLaunch.CAMERA, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            if (wide) {
                WidgetButton(context, "▦ Barcode", QuickLaunch.BARCODE, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(8.dp))
            }
            WidgetButton(context, "↻ Lagi", QuickLaunch.RELOG, GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun WidgetButton(context: Context, label: String, action: QuickLaunch, modifier: GlanceModifier) {
    Box(
        modifier
            .height(34.dp)
            .cornerRadius(17.dp)
            .background(BgButton)
            .clickable(actionStartActivity(action.intent(context))),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(color = ColorProvider(White), fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

/** Cincin progres digambar ke bitmap (Glance tidak punya cincin progres bawaan). */
private fun ringBitmap(d: WidgetData): Bitmap {
    val size = 240
    val stroke = 26f
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val rect = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    paint.color = 0x33FFFFFF
    c.drawArc(rect, 0f, 360f, false, paint)
    if (d.ready && d.budget > 0 && d.eaten > 0) {
        val frac = (d.eaten.toFloat() / d.budget).coerceIn(0f, 1f)
        paint.color = if (d.eaten > d.budget) 0xFFFFB4A6.toInt() else 0xFFB8FFE2.toInt()
        c.drawArc(rect, -90f, 360f * frac, false, paint)
    }
    return bmp
}
