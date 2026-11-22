package com.example.mealplanner.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.mealplanner.MealPlannerViewModel
import com.example.mealplanner.R
import com.example.mealplanner.ui.screens.BudgetScreen
import com.example.mealplanner.ui.screens.InsightsScreen
import com.example.mealplanner.ui.screens.MealPlannerScreen
import com.example.mealplanner.ui.screens.ProfileScreen
import com.example.mealplanner.ui.screens.RecipeDetailScreen
import com.example.mealplanner.ui.screens.RecipeEditorScreen
import com.example.mealplanner.ui.screens.RecipeListScreen
import com.example.mealplanner.ui.screens.ScannerScreen
import com.example.mealplanner.ui.screens.ShoppingListScreen
import com.example.mealplanner.ui.screens.TodayScreen
import com.example.mealplanner.util.ProfileQr
import com.example.mealplanner.util.RecipeQr
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Routes {
    const val TODAY = "today"
    const val RECIPES = "recipes"
    const val PLANNER = "planner"
    const val PROFILE = "profile"
    const val INSIGHTS = "insights"
    const val BUDGET = "budget"
    const val SCANNER = "scanner"
    const val RECIPE_DETAIL = "recipe_detail/{recipeId}"
    const val RECIPE_EDITOR = "recipe_editor?recipeId={recipeId}"
    const val SHOPPING_LIST = "shopping_list/{date}"

    fun recipeDetail(id: Int) = "recipe_detail/$id"
    fun recipeEditor(id: Int? = null) =
        if (id != null) "recipe_editor?recipeId=$id" else "recipe_editor"
    fun shoppingList(date: String) = "shopping_list/$date"
}

private fun exportToast(context: android.content.Context, success: Boolean, uri: Uri) {
    Toast.makeText(
        context,
        if (success) context.getString(R.string.export_success, uri.lastPathSegment ?: "")
        else context.getString(R.string.export_failed),
        Toast.LENGTH_LONG
    ).show()
}

/**
 * Shared handler for scan results landing on recipe-importing screens
 * (Today + Recipes). Product barcodes and profile codes are redirected
 * with guidance toasts; nothing is ever imported silently.
 */
@Composable
private fun RecipeScanResultEffect(entry: NavBackStackEntry, viewModel: MealPlannerViewModel) {
    val context = LocalContext.current
    val scanResult by entry.savedStateHandle.getStateFlow("scan_result", "").collectAsState()
    LaunchedEffect(scanResult) {
        if (scanResult.isNotEmpty()) {
            entry.savedStateHandle["scan_result"] = ""
            when {
                ProfileQr.isProfilePayload(scanResult) ->
                    Toast.makeText(context, R.string.scan_profile_on_profile_tab,
                        Toast.LENGTH_LONG).show()
                else -> {
                    val shared = RecipeQr.decode(scanResult)
                    when {
                        shared != null -> viewModel.saveRecipe(shared.recipe, shared.ingredients) {
                            Toast.makeText(context,
                                context.getString(R.string.recipe_imported, shared.recipe.name),
                                Toast.LENGTH_LONG).show()
                        }
                        RecipeQr.isRecipePayload(scanResult) ->
                            Toast.makeText(context, R.string.invalid_code, Toast.LENGTH_SHORT).show()
                        else ->
                            Toast.makeText(context, R.string.scan_products_in_editor,
                                Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: MealPlannerViewModel) {
    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Column(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier.weight(1f)
        ) {
            composable(Routes.TODAY) { entry ->
                RecipeScanResultEffect(entry, viewModel)
                TodayScreen(
                    viewModel = viewModel,
                    onOpenRecipe = { navController.navigate(Routes.recipeDetail(it)) },
                    onOpenShopping = { navController.navigate(Routes.shoppingList(it)) },
                    onOpenInsights = { navController.navigate(Routes.INSIGHTS) },
                    onNewRecipe = { navController.navigate(Routes.recipeEditor()) },
                    onScan = { navController.navigate(Routes.SCANNER) },
                    onOpenPlanner = { navController.navigate(Routes.PLANNER) }
                )
            }

            composable(Routes.RECIPES) { entry ->
                RecipeScanResultEffect(entry, viewModel)
                val recipes by viewModel.recipes.collectAsState()
                val profile by viewModel.profile.collectAsState()
                RecipeListScreen(
                    recipes = recipes,
                    profile = profile,
                    hideClashing = profile.hideClashing,
                    onToggleHideClashing = viewModel::setHideClashing,
                    onRecipeClick = { navController.navigate(Routes.recipeDetail(it)) },
                    onAddRecipe = { navController.navigate(Routes.recipeEditor()) },
                    onScan = { navController.navigate(Routes.SCANNER) },
                    onExport = { uri ->
                        viewModel.exportData(uri) { success -> exportToast(it, success, uri) }
                    },
                    onExportCookbook = { uri ->
                        viewModel.exportCookbookPdf(uri) { success -> exportToast(it, success, uri) }
                    },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onImportFile = { uri, cb -> viewModel.parseBackup(uri, cb) },
                    onApplyImport = { parsed, withProfile, cb ->
                        viewModel.applyImport(parsed, withProfile, cb)
                    }
                )
            }

            composable(Routes.RECIPE_DETAIL) { entry ->
                val recipeId = entry.arguments?.getString("recipeId")?.toIntOrNull()
                LaunchedEffect(recipeId) { recipeId?.let { viewModel.loadRecipe(it) } }
                val loaded by viewModel.selectedRecipe.collectAsState()
                val profile by viewModel.profile.collectAsState()
                RecipeDetailScreen(
                    recipe = loaded?.takeIf { it.recipe.id == recipeId },
                    profile = profile,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.recipeEditor(it)) },
                    onDelete = { recipeToDelete ->
                        viewModel.deleteRecipe(recipeToDelete) { navController.popBackStack() }
                    },
                    onAddToPlan = { dates, mealType, servings, id ->
                        viewModel.addMeals(dates, mealType, id, servings)
                    },
                    onExportPdf = { uri ->
                        val id = recipeId
                        if (id != null) {
                            viewModel.exportRecipePdf(uri, id) { success ->
                                exportToast(navController.context, success, uri)
                            }
                        }
                    },
                    onDuplicate = { id ->
                        viewModel.duplicateRecipe(id) { newId ->
                            Toast.makeText(navController.context,
                                R.string.recipe_duplicated, Toast.LENGTH_SHORT).show()
                            navController.navigate(Routes.recipeEditor(newId))
                        }
                    },
                    onToggleFavorite = viewModel::toggleFavorite
                )
            }

            composable(Routes.RECIPE_EDITOR) { entry ->
                val recipeId = entry.arguments?.getString("recipeId")?.toIntOrNull()
                LaunchedEffect(recipeId) { recipeId?.let { viewModel.loadRecipe(it) } }
                val loaded by viewModel.selectedRecipe.collectAsState()
                val scanResult by entry.savedStateHandle.getStateFlow("scan_result", "").collectAsState()
                RecipeEditorScreen(
                    recipeId = recipeId,
                    existing = loaded,
                    onSave = { recipe, ingredients ->
                        viewModel.saveRecipe(recipe, ingredients) { navController.popBackStack() }
                    },
                    onBack = { navController.popBackStack() },
                    onScan = { navController.navigate(Routes.SCANNER) },
                    onBarcodeLookup = { code, callback -> viewModel.lookupBarcode(code, callback) },
                    scanResult = scanResult.ifEmpty { null },
                    onScanConsumed = { entry.savedStateHandle["scan_result"] = "" }
                )
            }

            composable(Routes.PLANNER) {
                MealPlannerScreen(
                    viewModel = viewModel,
                    onOpenRecipe = { navController.navigate(Routes.recipeDetail(it)) },
                    onOpenShoppingList = { navController.navigate(Routes.shoppingList(it)) },
                    onOpenBudget = { navController.navigate(Routes.BUDGET) }
                )
            }

            composable(Routes.SHOPPING_LIST) { entry ->
                val date = entry.arguments?.getString("date")
                    ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                ShoppingListScreen(
                    date = date,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.BUDGET) {
                BudgetScreen(viewModel) { navController.popBackStack() }
            }

            composable(Routes.PROFILE) { entry ->
                val scanResult by entry.savedStateHandle.getStateFlow("scan_result", "").collectAsState()
                ProfileScreen(
                    viewModel = viewModel,
                    scanResult = scanResult.ifEmpty { null },
                    onScanConsumed = { entry.savedStateHandle["scan_result"] = "" },
                    onScanProfile = { navController.navigate(Routes.SCANNER) }
                )
            }

            composable(Routes.INSIGHTS) {
                InsightsScreen(viewModel) { navController.popBackStack() }
            }

            composable(Routes.SCANNER) {
                ScannerScreen(
                    onResult = { value ->
                        navController.previousBackStackEntry?.savedStateHandle
                            ?.set("scan_result", value)
                        navController.popBackStack()
                    },
                    onClose = { navController.popBackStack() }
                )
            }
        }

        // Bottom navigation (top-level tabs only)
        if (currentRoute in listOf(Routes.TODAY, Routes.RECIPES, Routes.PLANNER, Routes.PROFILE)) {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == Routes.TODAY,
                    onClick = {
                        navController.navigate(Routes.TODAY) {
                            popUpTo(Routes.TODAY) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Rounded.Today, contentDescription = stringResource(R.string.tab_today)) },
                    label = { Text(stringResource(R.string.tab_today)) }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.RECIPES,
                    onClick = {
                        navController.navigate(Routes.RECIPES) {
                            popUpTo(Routes.TODAY) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.List, contentDescription = stringResource(R.string.screen_recipes)) },
                    label = { Text(stringResource(R.string.screen_recipes)) }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.PLANNER,
                    onClick = {
                        navController.navigate(Routes.PLANNER) {
                            popUpTo(Routes.TODAY) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.screen_planner)) },
                    label = { Text(stringResource(R.string.screen_planner)) }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.PROFILE,
                    onClick = {
                        navController.navigate(Routes.PROFILE) {
                            popUpTo(Routes.TODAY) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Rounded.HealthAndSafety, contentDescription = stringResource(R.string.tab_profile)) },
                    label = { Text(stringResource(R.string.tab_profile)) }
                )
            }
        }
    }
}
