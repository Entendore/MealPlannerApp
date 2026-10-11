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
class MealPlanQueriesTest {

    private lateinit var db: MealPlannerDatabase

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MealPlannerDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    private suspend fun recipeWithIngredient(name: String, ingredientName: String,
                                             qty: Double, price: Double): Int {
        val id = db.recipeDao().insertRecipe(Recipe(name = name, servings = 4)).toInt()
        db.recipeDao().insertIngredients(listOf(
            Ingredient(recipeId = id, name = ingredientName, quantity = qty,
                unit = "cups", price = price)))
        return id
    }

    @Test fun topPlannedOrdersByCountAndRespectsLimit() = runTest {
        val a = db.recipeDao().insertRecipe(Recipe(name = "A")).toInt()
        val b = db.recipeDao().insertRecipe(Recipe(name = "B")).toInt()
        val dao = db.mealPlanDao()
        listOf("2024-06-03", "2024-06-04", "2024-06-05").forEach {
            dao.insert(MealPlan(date = it, mealType = "Dinner", recipeId = a, sortOrder = 0))
        }
        dao.insert(MealPlan(date = "2024-06-06", mealType = "Dinner", recipeId = b, sortOrder = 0))

        val top = dao.getTopPlanned(5).first()
        assertEquals("A", top[0].name); assertEquals(3, top[0].count)
        assertEquals("B", top[1].name); assertEquals(1, top[1].count)
        assertEquals(1, dao.getTopPlanned(1).first().size)
    }

    @Test fun mealCountsGroupPerDateAndRespectRange() = runTest {
        val a = db.recipeDao().insertRecipe(Recipe(name = "A")).toInt()
        val dao = db.mealPlanDao()
        dao.insert(MealPlan(date = "2024-06-03", mealType = "Dinner", recipeId = a, sortOrder = 0))
        dao.insert(MealPlan(date = "2024-06-03", mealType = "Lunch", recipeId = a, sortOrder = 1))
        dao.insert(MealPlan(date = "2024-06-10", mealType = "Dinner", recipeId = a, sortOrder = 0))

        val counts = dao.getMealCountsBetween("2024-06-01", "2024-06-07").first()
        assertEquals(1, counts.size)
        assertEquals(2, counts.single().count)              // 06-10 outside the range
    }

    @Test fun plannedCostPerDayScalesAndGroups() = runTest {
        val id = recipeWithIngredient("Soup", "Carrot", 2.0, 8.0)
        val dao = db.mealPlanDao()
        dao.insert(MealPlan(date = "2024-06-03", mealType = "Lunch",
            recipeId = id, servings = 2, sortOrder = 0))
        dao.insert(MealPlan(date = "2024-06-03", mealType = "Dinner",
            recipeId = id, servings = 0, sortOrder = 1))

        val day = dao.getPlannedCostPerDayBetween("2024-06-01", "2024-06-30").first().single()
        assertEquals(12.0, day.cost, 1e-9)                  // 4 (2×½) + 8 (default servings)
    }

    @Test fun plannedCostBetweenSumsTheWholeRange() = runTest {
        val id = recipeWithIngredient("Soup", "Carrot", 1.0, 5.0)
        val dao = db.mealPlanDao()
        dao.insert(MealPlan(date = "2024-06-03", mealType = "Lunch",
            recipeId = id, sortOrder = 0))
        dao.insert(MealPlan(date = "2024-06-05", mealType = "Dinner",
            recipeId = id, servings = 2, sortOrder = 1))

        assertEquals(7.5, dao.getPlannedCostBetween("2024-06-01", "2024-06-30")
            .first()!!, 1e-9)                               // 5 + 2.5
    }

    @Test fun weekShoppingListMergesSameUnitAcrossDays() = runTest {
        val id = recipeWithIngredient("A", "Milk", 1.0, 1.0)
        val dao = db.mealPlanDao()
        dao.insert(MealPlan(date = "2024-06-03", mealType = "Dinner",
            recipeId = id, sortOrder = 0))
        dao.insert(MealPlan(date = "2024-06-05", mealType = "Dinner",
            recipeId = id, sortOrder = 0))

        val rows = dao.getShoppingListBetween("2024-06-03", "2024-06-09").first()
        assertEquals(1, rows.size)                          // same name+unit → one SQL row
        assertEquals(2.0, rows.single().totalQuantity, 1e-9)
    }

    @Test fun monthBudgetKeysAreExcludedFromDayRanges() = runTest {
        val budgetDao = db.budgetDao()
        budgetDao.setBudget(Budget("2024-06", 300.0))       // monthly key
        budgetDao.setBudget(Budget("2024-06-03", 40.0))     // daily key

        // "2024-06" sorts before "2024-06-01" — BETWEEN on full dates excludes it
        val inRange = budgetDao.getBudgetsBetween("2024-06-01", "2024-06-30").first()
        assertEquals(listOf("2024-06-03"), inRange.map { it.date })
        assertEquals(40.0, budgetDao.getBudgetForDateOnce("2024-06-03")!!.amount, 1e-9)
        assertEquals(300.0, budgetDao.getBudgetForDateOnce("2024-06")!!.amount, 1e-9)
    }

    @Test fun mealPlanRelationLoadsIngredientsForClashDetection() = runTest {
        val id = recipeWithIngredient("A", "Mayonnaise", 1.0, 0.5)
        db.recipeDao().insertIngredients(listOf(
            Ingredient(recipeId = id, name = "Mayonnaise", quantity = 1.0,
                allergens = listOf("Eggs"))))
        db.mealPlanDao().insert(MealPlan(date = "2024-06-03", mealType = "Lunch",
            recipeId = id, sortOrder = 0))

        val entry = db.mealPlanDao().getMealsForDate("2024-06-03").first().single()
        assertEquals(listOf("Eggs"), entry.ingredients.single().allergens)
    }

    @Test fun profileDaoUpsertsAndFlows() = runTest {
        val dao = db.dietaryProfileDao()
        assertNull(dao.getProfileOnce())
        dao.setProfile(DietaryProfile(allergies = listOf("Dairy")))
        assertEquals(listOf("Dairy"), dao.getProfileOnce()!!.allergies)
        dao.setProfile(DietaryProfile(allergies = listOf("Fish"), diets = listOf("Vegan")))
        assertEquals(listOf("Fish"), dao.getProfileOnce()!!.allergies)
        assertEquals(listOf("Vegan"), dao.getProfileOnce()!!.diets)
    }

    @Test fun toggleFavoriteFlipsBothWaysInSql() = runTest {
        val id = db.recipeDao().insertRecipe(Recipe(name = "A")).toInt()
        db.recipeDao().toggleFavorite(id)
        assertTrue(db.recipeDao().getRecipeById(id)!!.recipe.isFavorite)
        db.recipeDao().toggleFavorite(id)
        assertTrue(!db.recipeDao().getRecipeById(id)!!.recipe.isFavorite)
    }

    @Test fun converterCommaInvariantIsPinned() = runTest {
        // Raw SQL with an embedded comma — reading back splits on it. This is why
        // the editor strips commas from user tags (see the tags FormSection).
        db.openHelper.writableDatabase.execSQL(
            """INSERT INTO recipes (name, servings, tags) VALUES ('Raw', 1, 'X,Y')"""
        )
        assertEquals(listOf("X", "Y"),
            db.recipeDao().getAllRecipesOnce().single().recipe.tags)
    }
}
