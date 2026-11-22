package com.example.mealplanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MealPlannerDatabaseTest {

    private lateinit var db: MealPlannerDatabase

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MealPlannerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() = db.close()

    private fun recipe(name: String = "Pancakes", servings: Int = 4) =
        Recipe(name = name, servings = servings, category = "Breakfast")

    @Test
    fun cascadeDeleteRemovesIngredientsAndPlans() = runTest {
        val id = db.recipeDao().insertRecipe(recipe()).toInt()
        db.recipeDao().insertIngredients(listOf(
            Ingredient(recipeId = id, name = "Milk", quantity = 2.0, unit = "cups",
                allergens = listOf("Dairy"))))
        db.mealPlanDao().insert(MealPlan(date = "2024-06-01", mealType = "Breakfast",
            recipeId = id, servings = 4, sortOrder = 0))

        db.recipeDao().deleteRecipe(recipe().copy(id = id))

        assertEquals(0, db.recipeDao().getAllRecipesOnce().size)
        assertEquals(0, db.mealPlanDao().getRawPlansBetween("2024-06-01", "2024-06-01").size)
    }

    @Test
    fun shoppingListScalesByPlannedServingsIncludingDefault() = runTest {
        val id = db.recipeDao().insertRecipe(recipe(servings = 4)).toInt()
        db.recipeDao().insertIngredients(listOf(
            Ingredient(recipeId = id, name = "Flour", quantity = 2.0, unit = "cups",
                price = 8.0, allergens = listOf("Gluten"))))
        val planDao = db.mealPlanDao()

        planDao.insert(MealPlan(date = "2024-06-01", mealType = "Dinner",
            recipeId = id, servings = 2, sortOrder = 0))
        var row = planDao.getShoppingListForDate("2024-06-01").first().single()
        assertEquals(1.0, row.totalQuantity, 1e-9)      // 2 cups × 2/4
        assertEquals(4.0, row.totalPrice, 1e-9)         // price scales identically

        planDao.delete(planDao.getRawPlansBetween("2024-06-01", "2024-06-01").single())
        planDao.insert(MealPlan(date = "2024-06-01", mealType = "Dinner",
            recipeId = id, servings = 0, sortOrder = 0))
        row = planDao.getShoppingListForDate("2024-06-01").first().single()
        assertEquals(2.0, row.totalQuantity, 1e-9)      // servings=0 → recipe default
        assertEquals(8.0, row.totalPrice, 1e-9)
    }

    @Test
    fun mealSectionsOrderBySortOrderAndCookedToggles() = runTest {
        val a = db.recipeDao().insertRecipe(recipe("A")).toInt()
        val b = db.recipeDao().insertRecipe(recipe("B")).toInt()
        val planDao = db.mealPlanDao()
        planDao.insert(MealPlan(date = "2024-06-01", mealType = "Dinner",
            recipeId = a, sortOrder = 99))
        planDao.insert(MealPlan(date = "2024-06-01", mealType = "Dinner",
            recipeId = b, sortOrder = 1))

        val ordered = planDao.getMealsForDate("2024-06-01").first()
        assertEquals(b, ordered[0].mealPlan.recipeId)
        assertEquals(a, ordered[1].mealPlan.recipeId)

        val planA = planDao.getRawPlansBetween("2024-06-01", "2024-06-01")
            .first { it.recipeId == a }
        planDao.toggleCooked(planA.id, "2024-06-01T18:00")
        assertEquals("2024-06-01T18:00",
            planDao.getRawPlansBetween("2024-06-01", "2024-06-01")
                .first { it.recipeId == a }.cookedAt)
        planDao.toggleCooked(planA.id, "2024-06-01T18:30")
        assertEquals("",
            planDao.getRawPlansBetween("2024-06-01", "2024-06-01")
                .first { it.recipeId == a }.cookedAt)
    }

    @Test
    fun shoppingListConcatenatesAllergensForUiToDedupe() = runTest {
        val id1 = db.recipeDao().insertRecipe(Recipe(name = "A", servings = 2)).toInt()
        val id2 = db.recipeDao().insertRecipe(Recipe(name = "B", servings = 2)).toInt()
        db.recipeDao().insertIngredients(listOf(
            Ingredient(recipeId = id1, name = "Mayonnaise", quantity = 1.0, unit = "tbsp",
                allergens = listOf("Eggs")),
            Ingredient(recipeId = id2, name = "Mayonnaise", quantity = 1.0, unit = "tbsp",
                allergens = listOf("Eggs", "Soy"))))
        db.mealPlanDao().insert(MealPlan(date = "2024-06-01", mealType = "Lunch",
            recipeId = id1, sortOrder = 0))
        db.mealPlanDao().insert(MealPlan(date = "2024-06-01", mealType = "Dinner",
            recipeId = id2, sortOrder = 0))

        val row = db.mealPlanDao().getShoppingListForDate("2024-06-01").first().single()
        // GROUP_CONCAT works on concatenated strings — dedupe is the UI's job
        assertEquals(setOf("Eggs", "Soy"), row.allergens.split(',').toSet())
    }

    @Test
    fun budgetsUpsertClearAndPurchasesRoundTrip() = runTest {
        val budgetDao = db.budgetDao()
        budgetDao.setBudget(Budget("2024-06-01", 50.0))
        budgetDao.setBudget(Budget("2024-06-01", 60.0))                 // upsert overwrites
        assertEquals(60.0, budgetDao.getBudgetForDateOnce("2024-06-01")!!.amount, 1e-9)
        budgetDao.clearBudget("2024-06-01")
        assertNull(budgetDao.getBudgetForDateOnce("2024-06-01"))

        val purchaseDao = db.purchaseDao()
        purchaseDao.insert(Purchase(date = "2024-06-01", name = "Milk", unit = "l",
            quantity = 2.0, estimatedPrice = 3.0, actualPrice = 2.8))
        purchaseDao.update(purchaseDao.findFor("2024-06-01", "Milk", "l")!!
            .copy(actualPrice = 2.9))
        purchaseDao.insert(Purchase(date = "2024-06-02", name = "Bread", unit = "pc",
            quantity = 1.0, estimatedPrice = 2.0, actualPrice = 2.5))

        val perDay = purchaseDao.getActualSpendPerDayBetween("2024-06-01", "2024-06-30").first()
        assertEquals(2, perDay.size)
        assertEquals(2.9, perDay.first { it.date == "2024-06-01" }.cost, 1e-9)

        purchaseDao.deleteFor("2024-06-01", "Milk", "l")
        assertEquals(1, purchaseDao.getAllOnce().size)
    }

    @Test
    fun listConvertersRoundTrip() = runTest {
        val id = db.recipeDao().insertRecipe(
            Recipe(name = "Tagged", tags = listOf("One-pot", "Quick"),
                diets = listOf("Vegan"))).toInt()
        val loaded = db.recipeDao().getRecipeById(id)!!
        assertEquals(listOf("One-pot", "Quick"), loaded.recipe.tags)
        assertEquals(listOf("Vegan"), loaded.recipe.diets)
    }
}
