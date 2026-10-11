package com.example.mealplanner

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mealplanner.data.Budget
import com.example.mealplanner.data.DietaryProfile
import com.example.mealplanner.data.DietaryProfileDao
import com.example.mealplanner.data.Ingredient
import com.example.mealplanner.data.MealPlan
import com.example.mealplanner.data.MealPlanDao
import com.example.mealplanner.data.MealPlannerDatabase
import com.example.mealplanner.data.Purchase
import com.example.mealplanner.data.PurchaseDao
import com.example.mealplanner.data.Recipe
import com.example.mealplanner.data.RecipeDao
import com.example.mealplanner.data.RecipeWithIngredients
import com.example.mealplanner.data.ShoppingItem
import com.example.mealplanner.util.AllergenUtil
import com.example.mealplanner.util.BarcodeLookupResult
import com.example.mealplanner.util.OpenFoodFacts
import com.example.mealplanner.util.PdfExporter
import com.example.mealplanner.util.RecipeImageStore
import com.example.mealplanner.util.RecipeQr
import com.example.mealplanner.util.ShoppingMerger
import com.example.mealplanner.util.WaterPrefs
import com.example.mealplanner.util.WeekCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class MealPlannerViewModel(
    application: Application,
    database: MealPlannerDatabase? = null   // test seam: inject an in-memory DB
) : AndroidViewModel(application) {

    private val db = database ?: MealPlannerDatabase.getDatabase(application)
    private val recipeDao: RecipeDao = db.recipeDao()
    private val mealPlanDao: MealPlanDao = db.mealPlanDao()
    private val budgetDao: BudgetDao = db.budgetDao()
    private val profileDao: DietaryProfileDao = db.dietaryProfileDao()
    private val purchaseDao: PurchaseDao = db.purchaseDao()

    // ------------------------------------------------------------------
    // Recipes
    // ------------------------------------------------------------------

    val recipes: StateFlow<List<RecipeWithIngredients>> =
        recipeDao.getAllRecipes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedRecipe = MutableStateFlow<RecipeWithIngredients?>(null)
    val selectedRecipe: StateFlow<RecipeWithIngredients?> = _selectedRecipe.asStateFlow()

    fun loadRecipe(id: Int) {
        viewModelScope.launch {
            _selectedRecipe.value = null
            _selectedRecipe.value = recipeDao.getRecipeById(id)
        }
    }

    /**
     * Ingredients arrive with FINAL tags: the editor's chips (DB ∪ detection),
     * QR decode-time detection, or import-time detection. Never re-union here —
     * it would resurrect tags the user explicitly removed.
     */
    fun saveRecipe(recipe: Recipe, ingredients: List<Ingredient>, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            if (recipe.id == 0) {
                val newId = recipeDao.insertRecipe(recipe).toInt()
                recipeDao.insertIngredients(ingredients.map { it.copy(recipeId = newId) })
            } else {
                recipeDao.updateRecipe(recipe)
                recipeDao.deleteIngredientsForRecipe(recipe.id)
                recipeDao.insertIngredients(ingredients.map { it.copy(recipeId = recipe.id) })
            }
            onDone()
        }
    }

    fun deleteRecipe(recipe: Recipe, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            recipeDao.deleteRecipe(recipe)   // cascades to ingredients + meal plans
            withContext(Dispatchers.IO) { RecipeImageStore.delete(recipe.photoPath) }
            onDone()
        }
    }

    fun toggleFavorite(id: Int) {
        viewModelScope.launch {
            recipeDao.toggleFavorite(id)
            // keep the detail screen's heart in sync if it's showing this recipe
            if (_selectedRecipe.value?.recipe?.id == id) {
                _selectedRecipe.value = recipeDao.getRecipeById(id)
            }
        }
    }

    /** Clones recipe + ingredients (+ a physical photo copy), opens it in the editor. */
    fun duplicateRecipe(recipeId: Int, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val item = recipeDao.getRecipeById(recipeId) ?: return@launch
            val photoCopy = withContext(Dispatchers.IO) {
                RecipeImageStore.copyFile(item.recipe.photoPath)
            }
            val copy = item.recipe.copy(
                id = 0,
                name = item.recipe.name + " (copy)",
                isFavorite = false,
                photoPath = photoCopy
            )
            val newId = recipeDao.insertRecipe(copy).toInt()
            recipeDao.insertIngredients(item.ingredients.map { it.copy(id = 0, recipeId = newId) })
            onDone(newId)
        }
    }

    // ------------------------------------------------------------------
    // Meal planning
    // ------------------------------------------------------------------

    fun mealsForDate(date: String): Flow<List<MealPlan>> = mealPlanDaoFlow(date)
    private fun mealPlanDaoFlow(date: String) = mealPlanDao.getMealsForDate(date)

    fun addMeal(date: String, mealType: String, recipeId: Int, servings: Int) {
        viewModelScope.launch {
            val nextOrder = (mealPlanDao.maxSortOrder(date, mealType) ?: -1) + 1
            mealPlanDao.insert(
                MealPlan(date = date, mealType = mealType, recipeId = recipeId,
                    servings = servings, sortOrder = nextOrder)
            )
        }
    }

    /** Batch planning: one recipe across several days, each appended in section order. */
    fun addMeals(dates: List<String>, mealType: String, recipeId: Int, servings: Int) {
        viewModelScope.launch {
            dates.forEach { date ->
                val nextOrder = (mealPlanDao.maxSortOrder(date, mealType) ?: -1) + 1
                mealPlanDao.insert(
                    MealPlan(date = date, mealType = mealType, recipeId = recipeId,
                        servings = servings, sortOrder = nextOrder)
                )
            }
        }
    }

    fun removeMeal(mealPlan: MealPlan) {
        viewModelScope.launch { mealPlanDao.delete(mealPlan) }
    }

    /** Snackbar undo — re-inserts the exact row (id, order and cooked state intact). */
    fun restoreMeal(plan: MealPlan) {
        viewModelScope.launch { mealPlanDao.insert(plan) }
    }

    fun reorderMeals(idsInOrder: List<Int>) {
        viewModelScope.launch {
            mealPlanDao.updateSortOrders(idsInOrder.withIndex().associate { (i, id) -> id to i })
        }
    }

    fun toggleCooked(plan: MealPlan) {
        viewModelScope.launch { mealPlanDao.toggleCooked(plan.id, LocalDateTime.now().toString()) }
    }

    fun copyWeek(sourceStart: String, targetStart: String, replace: Boolean, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val iso = DateTimeFormatter.ISO_LOCAL_DATE
            val s = LocalDate.parse(sourceStart)
            val t = LocalDate.parse(targetStart)
            val source = mealPlanDao.getRawPlansBetween(sourceStart, s.plusDays(6).format(iso))
            val targetEnd = t.plusDays(6).format(iso)

            if (replace) mealPlanDao.deletePlansBetween(targetStart, targetEnd)
            val existingTarget = if (replace) emptyList()
            else mealPlanDao.getRawPlansBetween(targetStart, targetEnd)

            WeekCopy.buildCopies(source, ChronoUnit.WEEKS.between(s, t), existingTarget, replace)
                .forEach { mealPlanDao.insert(it) }
            onDone(source.size)
        }
    }

    // ------------------------------------------------------------------
    // Shopping list (merged across compatible units) + purchases
    // ------------------------------------------------------------------

    fun shoppingListFor(date: String): Flow<List<ShoppingItem>> =
        mealPlanDao.getShoppingListForDate(date).map { ShoppingMerger.merge(it) }

    fun shoppingListBetween(start: String, end: String): Flow<List<ShoppingItem>> =
        mealPlanDao.getShoppingListBetween(start, end).map { ShoppingMerger.merge(it) }

    fun purchasesForDate(date: String): Flow<List<Purchase>> =
        purchaseDao.getPurchasesForDate(date)

    fun saveActualPrice(date: String, name: String, unit: String,
                        quantity: Double, estimatedPrice: Double, actualPrice: Double) {
        viewModelScope.launch {
            val existing = purchaseDao.findFor(date, name, unit)
            if (existing == null) purchaseDao.insert(
                Purchase(date = date, name = name, unit = unit,
                    quantity = quantity, estimatedPrice = estimatedPrice, actualPrice = actualPrice)
            ) else purchaseDao.update(
                existing.copy(quantity = quantity, estimatedPrice = estimatedPrice,
                    actualPrice = actualPrice)
            )
        }
    }

    fun clearActualPrice(date: String, name: String, unit: String) {
        viewModelScope.launch { purchaseDao.deleteFor(date, name, unit) }
    }

    // ------------------------------------------------------------------
    // Budgets & costs
    // ------------------------------------------------------------------

    fun budgetForDate(date: String): Flow<Budget?> = budgetDao.getBudgetForDate(date)
    fun plannedCostForDate(date: String): Flow<Double?> = mealPlanDao.getPlannedCostForDate(date)
    fun plannedCostBetween(start: String, end: String): Flow<Double?> =
        mealPlanDao.getPlannedCostBetween(start, end)

    fun setBudget(date: String, amount: Double?) {
        viewModelScope.launch {
            if (amount == null || amount <= 0.0) budgetDao.clearBudget(date)
            else budgetDao.setBudget(Budget(date = date, amount = amount))
        }
    }

    data class MonthBudgetDay(
        val date: LocalDate,
        val budget: Double?,
        val planned: Double,
        val actual: Double?
    )

    fun monthOverview(start: String, end: String): Flow<List<MonthBudgetDay>> =
        combine(
            budgetDao.getBudgetsBetween(start, end),
            mealPlanDao.getPlannedCostPerDayBetween(start, end),
            purchaseDao.getActualSpendPerDayBetween(start, end)
        ) { budgets, planned, actual ->
            val b = budgets.associate { it.date to it.amount }
            val p = planned.associate { it.date to it.cost }
            val a = actual.associate { it.date to it.cost }
            (b.keys + p.keys + a.keys).sorted().map { d ->
                MonthBudgetDay(LocalDate.parse(d), b[d], p[d] ?: 0.0, a[d])
            }
        }

    // ------------------------------------------------------------------
    // Dietary profile
    // ------------------------------------------------------------------

    val profile: StateFlow<DietaryProfile> =
        profileDao.getProfile()
            .map { it ?: DietaryProfile() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DietaryProfile())

    fun toggleAllergy(tag: String) = updateProfile { copy(allergies = toggled(allergies, tag)) }
    fun toggleDiet(tag: String) = updateProfile { copy(diets = toggled(diets, tag)) }
    fun setHideClashing(hide: Boolean) = updateProfile { copy(hideClashing = hide) }

    fun importProfile(incoming: DietaryProfile) {
        viewModelScope.launch { profileDao.setProfile(incoming.copy(id = 1)) }
    }

    private fun updateProfile(transform: DietaryProfile.() -> DietaryProfile) {
        viewModelScope.launch { profileDao.setProfile(profile.value.transform()) }
    }

    private fun toggled(list: List<String>, tag: String): List<String> =
        if (tag in list) list - tag else list + tag

    // ------------------------------------------------------------------
    // Barcode lookup
    // ------------------------------------------------------------------

    fun lookupBarcode(code: String, onResult: (BarcodeLookupResult) -> Unit) {
        viewModelScope.launch {
            onResult(withContext(Dispatchers.IO) { OpenFoodFacts.lookup(code) })
        }
    }

    // ------------------------------------------------------------------
    // Insights
    // ------------------------------------------------------------------

    fun topPlannedRecipes(limit: Int = 5): Flow<List<com.example.mealplanner.data.NameCount>> =
        mealPlanDao.getTopPlanned(limit)

    fun mealCountsBetween(start: String, end: String): Flow<List<com.example.mealplanner.data.DateCount>> =
        mealPlanDao.getMealCountsBetween(start, end)

    fun plannedCostPerDayBetween(start: String, end: String): Flow<List<com.example.mealplanner.data.DayCost>> =
        mealPlanDao.getPlannedCostPerDayBetween(start, end)

    fun actualSpendPerDayBetween(start: String, end: String): Flow<List<com.example.mealplanner.data.DayCost>> =
        purchaseDao.getActualSpendPerDayBetween(start, end)

    // ------------------------------------------------------------------
    // Exports
    // ------------------------------------------------------------------

    fun exportData(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val recipes = recipeDao.getAllRecipesOnce()
                val plans = mealPlanDao.getAllPlansOnce()
                val nameById = recipes.associate { it.recipe.id to it.recipe.name }
                val prof = profileDao.getProfileOnce() ?: DietaryProfile()

                val root = JSONObject()
                    .put("app", "meal-planner")
                    .put("exportedAt", LocalDateTime.now().toString())

                root.put("recipes", JSONArray().apply {
                    recipes.forEach { r ->
                        put(JSONObject().apply {
                            put("name", r.recipe.name)
                            put("category", r.recipe.category)
                            put("prepTimeMinutes", r.recipe.prepTimeMinutes)
                            put("cookTimeMinutes", r.recipe.cookTimeMinutes)
                            put("servings", r.recipe.servings)
                            put("instructions", r.recipe.instructions)
                            put("diets", JSONArray(r.recipe.diets))
                            put("adaptedNote", r.recipe.adaptedNote)
                            put("isFavorite", r.recipe.isFavorite)
                            put("tags", JSONArray(r.recipe.tags))
                            put("ingredients", JSONArray().apply {
                                r.ingredients.forEach { i ->
                                    put(JSONObject()
                                        .put("name", i.name)
                                        .put("quantity", i.quantity)
                                        .put("unit", i.unit)
                                        .put("price", i.price)
                                        .put("allergens", JSONArray(i.allergens)))
                                }
                            })
                        })
                    }
                })

                root.put("mealPlans", JSONArray().apply {
                    plans.forEach { p ->
                        put(JSONObject()
                            .put("date", p.mealPlan.date)
                            .put("mealType", p.mealPlan.mealType)
                            .put("servings", p.mealPlan.servings)
                            .put("sortOrder", p.mealPlan.sortOrder)
                            .put("cookedAt", p.mealPlan.cookedAt)
                            .put("recipe", nameById[p.mealPlan.recipeId] ?: ""))
                    }
                })

                root.put("budgets", JSONArray().apply {
                    budgetDao.getAllBudgetsOnce().forEach { b ->
                        put(JSONObject().put("date", b.date).put("amount", b.amount))
                    }
                })

                // archived for completeness; the current importer doesn't restore these
                root.put("purchases", JSONArray().apply {
                    purchaseDao.getAllOnce().forEach { pu ->
                        put(JSONObject()
                            .put("date", pu.date).put("name", pu.name).put("unit", pu.unit)
                            .put("quantity", pu.quantity).put("estimatedPrice", pu.estimatedPrice)
                            .put("actualPrice", pu.actualPrice))
                    }
                })

                root.put("profile", JSONObject()
                    .put("allergies", JSONArray(prof.allergies))
                    .put("diets", JSONArray(prof.diets))
                    .put("hideClashing", prof.hideClashing))

                val water = WaterPrefs.all(getApplication())
                if (water.isNotEmpty()) {
                    root.put("water", JSONObject().apply { water.forEach { (d, g) -> put(d, g) } })
                }

                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openOutputStream(uri)?.use { it.write(root.toString(2).toByteArray()) }
                        ?: error("Could not open output stream")
                }
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun exportMealPlanPdf(uri: Uri, days: List<LocalDate>, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val mealsByDate = days.associate { day ->
                    val key = day.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    key to mealPlanDao.getMealsForDateOnce(key)
                }
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        PdfExporter.writeMealPlanPdf(getApplication(), days, mealsByDate, it)
                    } ?: error("Could not open output stream")
                }
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun exportShoppingListPdf(uri: Uri, start: String, end: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val items = ShoppingMerger.merge(mealPlanDao.getShoppingListBetweenOnce(start, end))
                val budget = if (start == end) budgetDao.getBudgetForDateOnce(start)?.amount else null
                val allergies = (profileDao.getProfileOnce() ?: DietaryProfile()).allergies.toSet()
                val label = if (start == end) {
                    LocalDate.parse(start)
                        .format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
                } else {
                    val f = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
                    LocalDate.parse(start).format(f) + " – " + LocalDate.parse(end).format(f)
                }
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        PdfExporter.writeShoppingListPdf(
                            getApplication(), label, items, budget, allergies, it)
                    } ?: error("Could not open output stream")
                }
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun exportRecipePdf(uri: Uri, recipeId: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val item = recipeDao.getRecipeById(recipeId) ?: error("Recipe not found")
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        PdfExporter.writeRecipePdf(getApplication(), item, it)
                    } ?: error("Could not open output stream")
                }
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun exportCookbookPdf(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val recipes = recipeDao.getAllRecipesOnce()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        PdfExporter.writeCookbookPdf(getApplication(), recipes, it)
                    } ?: error("Could not open output stream")
                }
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    // ------------------------------------------------------------------
    // Import
    // ------------------------------------------------------------------

    fun parseBackup(uri: Uri, onParsed: (JsonBackupResult?) -> Unit) {
        viewModelScope.launch {
            onParsed(try {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not open file")
                }
                JsonBackupResult.Success(com.example.mealplanner.util.JsonBackup.parse(text))
            } catch (e: Exception) {
                JsonBackupResult.Failure
            })
        }
    }

    fun applyImport(
        data: com.example.mealplanner.util.JsonBackup.Parsed,
        importProfile: Boolean,
        onDone: (com.example.mealplanner.util.JsonBackup.Counts) -> Unit
    ) {
        viewModelScope.launch {
            val idByName = mutableMapOf<String, Int>()
            recipeDao.getAllRecipesOnce().forEach { idByName[it.recipe.name.lowercase()] = it.recipe.id }

            var added = 0
            var updated = 0
            data.recipes.forEach { item ->
                val key = item.recipe.name.lowercase()
                val detected = item.ingredients.map {
                    it.copy(allergens = (it.allergens + AllergenUtil.detectAllergens(it.name)).distinct())
                }
                val existingId = idByName[key]
                if (existingId != null) {
                    recipeDao.updateRecipe(item.recipe.copy(id = existingId))
                    recipeDao.deleteIngredientsForRecipe(existingId)
                    recipeDao.insertIngredients(detected.map { it.copy(recipeId = existingId) })
                    updated++
                } else {
                    val newId = recipeDao.insertRecipe(item.recipe).toInt()
                    recipeDao.insertIngredients(detected.map { it.copy(recipeId = newId) })
                    idByName[key] = newId
                    added++
                }
            }

            var plans = 0
            data.plans.forEach { p ->
                idByName[p.recipeName.lowercase()]?.let { id ->
                    mealPlanDao.insert(
                        MealPlan(date = p.date, mealType = p.mealType, recipeId = id,
                            servings = p.servings, sortOrder = p.sortOrder, cookedAt = p.cookedAt)
                    )
                    plans++
                }
            }

            data.budgets.forEach { budgetDao.setBudget(it) }

            var profileDone = false
            if (importProfile) {
                data.profile?.let {
                    profileDao.setProfile(it.copy(id = 1))
                    profileDone = true
                }
            }

            data.water.forEach { (d, g) -> WaterPrefs.set(getApplication(), d, g) }

            onDone(com.example.mealplanner.util.JsonBackup.Counts(
                added, updated, plans, data.budgets.size, profileDone))
        }
    }
}

/** Wrapper so the UI can distinguish "invalid file" from a real parse result. */
sealed class JsonBackupResult {
    data class Success(val data: com.example.mealplanner.util.JsonBackup.Parsed) : JsonBackupResult()
    object Failure : JsonBackupResult()
}
