package com.example.mealplanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mealplanner.MealPlannerViewModel
import com.example.mealplanner.R
import com.example.mealplanner.data.MealPlanWithRecipe
import com.example.mealplanner.ui.components.RingProgress
import com.example.mealplanner.ui.components.SectionHeader
import com.example.mealplanner.ui.components.categoryIcon
import com.example.mealplanner.ui.formatMoney
import com.example.mealplanner.ui.mealTypeLabel
import com.example.mealplanner.util.WaterPrefs
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: MealPlannerViewModel,
    onOpenRecipe: (Int) -> Unit,
    onOpenShopping: (String) -> Unit,
    onOpenInsights: () -> Unit,
    onNewRecipe: () -> Unit,
    onScan: () -> Unit,
    onOpenPlanner: () -> Unit
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val dateKey = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

    val meals by remember(dateKey) { viewModel.mealsForDate(dateKey) }
        .collectAsState(initial = emptyList())
    val dayCost by remember(dateKey) { viewModel.plannedCostForDate(dateKey) }
        .collectAsState(initial = null)
    val dayBudget by remember(dateKey) { viewModel.budgetForDate(dateKey) }
        .collectAsState(initial = null)
    val weekCost by remember {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        viewModel.plannedCostBetween(
            monday.format(DateTimeFormatter.ISO_LOCAL_DATE),
            monday.plusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE))
    }.collectAsState(initial = null)

    var waterGlasses by remember(dateKey) {
        mutableIntStateOf(WaterPrefs.get(context, dateKey))
    }

    val greetingRes = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> R.string.greeting_morning
            in 12..17 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_today)) },
                actions = {
                    IconButton(onClick = onOpenInsights) {
                        Icon(Icons.Rounded.Insights,
                            contentDescription = stringResource(R.string.insights))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(bottom = 16.dp)
        ) {
            // ---- Greeting ----
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(greetingRes), style = MaterialTheme.typography.headlineSmall)
                Text(
                    today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- Quick actions ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickAction(Icons.Default.Add, stringResource(R.string.quick_add_recipe), onNewRecipe)
                QuickAction(Icons.Rounded.QrCodeScanner, stringResource(R.string.quick_scan), onScan)
                QuickAction(Icons.Default.ShoppingCart, stringResource(R.string.quick_shopping)) {
                    onOpenShopping(dateKey)
                }
                QuickAction(Icons.Rounded.Insights, stringResource(R.string.quick_insights), onOpenInsights)
            }

            // ---- Water ring ----
            ElevatedCard(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    RingProgress(
                        progress = waterGlasses / WaterPrefs.WATER_GOAL.toFloat(),
                        strokeWidth = 8.dp,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(76.dp)
                    ) { Text("$waterGlasses", style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.water_title),
                            style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.water_glasses, waterGlasses, WaterPrefs.WATER_GOAL),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            FilledTonalIconButton(
                                onClick = {
                                    waterGlasses = (waterGlasses - 1).coerceAtLeast(0)
                                    WaterPrefs.set(context, dateKey, waterGlasses)
                                },
                                enabled = waterGlasses > 0,
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Default.Remove, contentDescription = null,
                                modifier = Modifier.size(16.dp)) }
                            Spacer(Modifier.width(6.dp))
                            FilledTonalIconButton(
                                onClick = {
                                    waterGlasses++
                                    WaterPrefs.set(context, dateKey, waterGlasses)
                                },
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Default.Add, contentDescription = null,
                                modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }

            // ---- Meals carousel ----
            SectionHeader(stringResource(R.string.meals_today), action = {
                TextButton(onClick = onOpenPlanner) { Text(stringResource(R.string.go_to_planner)) }
            })
            if (meals.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.nothing_planned),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = onOpenPlanner) {
                            Text(stringResource(R.string.go_to_planner))
                        }
                    }
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(meals, key = { it.mealPlan.id }) { entry ->
                        TodayMealCard(entry) { onOpenRecipe(entry.recipe.id) }
                    }
                }
            }

            // ---- Budget mini-card ----
            if (dayCost != null || dayBudget != null || (weekCost ?: 0.0) > 0.0) {
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.estimated_cost, formatMoney(dayCost ?: 0.0)),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onOpenPlanner) {
                                Text(stringResource(R.string.set_budget))
                            }
                        }
                        dayBudget?.let { b ->
                            val spent = dayCost ?: 0.0
                            LinearProgressIndicator(
                                progress = {
                                    if (b.amount > 0) (spent / b.amount).toFloat()
                                        .coerceIn(0f, 1f) else 0f
                                },
                                color = if (spent > b.amount) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().height(6.dp)
                            )
                        }
                        if ((weekCost ?: 0.0) > 0.0) {
                            Text(
                                stringResource(R.string.this_week_planned, formatMoney(weekCost ?: 0.0)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.weight(1f)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun TodayMealCard(entry: MealPlanWithRecipe, onClick: () -> Unit) {
    val recipe = entry.recipe
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.width(150.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(92.dp)) {
                if (recipe.photoPath != null) {
                    AsyncImage(
                        model = File(recipe.photoPath!!), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(categoryIcon(recipe.category), contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp))
                        }
                    }
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                ) {
                    Text(mealTypeLabel(entry.mealPlan.mealType),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Text(
                recipe.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}
