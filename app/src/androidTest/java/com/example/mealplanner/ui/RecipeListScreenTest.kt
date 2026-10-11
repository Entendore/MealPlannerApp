package com.example.mealplanner.ui

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.waitUntil
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mealplanner.JsonBackupResult
import com.example.mealplanner.R
import com.example.mealplanner.data.DietaryProfile
import com.example.mealplanner.data.Ingredient
import com.example.mealplanner.data.Recipe
import com.example.mealplanner.data.RecipeWithIngredients
import com.example.mealplanner.ui.screens.RecipeListScreen
import com.example.mealplanner.util.JsonBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeListScreenTest {

    @get:Rule val rule = createComposeRule()

    private fun item(
        name: String, favorite: Boolean = false, category: String = "Dinner",
        tags: List<String> = emptyList(), ingredientNames: List<String> = emptyList(),
        allergens: List<String> = emptyList()
    ) = RecipeWithIngredients(
        recipe = Recipe(name = name, category = category, isFavorite = favorite, tags = tags),
        ingredients = ingredientNames.map { Ingredient(name = it, allergens = allergens) }
    )

    private fun setContent(
        recipes: List<RecipeWithIngredients>,
        profile: DietaryProfile = DietaryProfile(),
        hideClashing: Boolean = false,
        onToggleHideClashing: (Boolean) -> Unit = {},
        onToggleFavorite: (Int) -> Unit = {}
    ) {
        rule.setContent {
            MaterialTheme {
                RecipeListScreen(
                    recipes = recipes, profile = profile, hideClashing = hideClashing,
                    onToggleHideClashing = onToggleHideClashing,
                    onRecipeClick = {}, onAddRecipe = {}, onScan = {},
                    onExport = { _: Uri -> }, onExportCookbook = { _: Uri -> },
                    onToggleFavorite = onToggleFavorite,
                    onImportFile = { _: Uri, _: (JsonBackupResult?) -> Unit -> },
                    onApplyImport = { _: JsonBackup.Parsed, _: Boolean,
                                      _: (JsonBackup.Counts) -> Unit -> }
                )
            }
        }
    }

    private val applePie = item("Apple Pie", favorite = true, ingredientNames = listOf("Flour"))
    private val borscht = item("Borscht", ingredientNames = listOf("Beetroot"))

    @Test
    fun searchByIngredientMatchesRecipesWhoseNameDoesntMatch() {
        setContent(listOf(applePie, borscht))
        rule.onNodeWithText(rule.activity.getString(R.string.search_hint))
            .performTextInput("beetroot")
        rule.onNodeWithText("Borscht").assertExists()
        rule.onNodeWithText("Apple Pie").assertDoesNotExist()
    }

    @Test
    fun favoritesChipFilters() {
        setContent(listOf(applePie, borscht))
        rule.onNodeWithText(rule.activity.getString(R.string.favorites)).performClick()
        rule.onNodeWithText("Apple Pie").assertExists()
        rule.onNodeWithText("Borscht").assertDoesNotExist()
    }

    @Test
    fun gridModeLongPressTogglesFavorite() {
        var toggled = -1
        setContent(listOf(applePie, borscht), onToggleFavorite = { toggled = it })
        rule.onNodeWithContentDescription(
            rule.activity.getString(R.string.toggle_layout)).performClick()
        rule.onNodeWithText("Borscht").performTouchInput { longClick() }
        rule.waitUntil(3_000) { toggled == borscht.recipe.id }
    }

    @Test
    fun categoryChipsShowCountsAndFilter() {
        setContent(listOf(applePie.copy(recipe = applePie.recipe.copy(category = "Dessert")), borscht))
        rule.onNodeWithText("Dessert · 1").assertExists()
        rule.onNodeWithText("Dessert · 1").performClick()
        rule.onNodeWithText("Apple Pie").assertExists()
        rule.onNodeWithText("Borscht").assertDoesNotExist()
    }

    @Test
    fun tagChipsAppearAndFilter() {
        setContent(listOf(item("A", tags = listOf("One-pot")), item("B")))
        rule.onNodeWithText("#One-pot").performClick()
        rule.onNodeWithText("A").assertExists()
        rule.onNodeWithText("B").assertDoesNotExist()
    }

    @Test
    fun hideAllergenHidesClashingRecipesAndEmptyStateOffersShowAll() {
        val dairy1 = item("Cheesy Pasta", ingredientNames = listOf("Cheddar"),
            allergens = listOf("Dairy"))
        val dairy2 = item("Milk Shake", ingredientNames = listOf("Milk"),
            allergens = listOf("Dairy"))
        var toggleValue = true
        setContent(
            recipes = listOf(dairy1, dairy2),
            profile = DietaryProfile(allergies = listOf("Dairy")),
            hideClashing = true,
            onToggleHideClashing = { toggleValue = it }
        )
        rule.onNodeWithText("Cheesy Pasta").assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(R.string.no_safe_recipes_title)).assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.show_all)).performClick()
        assertFalse(toggleValue)
    }

    @Test
    fun gridToggleKeepsRecipesVisible() {
        setContent(listOf(applePie, borscht))
        rule.onNodeWithContentDescription(
            rule.activity.getString(R.string.toggle_layout)).performClick()
        rule.onNodeWithText("Apple Pie").assertExists()
        rule.onNodeWithText("Borscht").assertExists()
    }
}
