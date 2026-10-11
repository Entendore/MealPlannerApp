package com.example.mealplanner.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mealplanner.MealPlannerViewModel
import com.example.mealplanner.R
import com.example.mealplanner.data.DietaryProfile
import com.example.mealplanner.data.MealPlan
import com.example.mealplanner.data.MealPlanWithRecipe
import com.example.mealplanner.data.Recipe
import com.example.mealplanner.ui.components.BudgetDialog
import com.example.mealplanner.ui.components.CategoryTile
import com.example.mealplanner.ui.components.ReorderableColumn
import com.example.mealplanner.ui.components.SectionHeader
import com.example.mealplanner.ui.formatMoney
import com.example.mealplanner.ui.mealTypeLabel
import com.example.mealplanner.util.AllergenUtil
import com.example.mealplanner.util.WaterPrefs
import kotlinx.coroutines.launch
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlannerScreen(
    viewModel: MealPlannerViewModel,
    onOpenRecipe: (Int) -> Unit,
    onOpenShoppingList: (String) -> Unit,
    onOpenBudget: () -> Unit
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val monthDay = remember { DateTimeFormatter.ofPattern("MMM d") }
    val monthYear = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }

    var planMode by rememberSaveable { mutableStateOf("week") }
    var weekOffset by remember { mutableIntStateOf(0) }
    var monthOffset by remember { mutableIntStateOf(0) }

    val weekStart = remember(weekOffset) {
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(weekOffset.toLong())
    }
    val weekDays = remember(weekStart) { (0..6).map { weekStart.plusDays(it.toLong()) } }
    val gridMonth = remember(monthOffset) { YearMonth.now().plusMonths(monthOffset.toLong()) }

    var selectedDate by remember { mutableStateOf(today) }
    LaunchedEffect(weekStart) {
        if (selectedDate !in weekDays) selectedDate = weekStart
    }

    val dateKey = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val meals by remember(dateKey) { viewModel.mealsForDate(dateKey) }
        .collectAsState(initial = emptyList())
    val recipes by viewModel.recipes.collectAsState()
    val profile by viewModel.profile.collectAsState()

    val monthCounts by remember(gridMonth) {
        viewModel.mealCountsBetween(
            gridMonth.atDay(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
            gridMonth.atEndOfMonth().format(DateTimeFormatter.ISO_LOCAL_DATE))
    }.collectAsState(initial = emptyList())

    val dayCost by remember(dateKey) { viewModel.plannedCostForDate(dateKey) }
        .collectAsState(initial = null)
    val dayBudget by remember(dateKey) { viewModel.budgetForDate(dateKey) }
        .collectAsState(initial = null)
    val weekCost by remember(weekStart) {
        viewModel.plannedCostBetween(
            weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE),
            weekStart.plusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE))
    }.collectAsState(initial = null)

    var waterGlasses by remember(dateKey) {
        mutableIntStateOf(WaterPrefs.get(context, dateKey))
    }

    var pickerMealType by remember { mutableStateOf<String?>(null) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }

    // ---- Undo snackbar ----
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun removeWithUndo(plan: MealPlan) {
        viewModel.removeMeal(plan)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.meal_removed),
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreMeal(plan)
        }
    }

    val showCostCard = (dayCost != null && dayCost!! > 0.0) ||
        dayBudget != null || (weekCost ?: 0.0) > 0.0

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_planner)) },
                actions = {
                    IconButton(onClick = onOpenBudget) {
                        Icon(Icons.Rounded.AccountBalance,
                            contentDescription = stringResource(R.string.budget_screen))
                    }
                    IconButton(onClick = { onOpenShoppingList(dateKey) }) {
                        Icon(Icons.Default.ShoppingCart,
                            contentDescription = stringResource(R.string.shopping_list))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // ---- Calendar header: title + mode toggle + copy + arrows ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (planMode == "week") selectedDate.format(monthYear)
                        else gridMonth.format(monthYear),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (planMode == "week") {
                        Text(
                            "${weekStart.format(monthDay)} – ${weekStart.plusDays(6).format(monthDay)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.height(32.dp)) {
                    SegmentedButton(
                        selected = planMode == "week",
                        onClick = { planMode = "week" },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text(stringResource(R.string.view_week),
                        style = MaterialTheme.typography.labelSmall) }
                    SegmentedButton(
                        selected = planMode == "month",
                        onClick = { planMode = "month" },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text(stringResource(R.string.view_month),
                        style = MaterialTheme.typography.labelSmall) }
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { showCopyDialog = true }) {
                    Icon(Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.copy_week))
                }
                FilledTonalIconButton(onClick = {
                    if (planMode == "week") weekOffset -= 1 else monthOffset -= 1
                }) {
                    Icon(Icons.Default.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.previous_week))
                }
                Spacer(Modifier.width(4.dp))
                FilledTonalIconButton(onClick = {
                    if (planMode == "week") weekOffset += 1 else monthOffset += 1
                }) {
                    Icon(Icons.Default.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.next_week))
                }
            }

            // ---- Day picker: chips (week) or month grid (month) ----
            if (planMode == "week") {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    weekDays.forEach { day ->
                        DayChip(
                            day = day,
                            isSelected = day == selectedDate,
                            isToday = day == today,
                            onClick = { selectedDate = day }
                        )
                    }
                }
            } else {
                MonthGrid(
                    month = gridMonth,
                    selected = selectedDate,
                    today = today,
                    counts = monthCounts.mapKeys { LocalDate.parse(it.key) },
                    onSelect = { day ->
                        selectedDate = day
                        planMode = "week"
                        weekOffset = ChronoUnit.WEEKS.between(
                            today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                            day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        ).toInt()
                    }
                )
            }

            // ---- Water card ----
            ElevatedCard(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            androidx.compose.material.icons.Icons.Rounded.WaterDrop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.water_title) + "  ·  " +
                                stringResource(R.string.water_glasses,
                                    waterGlasses, WaterPrefs.WATER_GOAL),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
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
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = {
                            (waterGlasses.toFloat() / WaterPrefs.WATER_GOAL).coerceIn(0f, 1f)
                        },
                        color = if (waterGlasses >= WaterPrefs.WATER_GOAL)
                            MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                }
            }

            // ---- Cost / budget card ----
            if (showCostCard) {
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        if ((weekCost ?: 0.0) > 0.0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.this_week_planned,
                                        formatMoney(weekCost ?: 0.0)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Payments, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.estimated_cost,
                                    formatMoney(dayCost ?: 0.0)),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            if (dayBudget == null) {
                                TextButton(onClick = { showBudgetDialog = true }) {
                                    Text(stringResource(R.string.set_budget))
                                }
                            } else {
                                IconButton(onClick = { showBudgetDialog = true }) {
                                    Icon(Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.edit_budget))
                                }
                            }
                        }
                        dayBudget?.let { b ->
                            val spent = dayCost ?: 0.0
                            val fraction = if (b.amount > 0) (spent / b.amount).toFloat() else 0f
                            LinearProgressIndicator(
                                progress = { fraction.coerceIn(0f, 1f) },
                                color = if (spent > b.amount) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().height(6.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (spent <= b.amount)
                                    stringResource(R.string.budget_remaining,
                                        formatMoney(b.amount - spent))
                                else stringResource(R.string.budget_over,
                                    formatMoney(spent - b.amount)),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (spent > b.amount) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ---- Meal sections ----
            if (recipes.isEmpty() && meals.isEmpty()) {
                com.example.mealplanner.ui.components.EmptyState(
                    icon = Icons.Default.DateRange,
                    title = stringResource(R.string.screen_planner),
                    message = stringResource(R.string.no_recipes_yet)
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    listOf("Breakfast", "Lunch", "Dinner").forEach { type ->
                        val sectionMeals = meals.filter { it.mealPlan.mealType == type }
                        val label = mealTypeLabel(type)
                        item(key = "header-$type") {
                            SectionHeader(
                                title = if (sectionMeals.isEmpty()) label
                                else "$label  ·  ${sectionMeals.size}",
                                action = {
                                    IconButton(onClick = { pickerMealType = type }) {
                                        Icon(Icons.Default.Add, contentDescription =
                                            stringResource(R.string.add_meal_to, label))
                                    }
                                }
                            )
                        }
                        if (sectionMeals.isEmpty()) {
                            item(key = "empty-$type") {
                                Text(
                                    stringResource(R.string.nothing_planned),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                                )
                            }
                        } else {
                            item(key = "meals-$type") {
                                ReorderableColumn(
                                    items = sectionMeals,
                                    onMove = { from, to ->
                                        val ids = sectionMeals.map { it.mealPlan.id }
                                            .toMutableList()
                                        val moved = ids.removeAt(from)
                                        ids.add(to.coerceIn(0, ids.size), moved)
                                        viewModel.reorderMeals(ids)
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) { entry, dragModifier ->
                                    MealCard(
                                        entry = entry,
                                        dragModifier = dragModifier,
                                        profile = profile,
                                        onOpen = { onOpenRecipe(entry.recipe.id) },
                                        onToggleCooked = { viewModel.toggleCooked(entry.mealPlan) },
                                        onRemove = { removeWithUndo(entry.mealPlan) }
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    // ---- Pick recipe dialog ----
    pickerMealType?.let { type ->
        PickRecipeDialog(
            recipes = recipes.map { it.recipe },
            onPick = { recipeId ->
                val servings = recipes.firstOrNull { it.recipe.id == recipeId }
                    ?.recipe?.servings ?: 1
                viewModel.addMeal(dateKey, type, recipeId, servings)
                pickerMealType = null
            },
            onDismiss = { pickerMealType = null }
        )
    }

    // ---- Budget dialog ----
    if (showBudgetDialog) {
        BudgetDialog(
            current = dayBudget?.amount,
            onConfirm = { amount ->
                viewModel.setBudget(dateKey, amount)
                showBudgetDialog = false
            },
            onDismiss = { showBudgetDialog = false }
        )
    }

    // ---- Copy week dialog ----
    if (showCopyDialog) {
        var targetOffset by remember { mutableLongStateOf(1L) }   // +1 week default
        var replace by remember { mutableStateOf(true) }
        val targetStart = weekStart.plusWeeks(targetOffset)
        val weekFmt = DateTimeFormatter.ofPattern("MMM d")

        AlertDialog(
            onDismissRequest = { showCopyDialog = false },
            title = { Text(stringResource(R.string.copy_week_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.copy_week_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(-1L to weekStart.minusWeeks(1), 1L to weekStart.plusWeeks(1))
                            .forEach { (offset, ws) ->
                                FilterChip(
                                    selected = targetOffset == offset,
                                    onClick = { targetOffset = offset },
                                    label = {
                                        Text(stringResource(
                                            R.string.copy_week_to,
                                            "${ws.format(weekFmt)} – ${ws.plusDays(6).format(weekFmt)}"
                                        ))
                                    }
                                )
                            }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = replace,
                            onCheckedChange = { replace = it }
                        )
                        Text(stringResource(R.string.replace_existing),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCopyDialog = false
                    viewModel.copyWeek(
                        weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        targetStart.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        replace
                    ) { count ->
                        Toast.makeText(context,
                            context.getString(R.string.copy_week_done, count),
                            Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.copy_week_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showCopyDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DayChip(day: LocalDate, isSelected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val container =
        if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant
    val content =
        if (isSelected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .widthIn(min = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall, color = content
        )
        Text(day.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium, color = content)
        Box(
            Modifier.padding(top = 2.dp).size(4.dp)
                .clip(CircleShape)
                .background(if (isToday) content else Color.Transparent)
        )
    }
}

/** Monday-first month grid with meal-count dots; tapping a day jumps to its week. */
@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    counts: Map<LocalDate, Int>,
    onSelect: (LocalDate) -> Unit
) {
    val lead = month.atDay(1).dayOfWeek.value - 1
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        (List(lead) { null as LocalDate? } + days).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(
                        Modifier.weight(1f).padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            val isSelected = day == selected
                            val isToday = day == today
                            Surface(
                                onClick = { onSelect(day) },
                                shape = CircleShape,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isToday -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> Color.Transparent
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        day.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            isToday -> MaterialTheme.colorScheme.onSecondaryContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Box(
                                        Modifier.padding(top = 2.dp).size(4.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if ((counts[day] ?: 0) > 0) {
                                                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.primary
                                                } else Color.Transparent
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MealCard(
    entry: MealPlanWithRecipe,
    dragModifier: Modifier,
    profile: DietaryProfile,
    onOpen: () -> Unit,
    onToggleCooked: () -> Unit,
    onRemove: () -> Unit
) {
    val recipe = entry.recipe
    val cooked = entry.mealPlan.cookedAt.isNotEmpty()
    val clash = AllergenUtil.run { entry.hasClash(profile) }
    ElevatedCard(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).then(dragModifier)
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Menu, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            if (recipe.photoPath != null) {
                AsyncImage(
                    model = File(recipe.photoPath!!),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.small)
                )
            } else {
                CategoryTile(recipe.category, size = 44.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        recipe.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (cooked) MaterialTheme.colorScheme.onSurfaceVariant
                        else LocalContentColor.current,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (recipe.isFavorite) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.Favorite, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    stringResource(
                        R.string.servings_count_planned,
                        entry.mealPlan.servings.takeIf { it > 0 } ?: recipe.servings
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (clash) {
                Icon(Icons.Rounded.Warning, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onToggleCooked) {
                Icon(
                    if (cooked) Icons.Rounded.CheckCircle
                    else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = stringResource(
                        if (cooked) R.string.cooked else R.string.mark_cooked),
                    tint = if (cooked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close,
                    contentDescription = stringResource(R.string.remove_from_plan))
            }
        }
    }
}

@Composable
private fun PickRecipeDialog(
    recipes: List<Recipe>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_recipe)) },
        text = {
            if (recipes.isEmpty()) {
                Text(stringResource(R.string.no_recipes_yet))
            } else {
                LazyColumn {
                    items(recipes, key = { it.id }) { recipe ->
                        Text(
                            text = recipe.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(recipe.id) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
