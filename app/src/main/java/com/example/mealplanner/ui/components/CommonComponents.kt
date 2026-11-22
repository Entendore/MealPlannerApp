package com.example.mealplanner.ui.components

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mealplanner.R
import com.example.mealplanner.ui.allergenLabelRes
import com.example.mealplanner.ui.currencySymbol
import com.example.mealplanner.ui.dietLabelRes
import com.example.mealplanner.ui.formatMoney
import com.example.mealplanner.ui.formatQuantity
import com.example.mealplanner.util.UnitType
import com.example.mealplanner.util.Units
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Empty / zero state
// ---------------------------------------------------------------------------

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

// ---------------------------------------------------------------------------
// Section header / meta chips
// ---------------------------------------------------------------------------

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun MetaChip(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun QuantityBadge(text: String, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        onClick = onClick, shape = CircleShape, color = container,
        modifier = modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, Modifier.size(20.dp), tint = tint)
        }
    }
}

// ---------------------------------------------------------------------------
// Category / diet iconography
// ---------------------------------------------------------------------------

fun categoryIcon(category: String): ImageVector = when (category) {
    "Breakfast" -> Icons.Rounded.WbSunny
    "Lunch" -> Icons.Rounded.LunchDining
    "Dinner" -> Icons.Rounded.DinnerDining
    "Dessert" -> Icons.Rounded.Cake
    "Snack" -> Icons.Rounded.Cookie
    "Drink" -> Icons.Rounded.LocalCafe
    else -> Icons.Rounded.Restaurant
}

private val WbSunnyIcon get() = Icons.Rounded.WbSunny
fun categoryIconOrNull(category: String): ImageVector = categoryIcon(category)
@Suppress("unused")
private fun keepWbSunnyImport() = WbSunnyIcon

fun dietIcon(diet: String): ImageVector = when (diet) {
    "Vegetarian" -> Icons.Rounded.Grass
    "Vegan" -> Icons.Rounded.Eco
    "Pescatarian" -> Icons.Rounded.SetMeal
    "Gluten-free" -> Icons.Rounded.BakeryDining
    "Dairy-free" -> Icons.Rounded.WaterDrop
    "Keto" -> Icons.Rounded.LocalFireDepartment
    else -> Icons.Rounded.Restaurant
}

/** Tonal square tile with the category icon — stand-in for missing photos. */
@Composable
fun CategoryTile(category: String, size: Dp = 64.dp, modifier: Modifier = Modifier) {
    val (container, content) = when (category) {
        "Breakfast" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "Dinner" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        "Dessert" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(shape = MaterialTheme.shapes.medium, color = container, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(categoryIcon(category), contentDescription = category,
                modifier = Modifier.size(size / 2), tint = content)
        }
    }
}

// ---------------------------------------------------------------------------
// Dietary clash banner (localized labels)
// ---------------------------------------------------------------------------

@Composable
fun ClashBanner(
    allergenClash: List<String>,
    dietClash: List<String>,
    modifier: Modifier = Modifier
) {
    if (allergenClash.isEmpty() && dietClash.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Column {
                if (allergenClash.isNotEmpty()) {
                    val labels = allergenClash.joinToString { stringResource(allergenLabelRes(it)) }
                    Text(
                        stringResource(R.string.allergy_clash_title) + " · " +
                            stringResource(R.string.allergy_clash_contains, labels),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                if (dietClash.isNotEmpty()) {
                    val labels = dietClash.joinToString { stringResource(dietLabelRes(it)) }
                    Text(
                        stringResource(R.string.diet_clash_missing, labels),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Budget dialog (planner / shopping list / month page)
// ---------------------------------------------------------------------------

@Composable
fun BudgetDialog(
    current: Double?,
    onConfirm: (Double?) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember {
        mutableStateOf(if (current != null && current > 0) formatQuantity(current) else "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.budget_hint)) },
                    prefix = { Text(currencySymbol()) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.toDoubleOrNull()) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text(stringResource(R.string.clear_budget))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Unit field — editable dropdown with canonical unit suggestions
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val resolved = Units.resolve(value)
    val suggestions = Units.suggestions(value)

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); expanded = true },
            label = { Text(stringResource(R.string.unit)) },
            singleLine = true,
            leadingIcon = {
                when (resolved?.type) {
                    UnitType.VOLUME -> Icon(Icons.Rounded.WaterDrop, contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary)
                    UnitType.WEIGHT -> Icon(Icons.Rounded.MonitorWeight, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    else -> {}
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            suggestions.forEach { def ->
                DropdownMenuItem(
                    text = { Text(def.key + when (def.type) {
                        UnitType.VOLUME -> "  ·  liquid"
                        UnitType.WEIGHT -> "  ·  weight"
                        UnitType.COUNT -> ""
                    }) },
                    onClick = { onChange(def.key); expanded = false }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Charts (Canvas-based, no libraries)
// ---------------------------------------------------------------------------

/** Circular progress ring with content centered inside. */
@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit = {}
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(trackColor, 0f, 360f, false, topLeft = Offset(inset, inset),
                size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, topLeft = Offset(inset, inset),
                    size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

data class Bar(val label: String, val value: Double)

/** Minimal animated bar chart: rounded gradient bars, value above, label below. */
@Composable
fun BarChart(
    bars: List<Bar>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    valueFormatter: (Double) -> String = { formatMoney(it) }
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onSurface
    val grow by animateFloatAsState(1f, tween(700), label = "bars")

    Canvas(modifier.fillMaxWidth().height(170.dp)) {
        if (bars.isEmpty()) return@Canvas
        val maxV = bars.maxOf { it.value }.takeIf { it > 0 } ?: 1.0
        val labelPaint = Paint().apply {
            textSize = 10.sp.toPx(); isAntiAlias = true
            textAlign = Paint.Align.CENTER; color = labelColor.toArgb()
        }
        val valuePaint = Paint().apply {
            textSize = 10.sp.toPx(); isAntiAlias = true
            textAlign = Paint.Align.CENTER; color = valueColor.toArgb()
        }
        val labelH = 16.dp.toPx()
        val valueH = 16.dp.toPx()
        val chartH = size.height - labelH - valueH
        val slot = size.width / bars.size
        val barW = slot * 0.55f

        bars.forEachIndexed { i, bar ->
            val h = (bar.value / maxV * chartH * grow).toFloat()
            val cx = i * slot + slot / 2f
            val top = valueH + (chartH - h)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(barColor, barColor.copy(alpha = 0.55f)),
                    startY = top, endY = top + h.coerceAtLeast(1f)
                ),
                topLeft = Offset(cx - barW / 2f, top),
                size = Size(barW, h.coerceAtLeast(if (bar.value > 0) 2.dp.toPx() else 0f)),
                cornerRadius = CornerRadius(barW / 3f)
            )
            drawContext.canvas.nativeCanvas.drawText(
                valueFormatter(bar.value), cx, top - 4.dp.toPx(), valuePaint)
            drawContext.canvas.nativeCanvas.drawText(
                bar.label, cx, size.height - 4.dp.toPx(), labelPaint)
        }
    }
}

data class Slice(val label: String, val value: Float, val color: Color)

/** Donut chart with a legend column beside it. */
@Composable
fun DonutChart(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 22.dp,
    centerContent: @Composable () -> Unit = {}
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = strokeWidth.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                if (total <= 0f) {
                    drawArc(trackColor, 0f, 360f, false, topLeft = Offset(inset, inset),
                        size = arcSize, style = Stroke(stroke))
                } else {
                    var start = -90f
                    slices.forEach { s ->
                        val sweep = 360f * s.value / total
                        drawArc(s.color, start, sweep, false, topLeft = Offset(inset, inset),
                            size = arcSize, style = Stroke(stroke))
                        start += sweep
                    }
                }
            }
            centerContent()
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            slices.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = s.color,
                        modifier = Modifier.size(10.dp)) {}
                    Spacer(Modifier.width(8.dp))
                    Text(s.label, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    Text(
                        if (total > 0) "${(s.value / total * 100).roundToInt()}%" else "0%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Horizontal proportional bar with label + count — "most planned". */
@Composable
fun LabeledBarRow(label: String, value: Int, max: Int, modifier: Modifier = Modifier) {
    val fraction = if (max > 0) value.toFloat() / max else 0f
    val animated by animateFloatAsState(fraction, tween(600), label = "row")
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(pluralStringResource(R.plurals.times_planned, value, value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small)) {
            Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth(animated.coerceIn(0.02f, 1f))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
