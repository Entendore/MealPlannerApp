package com.example.mealplanner.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mealplanner.MealPlannerViewModel
import com.example.mealplanner.R
import com.example.mealplanner.data.MealPlan
import com.example.mealplanner.data.MealPlannerDatabase
import com.example.mealplanner.data.Recipe
import com.example.mealplanner.ui.screens.MealPlannerScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class PlannerUndoTest {

    private lateinit var db: MealPlannerDatabase
    private lateinit var vm: MealPlannerViewModel

    @get:Rule val rule = createComposeRule()

    @Before fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, MealPlannerDatabase::class.java)
            .allowMainThreadQueries().build()
        vm = MealPlannerViewModel(app, db)
    }
    @After fun tearDown() = db.close()

    @Test
    fun removingShowsSnackbarAndUndoRestoresTheMeal() = runBlocking {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val id = db.recipeDao().insertRecipe(Recipe(name = "Undo Pasta")).toInt()
        db.mealPlanDao().insert(MealPlan(date = today, mealType = "Dinner",
            recipeId = id, sortOrder = 0))

        rule.setContent {
            MaterialTheme {
                MealPlannerScreen(
                    viewModel = vm,
                    onOpenRecipe = {}, onOpenShoppingList = {}, onOpenBudget = {}
                )
            }
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Undo Pasta").fetchSemanticsNodes().isNotEmpty()
        }

        rule.onAllNodesWithContentDescription(
            rule.activity.getString(R.string.remove_from_plan)
        )[0].performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.meal_removed)).assertExistsCompat()

        rule.onNodeWithText(rule.activity.getString(R.string.undo)).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Undo Pasta").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, db.mealPlanDao().getRawPlansBetween(today, today).size)
    }
}
