package com.example.mealplanner

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.mealplanner.data.Budget
import com.example.mealplanner.data.DietaryProfile
import com.example.mealplanner.data.Ingredient
import com.example.mealplanner.data.MealPlan
import com.example.mealplanner.data.MealPlannerDatabase
import com.example.mealplanner.data.Purchase
import com.example.mealplanner.data.Recipe
import com.example.mealplanner.data.RecipeWithIngredients
import com.example.mealplanner.util.JsonBackup
import com.example.mealplanner.util.RecipeImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MealPlannerViewModelTest {

    private lateinit var db: MealPlannerDatabase
    private lateinit var vm: MealPlannerViewModel
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, MealPlannerDatabase::class.java)
            .allowMainThreadQueries().build()
        vm = MealPlannerViewModel(app, db)
    }

    @After
    fun tearDown() { db.close(); Dispatchers.resetMain() }

    private fun runCurrent() = dispatcher.scheduler.advanceUntilIdle()

    private fun ingredient(name: String, allergens: List<String> = emptyList(), price: Double = 0.0) =
        Ingredient(name = name, quantity = 1.0, price = price, allergens = allergens)

    private suspend fun insertRecipe(name: String, servings: Int = 4): Int =
        db.recipeDao().insertRecipe(Recipe(name = name, servings = servings)).toInt()

    // ---- saveRecipe: chips-authoritative semantics ----

    @Test
    fun `saveRecipe keeps provided tags without re-detecting`() = runTest {
        vm.saveRecipe(Recipe(name = "Mystery"), listOf(ingredient("Zzz", listOf("Nuts")))) {}
        runCurrent()
        val saved = db.recipeDao().getAllRecipesOnce().single().ingredients.single()
        assertEquals(listOf("Nuts"), saved.allergens)      // "Zzz" matches no keyword — tag must survive
    }

    @Test
    fun `editor seeds detection into chips so plain saves still tag`() = runTest {
        vm.saveRecipe(Recipe(name = "A"), listOf(ingredient("Milk", listOf("Dairy")))) {}
        runCurrent()
        assertEquals(listOf("Dairy"),
            db.recipeDao().getAllRecipesOnce().single().ingredients.single().allergens)
    }

    @Test
    fun `updating a recipe replaces its ingredients wholesale`() = runTest {
        vm.saveRecipe(Recipe(name = "A"), listOf(ingredient("Butter"))) {}
        runCurrent()
        val id = db.recipeDao().getAllRecipesOnce().single().recipe.id
        vm.saveRecipe(Recipe(name = "A", id = id), listOf(ingredient("Oil"))) {}
        runCurrent()
        val loaded = db.recipeDao().getRecipeById(id)!!
        assertEquals(listOf("Oil"), loaded.ingredients.map { it.name })
    }

    // ---- favorites ----

    @Test
    fun `toggleFavorite flips the DB and re-syncs the loaded detail screen`() = runTest {
        vm.saveRecipe(Recipe(name = "A"), emptyList()) {}
        runCurrent()
        val id = db.recipeDao().getAllRecipesOnce().single().recipe.id
        vm.loadRecipe(id); runCurrent()

        vm.toggleFavorite(id); runCurrent()
        assertTrue(vm.selectedRecipe.value!!.recipe.isFavorite)
        vm.toggleFavorite(id); runCurrent()
        assertFalse(vm.selectedRecipe.value!!.recipe.isFavorite)
    }

    // ---- planning ----

    @Test
    fun `addMeals batch appends after existing meals per date`() = runTest {
        val id = insertRecipe("A")
        db.mealPlanDao().insert(MealPlan(date = "2024-06-03", mealType = "Dinner",
            recipeId = id, sortOrder = 5))
        runCurrent()

        vm.addMeals(listOf("2024-06-03", "2024-06-04"), "Dinner", id, 2)
        runCurrent()

        val plans = db.mealPlanDao().getRawPlansBetween("2024-06-01", "2024-06-30")
        assertEquals(3, plans.size)
        assertEquals(6, plans.first { it.date == "2024-06-03" }.sortOrder)
        assertEquals(0, plans.first { it.date == "2024-06-04" }.sortOrder)
    }

    @Test
    fun `toggleCooked stamps and clears`() = runTest {
        val id = insertRecipe("A")
        db.mealPlanDao().insert(MealPlan(date = "2024-06-03", mealType = "Dinner",
            recipeId = id, sortOrder = 0))
        val plan = db.mealPlanDao().getRawPlansBetween("2024-06-03", "2024-06-03").single()

        vm.toggleCooked(plan); runCurrent()
        assertTrue(db.mealPlanDao().getRawPlansBetween("2024-06-03", "2024-06-03")
            .single().cookedAt.isNotEmpty())
        vm.toggleCooked(plan); runCurrent()
        assertEquals("", db.mealPlanDao().getRawPlansBetween("2024-06-03", "2024-06-03")
            .single().cookedAt)
    }

    @Test
    fun `copyWeek clears cooked state and appends in non-replace mode`() = runTest {
        val id = insertRecipe("A")
        db.mealPlanDao().insert(MealPlan(date = "2024-06-03", mealType = "Dinner",
            recipeId = id, sortOrder = 0, cookedAt = "2024-06-03T18:00"))
        db.mealPlanDao().insert(MealPlan(date = "2024-06-10", mealType = "Dinner",
            recipeId = id, sortOrder = 0))
        runCurrent()

        vm.copyWeek("2024-06-03", "2024-06-10", false) {}
        runCurrent()

        val target = db.mealPlanDao().getRawPlansBetween("2024-06-10", "2024-06-16")
        assertEquals(2, target.size)
        val copy = target.first { it.sortOrder == 1 }
        assertEquals("", copy.cookedAt)                    // a copy is a plan, not history
        assertTrue(target.any { it.sortOrder == 0 })       // appended, existing kept
    }

    // ---- duplicate ----

    @Test
    fun `duplicateRecipe clones photo and ingredients resets favorite and isolates files`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dir = File(context.filesDir, "recipe_images").apply { mkdirs() }
        val src = File(dir, "src.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        vm.saveRecipe(Recipe(name = "Original", isFavorite = true, photoPath = src.absolutePath),
            listOf(ingredient("Flour"))) {}
        runCurrent()
        val original = db.recipeDao().getAllRecipesOnce().single()

        var copyId = -1
        vm.duplicateRecipe(original.recipe.id) { copyId = it }
        runCurrent()

        val copy = db.recipeDao().getRecipeById(copyId)!!
        assertEquals("Original (copy)", copy.recipe.name)
        assertFalse(copy.recipe.isFavorite)
        assertEquals(listOf("Flour"), copy.ingredients.map { it.name })
        assertTrue(copy.recipe.photoPath != original.recipe.photoPath)
        assertTrue(File(copy.recipe.photoPath!!).exists())

        vm.deleteRecipe(copy.recipe) {}                    // deleting the copy…
        runCurrent()
        assertTrue(src.exists())                           // …never touches the original's file
        assertFalse(File(copy.recipe.photoPath!!).exists())
        src.delete()
    }

    // ---- import ----

    @Test
    fun `applyImport counts new vs updated resolves names case-insensitively and unions detection`() = runTest {
        vm.saveRecipe(Recipe(name = "Pasta"), emptyList()) {}
        runCurrent()
        val pastaId = db.recipeDao().getAllRecipesOnce().single().recipe.id

        val parsed = JsonBackup.Parsed(
            recipes = listOf(
                RecipeWithIngredients(Recipe(name = "pasta"), listOf(ingredient("Milk"))),   // update
                RecipeWithIngredients(Recipe(name = "Soup"), emptyList())                    // new
            ),
            plans = listOf(JsonBackup.ParsedPlan(
                date = "2024-06-05", mealType = "Dinner", servings = 2, sortOrder = 0,
                recipeName = "PASTA")),
            budgets = listOf(Budget("2024-06-05", 10.0)),
            profile = DietaryProfile(allergies = listOf("Nuts"))
        )

        var counts: JsonBackup.Counts? = null
        vm.applyImport(parsed, importProfile = false) { counts = it }
        runCurrent()

        assertEquals(1, counts!!.recipesAdded)
        assertEquals(1, counts!!.recipesUpdated)
        assertEquals(1, counts!!.plansImported)

        val updated = db.recipeDao().getRecipeById(pastaId)!!
        assertEquals(listOf("Dairy"), updated.ingredients.single().allergens)  // detection unioned on import
        val plan = db.mealPlanDao().getRawPlansBetween("2024-06-05", "2024-06-05").single()
        assertEquals(pastaId, plan.recipeId)

        assertNull(db.dietaryProfileDao().getProfileOnce())   // opt-in was false
        assertEquals(10.0, db.budgetDao().getBudgetForDateOnce("2024-06-05")!!.amount, 1e-9)
    }

    @Test
    fun `applyImport replaces the profile only when opted in`() = runTest {
        val parsed = JsonBackup.Parsed(
            recipes = emptyList(), plans = emptyList(), budgets = emptyList(),
            profile = DietaryProfile(allergies = listOf("Fish"), diets = listOf("Vegan"))
        )
        vm.applyImport(parsed, importProfile = true) {}
        runCurrent()
        val stored = db.dietaryProfileDao().getProfileOnce()!!
        assertEquals(listOf("Fish"), stored.allergies)
        assertEquals(listOf("Vegan"), stored.diets)
    }

    // consolidation fix: parseBackup returns the JsonBackupResult wrapper
    @Test
    fun `parseBackup rejects invalid files and accepts valid ones`() = runTest {
        val dir = ApplicationProvider.getApplicationContext<Application>().cacheDir
        val bad = File(dir, "bad.json").apply { writeText("""{"app":"other"}""") }
        val good = File(dir, "good.json").apply {
            writeText("""{"app":"meal-planner","recipes":[{"name":"X"}]}""")
        }

        var badResult: JsonBackupResult? = null
        var goodResult: JsonBackupResult? = null
        vm.parseBackup(android.net.Uri.fromFile(bad)) { badResult = it }
        runCurrent()
        vm.parseBackup(android.net.Uri.fromFile(good)) { goodResult = it }
        runCurrent()

        assertTrue(badResult is JsonBackupResult.Failure)
        val success = goodResult as JsonBackupResult.Success
        assertEquals("X", success.data.recipes.single().recipe.name)
        bad.delete(); good.delete()
    }

    // ---- budgets & month overview ----

    @Test
    fun `monthOverview merges budget planned and actual into sorted days`() = runTest {
        val id = insertRecipe("Soup", servings = 2)
        db.recipeDao().insertIngredients(listOf(ingredient("Carrot", price = 6.0)))
        db.mealPlanDao().insert(MealPlan(date = "2024-06-03", mealType = "Lunch",
            recipeId = id, servings = 2, sortOrder = 0))
        db.budgetDao().setBudget(Budget("2024-06-03", 50.0))
        db.purchaseDao().insert(Purchase(date = "2024-06-04", name = "Bread", unit = "pc",
            quantity = 1.0, estimatedPrice = 2.0, actualPrice = 2.5))
        runCurrent()

        val days = vm.monthOverview("2024-06-01", "2024-06-30").first()

        assertEquals(2, days.size)                          // sorted by date
        assertEquals(java.time.LocalDate.parse("2024-06-03"), days[0].date)
        assertEquals(50.0, days[0].budget!!, 1e-9)
        assertEquals(6.0, days[0].planned, 1e-9)
        assertNull(days[0].actual)
        assertEquals(java.time.LocalDate.parse("2024-06-04"), days[1].date)
        assertEquals(0.0, days[1].planned, 1e-9)
        assertEquals(2.5, days[1].actual!!, 1e-9)
    }

    @Test
    fun `setBudget upserts clears and supports month keys`() = runTest {
        vm.setBudget("2024-06-03", 40.0); runCurrent()
        assertEquals(40.0, db.budgetDao().getBudgetForDateOnce("2024-06-03")!!.amount, 1e-9)
        vm.setBudget("2024-06-03", 55.0); runCurrent()      // overwrite
        assertEquals(55.0, db.budgetDao().getBudgetForDateOnce("2024-06-03")!!.amount, 1e-9)
        vm.setBudget("2024-06-03", null); runCurrent()      // clear
        assertNull(db.budgetDao().getBudgetForDateOnce("2024-06-03"))

        vm.setBudget("2024-06", 300.0); runCurrent()        // monthly key rides the same table
        assertEquals(300.0, db.budgetDao().getBudgetForDateOnce("2024-06")!!.amount, 1e-9)
    }

    // ---- purchases ----

    @Test
    fun `saveActualPrice upserts and clearActualPrice removes`() = runTest {
        vm.saveActualPrice("2024-06-01", "Milk", "l", 2.0, 3.0, 2.8); runCurrent()
        var p = db.purchaseDao().findFor("2024-06-01", "Milk", "l")!!
        assertEquals(2.8, p.actualPrice, 1e-9)
        vm.saveActualPrice("2024-06-01", "Milk", "l", 2.0, 3.0, 3.1); runCurrent()
        p = db.purchaseDao().findFor("2024-06-01", "Milk", "l")!!
        assertEquals(3.1, p.actualPrice, 1e-9)              // updated, not duplicated
        assertEquals(1, db.purchaseDao().getAllOnce().size)
        vm.clearActualPrice("2024-06-01", "Milk", "l"); runCurrent()
        assertNull(db.purchaseDao().findFor("2024-06-01", "Milk", "l"))
    }
}
