package com.example.mealplanner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mealplanner.R
import com.example.mealplanner.data.DietaryProfile
import com.example.mealplanner.data.Ingredient
import com.example.mealplanner.data.Recipe
import com.example.mealplanner.data.RecipeWithIngredients
import com.example.mealplanner.ui.screens.RecipeDetailScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeDetailScreenTest {

    @get:Rule val rule = createComposeRule()

    private fun recipe(instructions: String = "1. Mix\n2. Cook 5 minutes") =
        RecipeWithIngredients(
            recipe = Recipe(name = "Pancakes", servings = 4, instructions = instructions),
            ingredients = listOf(Ingredient(name = "Flour", quantity = 2.0, unit = "cups"))
        )

    private fun setContent(
        item: RecipeWithIngredients = recipe(),
        profile: DietaryProfile = DietaryProfile(),
        onDuplicate: (Int) -> Unit = {},
        onToggleFavorite: (Int) -> Unit = {}
    ) {
        rule.setContent {
            MaterialTheme {
                RecipeDetailScreen(
                    recipe = item, profile = profile,
                    onBack = {}, onEdit = {}, onDelete = {},
                    onAddToPlan = { _, _, _, _ -> }, onExportPdf = {},
                    onDuplicate = onDuplicate, onToggleFavorite = onToggleFavorite
                )
            }
        }
    }

    @Test
    fun servingsScalerRecomputesQuantitiesAndResets() {
        setContent()
        rule.onNodeWithText("2 cups").assertExists()       // base 4 servings
        rule.onNodeWithContentDescription(
            rule.activity.getString(R.string.increase_servings)).performClick()
        rule.onNodeWithContentDescription(
            rule.activity.getString(R.string.increase_servings)).performClick()   // ×1.5
        rule.onNodeWithText("3 cups").assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.reset)).performClick()
        rule.onNodeWithText("2 cups").assertExists()
    }

    @Test
    fun cookModeStepsThroughAndCloses() {
        setContent()
        rule.onNodeWithText(rule.activity.getString(R.string.cook_mode)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.step_of, 1, 2)).assertExists()
        rule.onNodeWithText("1. Mix").assertExists()       // raw step text is preserved
        rule.onNodeWithText(rule.activity.getString(R.string.next)).performClick()
        rule.onNodeWithText("2. Cook 5 minutes").assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.start_timer, "0:05")).assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.done)).performClick()
        rule.onNodeWithText("2. Cook 5 minutes").assertDoesNotExist()
    }

    @Test
    fun clashBannerAppearsForClashingProfiles() {
        setContent(
            item = RecipeWithIngredients(
                recipe = Recipe(name = "Caesar", servings = 2),
                ingredients = listOf(Ingredient(name = "Parmesan", allergens = listOf("Dairy")))
            ),
            profile = DietaryProfile(allergies = listOf("Dairy"))
        )
        rule.onNodeWithText(rule.activity.getString(R.string.allergy_clash_title)).assertExists()
    }

    @Test
    fun duplicateAndFavoriteCallbacksFire() {
        var duplicated = -1
        var toggled = -1
        setContent(onDuplicate = { duplicated = it }, onToggleFavorite = { toggled = it })
        rule.onNodeWithContentDescription(
            rule.activity.getString(R.string.duplicate)).performClick()
        rule.onNodeWithContentDescription(
            rule.activity.getString(R.string.toggle_favorite)).performClick()
        assertEquals(1, duplicated)     // first auto-generated id
        assertEquals(1, toggled)
    }

    @Test
    fun oversizedRecipeShowsTooLargeMessageInsteadOfQr() {
        setContent(item = recipe(instructions = "x".repeat(12_000)))
        rule.onNodeWithContentDescription(
            rule.activity.getString(R.string.qr_share_title)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.qr_too_large)).assertExists()
    }
}
