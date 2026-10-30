package com.hawdietpro.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hawdietpro.app.core.Format
import com.hawdietpro.app.domain.model.Meal
import com.hawdietpro.app.domain.model.MealType
import com.hawdietpro.app.domain.model.Nutrients
import com.hawdietpro.app.ui.theme.HawTheme
import com.hawdietpro.app.ui.theme.MacroColors
import com.hawdietpro.app.ui.theme.MealTypeColors
import java.io.File

// ─────────────────────────────── Struktur & kartu ───────────────────────────────

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        action?.invoke()
    }
}

/**
 * Kartu standar: putih (gelap: abu tua) dengan border tipis, sudut 22dp.
 * [color] boleh diisi untuk kartu berwarna (tanpa border).
 */
@Composable
fun HawCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val hc = HawTheme.colors
    val bg = color ?: hc.card
    // Sorotan tipis di tepi atas (mode gelap) memberi kesan kartu "kaca" yang sedikit terangkat.
    val border = if (color == null) {
        BorderStroke(1.dp, Brush.verticalGradient(listOf(lerp(hc.cardBorder, Color.White, if (hc.cardHighlight.alpha > 0f) 0.06f else 0f), hc.cardBorder)))
    } else null
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().pressScale(interaction, 0.98f),
            shape = MaterialTheme.shapes.large,
            color = bg,
            border = border,
            interactionSource = interaction,
        ) {
            Box(Modifier.padding(contentPadding)) { content() }
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = bg, border = border) {
            Box(Modifier.padding(contentPadding)) { content() }
        }
    }
}

/** Lingkaran ikon berwarna lembut (dipakai di header kartu, menu, dsb.). */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun EmojiBadge(emoji: String, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
    }
}

/** Label kecil berbentuk pil. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false, icon: ImageVector? = null) {
    Row(
        modifier
            .clip(CircleShape)
            .background(if (filled) color else color.copy(alpha = 0.13f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, null, tint = if (filled) Color.White else color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (filled) Color.White else color)
    }
}

fun mealTypeColor(type: MealType): Color = when (type) {
    MealType.SARAPAN -> MealTypeColors.Sarapan
    MealType.MAKAN_SIANG -> MealTypeColors.Siang
    MealType.MAKAN_MALAM -> MealTypeColors.Malam
    MealType.CAMILAN -> MealTypeColors.Camilan
}

// ─────────────────────────────── Makanan ───────────────────────────────

@Composable
fun MealPhoto(path: String?, modifier: Modifier = Modifier, size: Dp = 64.dp, corner: Dp = 16.dp) {
    val shape = RoundedCornerShape(corner)
    if (path != null && File(path).exists()) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Restaurant, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size * 0.4f))
        }
    }
}

@Composable
fun MealRow(meal: Meal, onClick: () -> Unit, modifier: Modifier = Modifier, showType: Boolean = true) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            MealPhoto(meal.photoPath, size = 58.dp)
            meal.grade?.let {
                GradeBadge(it, size = 22.dp, modifier = Modifier.align(Alignment.BottomEnd).padding(0.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(meal.menuName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    if (showType) append(meal.mealType.label).append(" · ")
                    append(Format.time(meal.eatenAt))
                    append(" · ").append(meal.items.size).append(" elemen")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(5.dp))
            MacroInline(meal.total)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Format.kcalNumber(meal.total.calories), style = MaterialTheme.typography.titleMedium)
            Text("kkal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Batang proporsi energi protein/karbo/lemak dalam satu baris. */
@Composable
fun SegmentedMacroBar(n: Nutrients, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val p = (n.protein * 4).toFloat()
    val c = (n.carbs * 4).toFloat()
    val f = (n.fat * 9).toFloat()
    val total = p + c + f
    Row(
        modifier.fillMaxWidth().height(height).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (total > 0f) {
            listOf(p to MacroColors.Protein, c to MacroColors.Carbs, f to MacroColors.Fat).forEach { (v, color) ->
                if (v > 0f) Box(Modifier.weight(v).fillMaxSize().background(color))
            }
        }
    }
}

// ─────────────────────────────── Statistik & status ───────────────────────────────

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    color: Color? = null,
    accent: Color? = null,
) {
    val bg = color ?: HawTheme.colors.card
    Surface(
        modifier.fillMaxHeight(),
        shape = MaterialTheme.shapes.medium,
        color = bg,
        border = if (color == null) BorderStroke(1.dp, HawTheme.colors.cardBorder) else null,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                accent?.let {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(it))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            sub?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 3.dp)
    }
}

/** Pesan banner (info / peringatan / sukses) dengan ikon dalam lingkaran. */
@Composable
fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = if (title != null) Alignment.Top else Alignment.CenterVertically) {
                icon?.let {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(content.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(it, null, Modifier.size(18.dp)) }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    title?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            actions?.let {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { it() }
            }
        }
    }
}

/** Progress horizontal bulat sederhana (beranimasi). */
@Composable
fun SoftProgress(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp, track: Color = color.copy(alpha = 0.15f)) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "soft")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(CircleShape).background(color))
    }
}
