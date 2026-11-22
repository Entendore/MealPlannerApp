package com.example.mealplanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mealplanner.MealPlannerViewModel
import com.example.mealplanner.R
import com.example.mealplanner.ui.components.BudgetDialog
import com.example.mealplanner.ui.components.EmptyState
import com.example.mealplanner.ui.formatMoney
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(viewModel: MealPlannerViewModel, onBack: () -> Unit) {
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()) }

    var monthOffset by remember { mutableIntStateOf(0) }
    val month = remember(monthOffset) { YearMonth.now().plusMonths(monthOffset.toLong()) }
    val start = month.atDay(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
    val end = month.atEndOfMonth().format(DateTimeFormatter.ISO_LOCAL_DATE)

    val days by remember(start, end) { viewModel.monthOverview(start, end) }
        .collectAsState(initial = emptyList())
    val monthBudget by viewModel.budgetForDate(month.toString())   // "yyyy-MM" month key
        .collectAsState(initial = null)
    var showBudgetDialog by remember { mutableStateOf(false) }

    val totalPlanned = days.sumOf { it.planned }
    val totalActual = days.sumOf { it.actual ?: 0.0 }
    val reference = if (totalActual > 0) totalActual else totalPlanned

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.budget_screen)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Month navigation ----
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { monthOffset -= 1 }) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null)
                }
                Text(
                    month.format(monthFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                FilledTonalIconButton(onClick = { monthOffset += 1 }) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                }
            }

            // ---- Summary card ----
            ElevatedCard(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Savings, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.monthly_budget),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f))
                        if (monthBudget == null) {
                            TextButton(onClick = { showBudgetDialog = true }) {
                                Text(stringResource(R.string.set_budget))
                            }
                        } else {
                            IconButton(onClick = { showBudgetDialog = true }) {
                                Icon(Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.edit_budget))
                            }
                        }
                    }
                    if (monthBudget != null) {
                        val fraction = if (monthBudget!!.amount > 0)
                            (reference / monthBudget!!.amount).toFloat()
                                .coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(
                            progress = { fraction },
                            color = if (reference > monthBudget!!.amount)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                    }
                    Text(stringResource(R.string.planned_total, formatMoney(totalPlanned)),
                        style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.actual_month, formatMoney(totalActual)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (totalActual > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- Per-day rows ----
            if (days.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.ShoppingBasket,
                    title = stringResource(R.string.budget_screen),
                    message = stringResource(R.string.no_budget_data)
                )
            } else {
                days.forEach { day ->
                    val spend = day.actual ?: day.planned
                    val over = day.budget != null && spend > day.budget!!
                    ListItem(
                        headlineContent = { Text(day.date.format(dayFormatter)) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    stringResource(R.string.estimated_cost,
                                        formatMoney(day.planned)),
                                    day.actual?.let {
                                        stringResource(R.string.actual_month, formatMoney(it))
                                    }
                                ).joinToString("  ·  "),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            if (day.budget != null) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (over) MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        formatMoney(day.budget!!),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (over) MaterialTheme.colorScheme.onErrorContainer
                                        else MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            } else {
                                Icon(Icons.Default.Payments, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }
            }
        }
    }

    if (showBudgetDialog) {
        BudgetDialog(
            current = monthBudget?.amount,
            onConfirm = { amount ->
                viewModel.setBudget(month.toString(), amount)
                showBudgetDialog = false
            },
            onDismiss = { showBudgetDialog = false }
        )
    }
}
