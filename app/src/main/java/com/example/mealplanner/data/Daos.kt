package com.example.mealplanner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY isFavorite DESC, name COLLATE NOCASE")
    fun getAllRecipes(): Flow<List<RecipeWithIngredients>>

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE")
    suspend fun getAllRecipesOnce(): List<RecipeWithIngredients>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getRecipeById(id: Int): RecipeWithIngredients?

    @Insert
    suspend fun insertRecipe(recipe: Recipe): Long

    @Update
    suspend fun updateRecipe(recipe: Recipe)

    @Delete
    suspend fun deleteRecipe(recipe: Recipe)

    @Insert
    suspend fun insertIngredients(ingredients: List<Ingredient>)

    @Query("DELETE FROM ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredientsForRecipe(recipeId: Int)

    @Query("UPDATE recipes SET isFavorite = CASE isFavorite WHEN 1 THEN 0 ELSE 1 END WHERE id = :id")
    suspend fun toggleFavorite(id: Int)
}

@Dao
interface MealPlanDao {

    // ---------- reads ----------

    @Transaction
    @Query("SELECT * FROM meal_plans WHERE date = :date ORDER BY sortOrder, id")
    fun getMealsForDate(date: String): Flow<List<MealPlanWithRecipe>>

    @Transaction
    @Query("SELECT * FROM meal_plans WHERE date = :date ORDER BY sortOrder, id")
    suspend fun getMealsForDateOnce(date: String): List<MealPlanWithRecipe>

    @Transaction
    @Query("SELECT * FROM meal_plans")
    suspend fun getAllPlansOnce(): List<MealPlanWithRecipe>

    @Query("SELECT * FROM meal_plans WHERE date BETWEEN :start AND :end")
    suspend fun getRawPlansBetween(start: String, end: String): List<MealPlan>

    // ---------- shopping list (servings-scaled; day + week scopes) ----------

    @Query(
        """
        SELECT i.name AS name, i.unit AS unit,
               ROUND(SUM(i.quantity *
                   (CASE WHEN mp.servings > 0 THEN CAST(mp.servings AS REAL)
                         ELSE CAST(r.servings AS REAL) END)
                   / CAST(r.servings AS REAL)), 2) AS totalQuantity,
               ROUND(SUM(i.price *
                   (CASE WHEN mp.servings > 0 THEN CAST(mp.servings AS REAL)
                         ELSE CAST(r.servings AS REAL) END)
                   / CAST(r.servings AS REAL)), 2) AS totalPrice,
               IFNULL(GROUP_CONCAT(DISTINCT i.allergens), '') AS allergens
        FROM meal_plans mp
        JOIN recipes r ON r.id = mp.recipeId
        JOIN ingredients i ON i.recipeId = mp.recipeId
        WHERE mp.date = :date
        GROUP BY i.name, i.unit
        ORDER BY i.name COLLATE NOCASE
        """
    )
    fun getShoppingListForDate(date: String): Flow<List<ShoppingItem>>

    @Query(
        """
        SELECT i.name AS name, i.unit AS unit,
               ROUND(SUM(i.quantity *
                   (CASE WHEN mp.servings > 0 THEN CAST(mp.servings AS REAL)
                         ELSE CAST(r.servings AS REAL) END)
                   / CAST(r.servings AS REAL)), 2) AS totalQuantity,
               ROUND(SUM(i.price *
                   (CASE WHEN mp.servings > 0 THEN CAST(mp.servings AS REAL)
                         ELSE CAST(r.servings AS REAL) END)
                   / CAST(r.servings AS REAL)), 2) AS totalPrice,
               IFNULL(GROUP_CONCAT(DISTINCT i.allergens), '') AS allergens
        FROM meal_plans mp
        JOIN recipes r ON r.id = mp.recipeId
        JOIN ingredients i ON i.recipeId = mp.recipeId
        WHERE mp.date = :date
        GROUP BY i.name, i.unit
        ORDER BY i.name COLLATE NOCASE
        """
    )
    suspend fun getShoppingListForDateOnce(date: String): List<ShoppingItem>

    @Query(
        """
        SELECT i.name AS name, i.unit AS unit,
               ROUND(SUM(i.quantity *
                   (CASE WHEN mp.servings > 0 THEN CAST(mp.servings AS REAL)
                         ELSE CAST(r.servings AS REAL) END)
                   / CAST(r.servings AS REAL)), 2) AS totalQuantity,
               ROUND(SUM(i.price *
                   (CASE WHEN mp.servings > 0 THEN CAST(mp.servings AS REAL)
                         ELSE CAST(r.servings AS REAL) END)
                   / CAST(r.servings AS REAL)), 2) AS totalPrice,
               IFNULL(GROUP_CONCAT(DISTINCT i.allergens), '') AS allergens
        FROM meal_plans mp
        JOIN recipes r ON r.id = mp.recipeId
        JOIN ingredients i ON i.recipeId = mp.recipeId
        WHERE mp.date BETWEEN :start AND :end
        GROUP BY i.name, i.unit
        ORDER BY i.name COLLATE NOCASE
        """
    )
    fun getShoppingListBetween(start: String, end: String): Flow<List<ShoppingItem>>

    @Query(
        """
        SELECT i.name AS name, i.unit AS unit,
               ROUND(SUM(i.quantity *
                   (CASE WHEN mp.servings > 0 THEN CAST(mp.servings AS REAL)
                         ELSE CAST(r.servings AS REAL) END)
                   / CAST(r.servings AS REAL)), 2) AS totalQuantity,
               ROUND(SUM(i.price *
                   (CASE WHEN mp.servings > 0 THEN CAST(mp.servings AS REAL)
                         ELSE CAST(r.servings AS REAL) END)
                   / CAST(r.servings AS REAL)), 2) AS totalPrice,
               IFNULL(GROUP_CONCAT(DISTINCT i.allergens), '') AS allergens
        FROM meal_plans mp
        JOIN recipes r ON r.id = mp.recipeId
        JOIN ingredients i ON i.recipeId = mp.recipeId
        WHERE mp.date BETWEEN :start AND :end
        GROUP BY i.name, i.unit
        ORDER BY i.name COLLATE NOCASE
        """
    )
    suspend fun getShoppingListBetweenOnce(start: String, end: String): List<ShoppingItem>

    // ---------- costs ----------

    @Query(
        """
        SELECT ROUND(SUM(i.price *
                   (CASE WHEN mp.servings > 0 THEN CAST(mp.servings AS REAL)
                         ELSE CAST(r.servings AS REAL) END)
                   / CAST(r.servings AS REAL)), 2)
        FROM meal_plans mp
        JOIN recipes r ON r.id = mp.recipeId
        JOIN ingredients i ON i.recipeId = mp.recipeId
        WHERE mp.date = :date
        """
    )
    fun getPlannedCostForDate(date: String): Flow<Double?>

    @Query(
        """
        SELECT ROUND(SUM(i.price *
                   (CASE WHEN mp.servings > 0 THEN CAST(mp.servings AS REAL)
                         ELSE CAST(r.servings AS REAL) END)
                   / CAST(r.servings AS REAL)), 2)
        FROM meal_plans mp
        JOIN recipes r ON r.id = mp.recipeId
        JOIN ingredients i ON i.recipeId = mp.recipeId
        WHERE mp.date BETWEEN :start AND :end
        """
    )
    fun getPlannedCostBetween(start: String, end: String): Flow<Double?>

    @Query(
        """
        SELECT mp.date AS date,
               ROUND(SUM(i.price *
                   (CASE WHEN mp.servings > 0 THEN CAST(mp.servings AS REAL)
                         ELSE CAST(r.servings AS REAL) END)
                   / CAST(r.servings AS REAL)), 2) AS cost
        FROM meal_plans mp
        JOIN recipes r ON r.id = mp.recipeId
        JOIN ingredients i ON i.recipeId = mp.recipeId
        WHERE mp.date BETWEEN :start AND :end
        GROUP BY mp.date
        """
    )
    fun getPlannedCostPerDayBetween(start: String, end: String): Flow<List<DayCost>>

    // ---------- insights ----------

    @Query(
        """SELECT r.name AS name, COUNT(mp.id) AS count
           FROM meal_plans mp JOIN recipes r ON r.id = mp.recipeId
           GROUP BY mp.recipeId ORDER BY count DESC LIMIT :limit"""
    )
    fun getTopPlanned(limit: Int): Flow<List<NameCount>>

    @Query("SELECT date, COUNT(*) AS count FROM meal_plans WHERE date BETWEEN :start AND :end GROUP BY date")
    fun getMealCountsBetween(start: String, end: String): Flow<List<DateCount>>

    // ---------- ordering / state ----------

    @Query("SELECT MAX(sortOrder) FROM meal_plans WHERE date = :date AND mealType = :mealType")
    suspend fun maxSortOrder(date: String, mealType: String): Int?

    @Query("UPDATE meal_plans SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Int, sortOrder: Int)

    @Transaction
    suspend fun updateSortOrders(orders: Map<Int, Int>) {
        orders.forEach { (id, order) -> updateSortOrder(id, order) }
    }

    @Query(
        """UPDATE meal_plans SET cookedAt =
           CASE WHEN cookedAt = '' THEN :now ELSE '' END WHERE id = :id"""
    )
    suspend fun toggleCooked(id: Int, now: String)

    @Query("DELETE FROM meal_plans WHERE date BETWEEN :start AND :end")
    suspend fun deletePlansBetween(start: String, end: String)

    @Insert
    suspend fun insert(mealPlan: MealPlan)

    @Delete
    suspend fun delete(mealPlan: MealPlan)
}

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE date = :date")
    fun getBudgetForDate(date: String): Flow<Budget?>

    @Query("SELECT * FROM budgets WHERE date = :date")
    suspend fun getBudgetForDateOnce(date: String): Budget?

    @Query("SELECT * FROM budgets ORDER BY date")
    suspend fun getAllBudgetsOnce(): List<Budget>

    @Query("SELECT * FROM budgets WHERE date BETWEEN :start AND :end ORDER BY date")
    fun getBudgetsBetween(start: String, end: String): Flow<List<Budget>>

    @Upsert
    suspend fun setBudget(budget: Budget)

    @Query("DELETE FROM budgets WHERE date = :date")
    suspend fun clearBudget(date: String)
}

@Dao
interface DietaryProfileDao {

    @Query("SELECT * FROM dietary_profile WHERE id = 1")
    fun getProfile(): Flow<DietaryProfile?>

    @Query("SELECT * FROM dietary_profile WHERE id = 1")
    suspend fun getProfileOnce(): DietaryProfile?

    @Upsert
    suspend fun setProfile(profile: DietaryProfile)
}

@Dao
interface PurchaseDao {

    @Query("SELECT * FROM purchases WHERE date = :date ORDER BY name")
    fun getPurchasesForDate(date: String): Flow<List<Purchase>>

    @Query("SELECT * FROM purchases ORDER BY date")
    suspend fun getAllOnce(): List<Purchase>

    @Query("SELECT * FROM purchases WHERE date = :date AND name = :name AND unit = :unit LIMIT 1")
    suspend fun findFor(date: String, name: String, unit: String): Purchase?

    @Query("SELECT date, SUM(actualPrice) AS cost FROM purchases WHERE date BETWEEN :start AND :end GROUP BY date")
    fun getActualSpendPerDayBetween(start: String, end: String): Flow<List<DayCost>>

    @Insert
    suspend fun insert(purchase: Purchase)

    @Update
    suspend fun update(purchase: Purchase)

    @Query("DELETE FROM purchases WHERE date = :date AND name = :name AND unit = :unit")
    suspend fun deleteFor(date: String, name: String, unit: String)
}
