package com.hawdietpro.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.domain.model.Grade
import com.hawdietpro.app.domain.model.NutritionSource
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.ui.theme.GradeColors
import com.hawdietpro.app.ui.theme.MacroColors
import kotlin.math.roundToInt

/** Ring progres kalori: terisi sesuai kalori masuk / target, merah bila melebihi. */
@Composable
fun CalorieRing(
    consumed: Double,
    target: Int,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    stroke: Dp = 16.dp,
) {
    val progress = if (target > 0) (consumed / target).toFloat() else 0f
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    val over = consumed > target && target > 0
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val remaining = target - consumed.roundToInt()

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
            val inset = stroke.toPx() / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = s)
            drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = s)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                Format.grouped(if (remaining >= 0) remaining else -remaining),
                style = MaterialTheme.typography.headlineLarge,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (remaining >= 0) "kkal tersisa" else "kkal berlebih",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

data class MacroSpec(val label: String, val value: Double, val target: Int, val color: Color)

fun macroSpecs(n: Nutrients, proteinT: Int, carbsT: Int, fatT: Int, fiberT: Int) = listOf(
    MacroSpec("Protein", n.protein, proteinT, MacroColors.Protein),
    MacroSpec("Karbo", n.carbs, carbsT, MacroColors.Carbs),
    MacroSpec("Lemak", n.fat, fatT, MacroColors.Fat),
    MacroSpec("Serat", n.fiber, fiberT, MacroColors.Fiber),
)

@Composable
fun MacroProgress(spec: MacroSpec, modifier: Modifier = Modifier) {
    val progress = if (spec.target > 0) (spec.value / spec.target).toFloat().coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(progress, tween(600), label = "macro")
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(spec.label, style = MaterialTheme.typography.labelLarge)
            Text(
                "${spec.value.roundToInt()} / ${spec.target} g",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = spec.color,
            trackColor = spec.color.copy(alpha = 0.18f),
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
    }
}

/** Ringkasan makro kecil dalam satu baris: P 20g · K 50g · L 10g. */
@Composable
fun MacroInline(n: Nutrients, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        MacroDot("P", n.protein, MacroColors.Protein)
        MacroDot("K", n.carbs, MacroColors.Carbs)
        MacroDot("L", n.fat, MacroColors.Fat)
    }
}

@Composable
private fun MacroDot(label: String, grams: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text("$label ${Format.oneDecimal(grams)}g", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun gradeColor(grade: Grade): Color = when (grade) {
    Grade.A -> GradeColors.A
    Grade.B -> GradeColors.B
    Grade.C -> GradeColors.C
    Grade.D -> GradeColors.D
    Grade.E -> GradeColors.E
}

@Composable
fun GradeBadge(grade: Grade, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Surface(modifier.size(size), shape = CircleShape, color = gradeColor(grade)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                grade.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = if (size >= 40.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
fun SourceBadge(source: NutritionSource, modifier: Modifier = Modifier) {
    val (bg, fg) = when (source) {
        NutritionSource.DB -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        NutritionSource.AI -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        NutritionSource.MANUAL -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(modifier, shape = RoundedCornerShape(6.dp), color = bg) {
        Text(
            source.label,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
