package com.example.mealplanner.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mealplanner.JsonBackupResult
import com.example.mealplanner.R
import com.example.mealplanner.data.DietaryProfile
import com.example.mealplanner.data.RecipeWithIngredients
import com.example.mealplanner.ui.components.EmptyState
import com.example.mealplanner.ui.components.MetaChip
import com.example.mealplanner.ui.components.categoryIcon
import com.example.mealplanner.ui.dietLabel
import com.example.mealplanner.ui.formatMoney
import com.example.mealplanner.util.AllergenUtil
import com.example.mealplanner.util.JsonBackup
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecipeListScreen(
    recipes: List<RecipeWithIngredients>,
    profile: DietaryProfile,
    hideClashing: Boolean,
    onToggleHideClashing: (Boolean) -> Unit,
    onRecipeClick: (Int) -> Unit,
    onAddRecipe: () -> Unit,
    onScan: () -> Unit,
    onExport: (Uri) -> Unit,
    onExportCookbook: (Uri) -> Unit,
    onToggleFavorite: (Int) -> Unit,
    onImportFile: (Uri, (JsonBackupResult?) -> Unit) -> Unit,
    onApplyImport: (JsonBackup.Parsed, Boolean, (JsonBackup.Counts) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedDiet by remember { mutableStateOf<String?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var onlyFavorites by remember { mutableStateOf(false) }
    var gridMode by rememberSaveable { mutableStateOf(false) }

    val categories = remember(recipes) { recipes.map { it.recipe.category }.distinct().sorted() }
    val allTags = remember(recipes) { recipes.flatMap { it.recipe.tags }.distinct().sorted() }

    val filtered = remember(recipes, searchQuery, selectedCategory, selectedDiet,
        selectedTag, onlyFavorites, hideClashing, profile) {
        val query = searchQuery.trim()
        recipes.filter { item ->
            (item.recipe.name.contains(query, ignoreCase = true) ||
                item.ingredients.any { it.name.contains(query, ignoreCase = true) }) &&
                (selectedCategory == null || item.recipe.category == selectedCategory) &&
                (selectedDiet == null || selectedDiet in item.recipe.diets) &&
                (selectedTag == null || selectedTag in item.recipe.tags) &&
                (!onlyFavorites || item.recipe.isFavorite) &&
                (!hideClashing || AllergenUtil.run { item.allergenClash(profile) }.isEmpty())
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onExport) }
    val cookbookLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let(onExportCookbook) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            onImportFile(it) { result ->
                when (result) {
                    is JsonBackupResult.Success -> pendingImport = result
                    is JsonBackupResult.Failure, null ->
                        Toast.makeText(context, R.string.import_invalid, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    var pendingImport by remember { mutableStateOf<JsonBackupResult.Success?>(null) }
    var importProfile by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_recipes)) },
                actions = {
                    IconButton(onClick = { gridMode = !gridMode }) {
                        Icon(
                            if (gridMode) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                            contentDescription = stringResource(R.string.toggle_layout)
                        )
                    }
                    IconButton(onClick = onScan) {
                        Icon(Icons.Default.QrCodeScanner,
                            contentDescription = stringResource(R.string.scan_code))
                    }
                    IconButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) }
                    ) {
                        Icon(Icons.Default.Upload,
                            contentDescription = stringResource(R.string.import_data))
                    }
                    IconButton(
                        onClick = { cookbookLauncher.launch("cookbook-${java.time.LocalDate.now()}.pdf") },
                        enabled = recipes.isNotEmpty()
                    ) {
                        Icon(Icons.Rounded.MenuBook,
                            contentDescription = stringResource(R.string.export_cookbook_pdf))
                    }
                    IconButton(onClick = {
                        exportLauncher.launch(stringResource(R.string.export_file_name))
                    }) {
                        Icon(Icons.Default.Share,
                            contentDescription = stringResource(R.string.export_data))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddRecipe,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_recipe)) }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear,
                                contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Category chips (with counts)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text(stringResource(R.string.filter_all)) }
                )
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            selectedCategory = if (selectedCategory == category) null else category
                        },
                        label = { Text("$category · ${recipes.count { it.recipe.category == category }}") }
                    )
                }
            }

            // Favorites + diet + tag chips
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = onlyFavorites,
                    onClick = { onlyFavorites = !onlyFavorites },
                    leadingIcon = {
                        Icon(
                            if (onlyFavorites) Icons.Default.Favorite
                            else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (onlyFavorites) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    label = { Text(stringResource(R.string.favorites)) }
                )
                AllergenUtil.DIETS.forEach { diet ->
                    FilterChip(
                        selected = selectedDiet == diet,
                        onClick = { selectedDiet = if (selectedDiet == diet) null else diet },
                        leadingIcon = {
                            Icon(com.example.mealplanner.ui.components.dietIcon(diet),
                                contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        label = { Text(dietLabel(diet)) }
                    )
                }
                allTags.forEach { tag ->
                    FilterChip(
                        selected = selectedTag == tag,
                        onClick = { selectedTag = if (selectedTag == tag) null else tag },
                        label = { Text("#$tag") }
                    )
                }
            }

            Text(
                pluralStringResource(R.plurals.recipes_count, filtered.size, filtered.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            when {
                recipes.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.MenuBook,
                    title = stringResource(R.string.empty_recipes_title),
                    message = stringResource(R.string.empty_recipes),
                    actionLabel = stringResource(R.string.add_recipe),
                    onAction = onAddRecipe
                )
                filtered.isEmpty() -> {
                    val allHidden = hideClashing && searchQuery.isBlank() &&
                        selectedCategory == null && selectedDiet == null &&
                        selectedTag == null && !onlyFavorites &&
                        recipes.all { AllergenUtil.run { it.allergenClash(profile) }.isNotEmpty() }
                    if (allHidden) {
                        EmptyState(
                            icon = Icons.Rounded.VisibilityOff,
                            title = stringResource(R.string.no_safe_recipes_title),
                            message = stringResource(R.string.no_safe_recipes_message),
                            actionLabel = stringResource(R.string.show_all),
                            onAction = { onToggleHideClashing(false) }
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Rounded.SearchOff,
                            title = stringResource(R.string.no_results_title),
                            message = stringResource(R.string.empty_search, searchQuery.trim())
                        )
                    }
                }
                else -> if (gridMode) {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalItemSpacing = 10.dp
                    ) {
                        staggeredItems(filtered, key = { it.recipe.id }) { item ->
                            RecipeGridCard(
                                item = item,
                                isClashing = AllergenUtil.run { item.hasClash(profile) },
                                onToggleFavorite = { onToggleFavorite(item.recipe.id) },
                                onClick = { onRecipeClick(item.recipe.id) }
                            )
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(filtered, key = { it.recipe.id }) { item ->
                            Box(Modifier.animateItem()) {
                                RecipeCard(item, profile) {
                                    onRecipeClick(item.recipe.id)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingImport?.let { success ->
        val data = success.data
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.import_title)) },
            text = {
                Column {
                    Text(stringResource(
                        R.string.import_summary,
                        data.recipes.size, data.plans.size, data.budgets.size))
                    if (data.profile != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = importProfile,
                                onCheckedChange = { importProfile = it })
                            Text(stringResource(R.string.import_profile_too),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val toApply = data
                    pendingImport = null
                    onApplyImport(toApply, importProfile) { counts ->
                        Toast.makeText(context,
                            context.getString(R.string.import_done,
                                counts.recipesAdded, counts.recipesUpdated,
                                counts.plansImported, counts.budgetsImported),
                            Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.import_label)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun RecipeCard(
    item: RecipeWithIngredients,
    profile: DietaryProfile,
    onClick: () -> Unit
) {
    val recipe = item.recipe
    val clash = AllergenUtil.run { item.hasClash(profile) }
    val cost = item.ingredients.sumOf { it.price }
    ElevatedCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        if (recipe.photoPath != null) {
            Column {
                Box {
                    AsyncImage(
                        model = File(recipe.photoPath!!),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                    )
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.45f)
                            )
                        )
                    )
                    MetaChip(
                        icon = categoryIcon(recipe.category),
                        text = recipe.category,
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                    )
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (recipe.isFavorite) {
                            Icon(Icons.Default.Favorite,
                                contentDescription = stringResource(R.string.favorites),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        if (clash) {
                            Icon(Icons.Default.Warning, contentDescription = null,
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(recipe.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    CardMetaRow(recipe, item.ingredients.size, cost)
                }
            }
        } else {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.example.mealplanner.ui.components.CategoryTile(recipe.category, size = 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(recipe.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    CardMetaRow(recipe, item.ingredients.size, cost)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (recipe.isFavorite) {
                        Icon(Icons.Default.Favorite,
                            contentDescription = stringResource(R.string.favorites),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp))
                    }
                    if (clash) {
                        Icon(Icons.Default.Warning, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecipeGridCard(
    item: RecipeWithIngredients,
    isClashing: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    val recipe = item.recipe
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.combinedClickable(
            onClick = onClick, onLongClick = onToggleFavorite
        )
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(if (recipe.photoPath != null) 140.dp else 96.dp)) {
                if (recipe.photoPath != null) {
                    AsyncImage(
                        model = File(recipe.photoPath!!), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxSize()) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(categoryIcon(recipe.category),
                                contentDescription = recipe.category,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp))
                        }
                    }
                }
                Row(
                    Modifier.align(Alignment.TopEnd).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (recipe.isFavorite) {
                        Icon(Icons.Default.Favorite,
                            contentDescription = stringResource(R.string.favorites),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp))
                    }
                    if (isClashing) {
                        Icon(Icons.Default.Warning, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp))
                    }
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(recipe.name, style = MaterialTheme.typography.titleSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                val minutes = recipe.prepTimeMinutes + recipe.cookTimeMinutes
                Text(
                    listOfNotNull(
                        if (minutes > 0) stringResource(R.string.minutes_total, minutes) else null,
                        pluralStringResource(
                            R.plurals.servings_count, recipe.servings, recipe.servings)
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CardMetaRow(recipe: com.example.mealplanner.data.Recipe,
                        ingredientCount: Int, cost: Double) {
    val minutes = recipe.prepTimeMinutes + recipe.cookTimeMinutes
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (minutes > 0) {
            MetaChip(Icons.Default.Schedule, stringResource(R.string.minutes_total, minutes))
        }
        MetaChip(Icons.Default.Person,
            pluralStringResource(R.plurals.servings_count, recipe.servings, recipe.servings))
        MetaChip(Icons.Default.ShoppingBasket,
            pluralStringResource(R.plurals.ingredients_count, ingredientCount, ingredientCount))
        if (cost > 0) {
            MetaChip(Icons.Default.Payments, stringResource(R.string.cost_total, formatMoney(cost)))
        }
    }
}
