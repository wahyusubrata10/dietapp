package com.hawdietpro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.domain.model.Grade
import com.hawdietpro.app.domain.model.NutritionSource
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.ui.theme.GradeColors
import com.hawdietpro.app.ui.theme.MacroColors

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
        NutritionSource.KEMASAN -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
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
