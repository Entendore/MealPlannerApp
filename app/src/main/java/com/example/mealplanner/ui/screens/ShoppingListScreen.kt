package com.example.mealplanner.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.mealplanner.MealPlannerViewModel
import com.example.mealplanner.R
import com.example.mealplanner.ui.components.BudgetDialog
import com.example.mealplanner.ui.components.EmptyState
import com.example.mealplanner.ui.components.QuantityBadge
import com.example.mealplanner.ui.currencySymbol
import com.example.mealplanner.ui.formatAmount
import com.example.mealplanner.ui.formatMoney
import com.example.mealplanner.ui.formatQuantity
import com.example.mealplanner.util.AllergenUtil
import kotlinx.coroutines.flow.flowOf
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    date: String,
    viewModel: MealPlannerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var scope by rememberSaveable { mutableStateOf("day") }     // "day" | "week"
    val anchor = remember(date) { LocalDate.parse(date) }

    val start = remember(scope, date) {
        if (scope == "day") date
        else anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
    val end = remember(scope, date) {
        if (scope == "day") date
        else anchor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    val items by remember(start, end) { viewModel.shoppingListBetween(start, end) }
        .collectAsState(initial = emptyList())
    // Day budgets model a single shopping trip — hidden in week scope by design.
    val budget by remember(start, scope) {
        if (scope == "day") viewModel.budgetForDate(start) else flowOf(null)
    }.collectAsState(initial = null)
    val profile by viewModel.profile.collectAsState()

    // Trip actuals persist in the purchases table (day-anchored).
    val purchases by remember(date) { viewModel.purchasesForDate(date) }
        .collectAsState(initial = emptyList())
    val checkedState = remember { mutableStateMapOf<String, Boolean>() }
    val actualPrices = remember { mutableStateMapOf<String, String>() }

    fun keyOf(name: String, unit: String) = "$name|$unit"

    LaunchedEffect(purchases) {
        purchases.forEach { p ->
            val key = keyOf(p.name, p.unit)
            if (!actualPrices.containsKey(key)) actualPrices[key] = formatQuantity(p.actualPrice)
        }
    }

    val checkedCount = items.count { checkedState[keyOf(it.name, it.unit)] ?: false }
    val progress = if (items.isEmpty()) 0f else checkedCount.toFloat() / items.size

    val estimatedTotal = items.sumOf { it.totalPrice }
    val actualTotal = items.sumOf { item ->
        actualPrices[keyOf(item.name, item.unit)]?.toDoubleOrNull() ?: 0.0
    }
    val anyActual = actualPrices.values.any { it.toDoubleOrNull() != null }

    var showBudgetDialog by remember { mutableStateOf(false) }
    var editingPriceKey by remember { mutableStateOf<String?>(null) }

    val displayDate = remember(start, end, scope) {
        if (scope == "day") LocalDate.parse(start)
            .format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        else {
            val f = DateTimeFormatter.ofPattern("MMM d")
            LocalDate.parse(start).format(f) + " – " + LocalDate.parse(end).format(f)
        }
    }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            viewModel.exportShoppingListPdf(it, start, end) { success ->
                Toast.makeText(
                    context,
                    if (success) context.getString(R.string.export_success,
                        it.lastPathSegment ?: "")
                    else context.getString(R.string.export_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shopping_list_title, displayDate)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { exportPdfLauncher.launch("shopping-list-$start.pdf") },
                        enabled = items.isNotEmpty()
                    ) {
                        Icon(Icons.Default.PictureAsPdf,
                            contentDescription = stringResource(R.string.export_pdf))
                    }
                }
            )
        },
        bottomBar = {
            if (items.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        TotalRow(stringResource(R.string.estimated_total),
                            formatMoney(estimatedTotal))
                        if (anyActual) {
                            Spacer(Modifier.height(4.dp))
                            TotalRow(stringResource(R.string.actual_total),
                                formatMoney(actualTotal))
                        }
                        budget?.let { b ->
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = {
                                    if (b.amount > 0)
                                        (estimatedTotal / b.amount).toFloat()
                                            .coerceIn(0f, 1f) else 0f
                                },
                                color = if (estimatedTotal > b.amount)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().height(6.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (estimatedTotal <= b.amount)
                                    stringResource(R.string.budget_remaining,
                                        formatMoney(b.amount - estimatedTotal))
                                else stringResource(R.string.budget_over,
                                    formatMoney(estimatedTotal - b.amount)),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (estimatedTotal > b.amount)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.ShoppingBasket,
                    title = stringResource(R.string.shopping_list),
                    message = stringResource(R.string.empty_shopping_list)
                )
            }
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                // ---- Day/Week scope ----
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = scope == "day",
                        onClick = { scope = "day" },
                        label = { Text(stringResource(R.string.scope_day)) }
                    )
                    FilterChip(
                        selected = scope == "week",
                        onClick = { scope = "week" },
                        label = { Text(stringResource(R.string.scope_week)) }
                    )
                }

                // ---- Budget card (day scope only) ----
                if (scope == "day") {
                    ElevatedCard(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Savings, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                if (budget == null) {
                                    Text(stringResource(R.string.budget),
                                        style = MaterialTheme.typography.titleSmall)
                                    Text(stringResource(R.string.budget_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text(
                                        stringResource(R.string.budget_used,
                                            formatMoney(estimatedTotal),
                                            formatMoney(budget!!.amount)),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                            if (budget == null) {
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
                    }
                }

                // ---- Checked progress ----
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.checked_count, checkedCount, items.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp).height(4.dp),
                    strokeCap = StrokeCap.Round
                )

                // ---- Items ----
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(items, key = { keyOf(it.name, it.unit) }) { item ->
                        val key = keyOf(item.name, item.unit)
                        val checked = checkedState[key] ?: false
                        val actual = actualPrices[key]?.toDoubleOrNull()
                        val clash = item.allergens.split(',')
                            .filter { it.isNotBlank() }.toSet() intersect
                                profile.allergies.toSet()
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .clickable { editingPriceKey = key },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked,
                                onCheckedChange = { checkedState[key] = it })
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (checked) TextDecoration.LineThrough else null,
                                modifier = Modifier.weight(1f)
                            )
                            if (clash.isNotEmpty()) {
                                Icon(Icons.Default.Warning,
                                    contentDescription = clash.joinToString(),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            QuantityBadge(formatAmount(item.totalQuantity, item.unit))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (actual != null) formatMoney(actual)
                                else formatMoney(item.totalPrice),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (actual != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                    }
                }
            }
        }
    }

    // ---- Budget dialog ----
    if (showBudgetDialog) {
        BudgetDialog(
            current = budget?.amount,
            onConfirm = { amount ->
                viewModel.setBudget(start, amount)
                showBudgetDialog = false
            },
            onDismiss = { showBudgetDialog = false }
        )
    }

    // ---- Actual-price dialog ----
    editingPriceKey?.let { key ->
        val item = items.firstOrNull { keyOf(it.name, it.unit) == key } ?: return@let
        var priceText by remember(key) { mutableStateOf(actualPrices[key] ?: "") }
        AlertDialog(
            onDismissRequest = { editingPriceKey = null },
            title = { Text(stringResource(R.string.enter_actual_price, item.name)) },
            text = {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.actual_price_hint)) },
                    prefix = { Text(currencySymbol()) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = priceText.toDoubleOrNull()
                    if (parsed != null) {
                        actualPrices[key] = priceText
                        viewModel.saveActualPrice(start, item.name, item.unit,
                            item.totalQuantity, item.totalPrice, parsed)
                    } else {
                        actualPrices.remove(key)
                        viewModel.clearActualPrice(start, item.name, item.unit)
                    }
                    editingPriceKey = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    if (actualPrices.containsKey(key)) {
                        TextButton(onClick = {
                            actualPrices.remove(key)
                            viewModel.clearActualPrice(start, item.name, item.unit)
                            editingPriceKey = null
                        }) { Text(stringResource(R.string.clear)) }
                    }
                    TextButton(onClick = { editingPriceKey = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Payments, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
