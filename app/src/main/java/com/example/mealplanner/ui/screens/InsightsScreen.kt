package com.example.mealplanner.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mealplanner.MealPlannerViewModel
import com.example.mealplanner.R
import com.example.mealplanner.ui.components.Bar
import com.example.mealplanner.ui.components.BarChart
import com.example.mealplanner.ui.components.DonutChart
import com.example.mealplanner.ui.components.EmptyState
import com.example.mealplanner.ui.components.LabeledBarRow
import com.example.mealplanner.ui.components.Slice
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(viewModel: MealPlannerViewModel, onBack: () -> Unit) {
    val today = remember { LocalDate.now() }
    val shortDay = remember { DateTimeFormatter.ofPattern("d/M") }
    val monthLbl = remember { DateTimeFormatter.ofPattern("MMM", Locale.getDefault()) }

    val topRecipes by viewModel.topPlannedRecipes().collectAsState(initial = emptyList())
    val recipes by viewModel.recipes.collectAsState()

    val weekStart = remember {
        today.minusWeeks(7).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    val weekCosts by remember(weekStart) {
        viewModel.plannedCostPerDayBetween(
            weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE),
            today.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }.collectAsState(initial = emptyList())
    val weeklyBars = remember(weekCosts) {
        val byWeek = weekCosts.groupBy {
            LocalDate.parse(it.date).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        (0..7).map { i ->
            val w = weekStart.plusWeeks(i.toLong())
            Bar(w.format(shortDay), byWeek[w]?.sumOf { it.cost } ?: 0.0)
        }
    }

    val monthStart = remember { today.minusMonths(5).withDayOfMonth(1) }
    val actualCosts by remember(monthStart) {
        viewModel.actualSpendPerDayBetween(
            monthStart.format(DateTimeFormatter.ISO_LOCAL_DATE),
            today.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }.collectAsState(initial = emptyList())
    val monthlyBars = remember(actualCosts) {
        val byMonth = actualCosts.groupBy { YearMonth.from(LocalDate.parse(it.date)) }
        (0..5).map { i ->
            val m = YearMonth.now().minusMonths((5 - i).toLong())
            Bar(m.format(monthLbl), byMonth[m]?.sumOf { it.cost } ?: 0.0)
        }
    }

    // Palette read OUTSIDE remember — MaterialTheme.colorScheme is a composable call.
    val cs = MaterialTheme.colorScheme
    val palette = remember(cs) {
        listOf(cs.primary, cs.tertiary, cs.secondary, cs.error, cs.outline,
            cs.primary.copy(alpha = 0.55f), cs.tertiary.copy(alpha = 0.55f))
    }
    val categorySlices = remember(recipes, palette) {
        recipes.groupBy { it.recipe.category }
            .map { (cat, list) -> cat to list.size }
            .sortedByDescending { it.second }
            .mapIndexed { i, (cat, n) -> Slice(cat, n.toFloat(), palette[i % palette.size]) }
    }

    val hasAnyData = topRecipes.isNotEmpty() || recipes.isNotEmpty() ||
        weeklyBars.any { it.value > 0 } || monthlyBars.any { it.value > 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.insights)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        if (!hasAnyData) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.QueryStats,
                    title = stringResource(R.string.insights),
                    message = stringResource(R.string.no_data_yet)
                )
            }
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (topRecipes.isNotEmpty()) {
                ChartCard(stringResource(R.string.top_planned)) {
                    val maxCount = topRecipes.first().count
                    topRecipes.forEach { LabeledBarRow(it.name, it.count, maxCount) }
                }
            }
            if (weeklyBars.any { it.value > 0 }) {
                ChartCard(stringResource(R.string.planned_cost_weeks)) {
                    BarChart(bars = weeklyBars)
                }
            }
            if (monthlyBars.any { it.value > 0 }) {
                ChartCard(stringResource(R.string.actual_spend_months)) {
                    BarChart(bars = monthlyBars)
                }
            }
            if (categorySlices.isNotEmpty()) {
                ChartCard(stringResource(R.string.cookbook_breakdown)) {
                    DonutChart(
                        slices = categorySlices,
                        centerContent = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${recipes.size}",
                                    style = MaterialTheme.typography.titleLarge)
                                Text(
                                    pluralStringResource(
                                        R.plurals.recipes_count, recipes.size, recipes.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}
