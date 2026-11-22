package com.example.mealplanner.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mealplanner.MealPlannerViewModel
import com.example.mealplanner.R
import com.example.mealplanner.data.Ingredient
import com.example.mealplanner.data.MealPlan
import com.example.mealplanner.data.MealPlannerDatabase
import com.example.mealplanner.data.Recipe
import com.example.mealplanner.ui.screens.ShoppingListScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShoppingListScreenTest {

    private lateinit var db: MealPlannerDatabase
    private lateinit var viewModel: MealPlannerViewModel

    @get:Rule val rule = createComposeRule()

    @Before fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, MealPlannerDatabase::class.java)
            .allowMainThreadQueries().build()
        viewModel = MealPlannerViewModel(app, db)
    }
    @After fun tearDown() = db.close()

    @Test
    fun showsScaledMergedRowAndPersistsActualPrice() = runBlocking {
        val recipeId = db.recipeDao().insertRecipe(Recipe(name = "Pancakes", servings = 4)).toInt()
        db.recipeDao().insertIngredients(listOf(
            Ingredient(recipeId = recipeId, name = "Milk", quantity = 2.0, unit = "cups",
                price = 2.0)))
        db.mealPlanDao().insert(MealPlan(date = "2024-06-01", mealType = "Breakfast",
            recipeId = recipeId, servings = 2, sortOrder = 0))

        rule.setContent {
            MaterialTheme {
                ShoppingListScreen(date = "2024-06-01", viewModel = viewModel, onBack = {})
            }
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Milk").fetchSemanticsNodes().isNotEmpty()
        }
        // 2 cups × 2/4 = 1 → canonical "cup", singular form
        rule.onNodeWithText("1 cup").assertExistsCompat()

        // Record an actual price and verify it lands in the purchases table
        rule.onNodeWithText("Milk").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.actual_price_hint))
            .performTextInput("3.5")
        rule.onNodeWithText(rule.activity.getString(R.string.save)).performClick()
        rule.waitUntil(5_000) {
            runBlocking { db.purchaseDao().findFor("2024-06-01", "Milk", "cup") != null }
        }
        val purchase = db.purchaseDao().findFor("2024-06-01", "Milk", "cup")!!
        assertEquals(1.0, purchase.quantity, 1e-9)         // scaled quantity snapshot
        assertEquals(1.0, purchase.estimatedPrice, 1e-9)   // scaled estimate (2.0 × 0.5)
        assertEquals(3.5, purchase.actualPrice, 1e-9)
        assertNotNull(purchase)
    }
}
