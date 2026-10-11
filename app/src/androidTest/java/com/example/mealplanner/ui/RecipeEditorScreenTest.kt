package com.example.mealplanner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mealplanner.R
import com.example.mealplanner.ui.screens.RecipeEditorScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeEditorScreenTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun emptyNameBlocksSaveAndShowsInlineError() {
        var saves = 0
        rule.setContent {
            MaterialTheme {
                RecipeEditorScreen(
                    recipeId = null, existing = null,
                    onSave = { _, _ -> saves++ },
                    onBack = {}, onScan = {},
                    onBarcodeLookup = { _, _ -> },
                    scanResult = null, onScanConsumed = {}
                )
            }
        }
        val activity = rule.activity

        rule.onNodeWithText(activity.getString(R.string.save_recipe)).performClick()
        rule.onNodeWithText(activity.getString(R.string.error_name_required)).assertExistsCompat()
        assertEquals(0, saves)

        rule.onNodeWithText(activity.getString(R.string.recipe_name)).performTextInput("Lasagna")
        rule.onNodeWithText(activity.getString(R.string.save_recipe)).performClick()
        assertEquals(1, saves)
    }
}
