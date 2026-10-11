package com.example.mealplanner.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String = "Dinner",
    val prepTimeMinutes: Int = 0,
    val cookTimeMinutes: Int = 0,
    val servings: Int = 1,
    val instructions: String = "",
    val photoPath: String? = null,
    val diets: List<String> = emptyList(),
    val adaptedNote: String = "",
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList()
)

@Entity(
    tableName = "ingredients",
    foreignKeys = [ForeignKey(
        entity = Recipe::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("recipeId")]
)
data class Ingredient(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recipeId: Int = 0,
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "",
    val price: Double = 0.0,
    val allergens: List<String> = emptyList()
)

@Entity(
    tableName = "meal_plans",
    foreignKeys = [ForeignKey(
        entity = Recipe::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("recipeId"), Index("date")]
)
data class MealPlan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,        // "2024-06-15"
    val mealType: String,    // "Breakfast" | "Lunch" | "Dinner"
    val recipeId: Int,
    val servings: Int = 0,   // planned servings; 0 = use the recipe's default
    val sortOrder: Int = 0,  // position within its day + meal type (drag-reorder)
    val cookedAt: String = "" // "" = planned; ISO datetime = cooked
)

/** Per-day or per-month budget. Month keys use "yyyy-MM" (see Budget screen). */
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val date: String,
    val amount: Double
)

/** Single-row user profile (id is always 1). */
@Entity(tableName = "dietary_profile")
data class DietaryProfile(
    @PrimaryKey val id: Int = 1,
    val allergies: List<String> = emptyList(),
    val diets: List<String> = emptyList(),
    val hideClashing: Boolean = false
)

/** A recorded real-world purchase (one shopping-trip item). */
@Entity(tableName = "purchases", indices = [Index("date")])
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val name: String,
    val unit: String,
    val quantity: Double,
    val estimatedPrice: Double,
    val actualPrice: Double
)

// ---------- Room Pojos (not tables) ----------

data class RecipeWithIngredients(
    @Embedded val recipe: Recipe,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<Ingredient>
)

/** Planned meal + recipe + ingredients (ingredients power clash detection). */
data class MealPlanWithRecipe(
    @Embedded val mealPlan: MealPlan,
    @Relation(parentColumn = "recipeId", entityColumn = "id")
    val recipe: Recipe,
    @Relation(parentColumn = "recipeId", entityColumn = "recipeId")
    val ingredients: List<Ingredient> = emptyList()
)

/** Aggregated shopping-list row (allergens is a raw concat — dedupe in UI). */
data class ShoppingItem(
    val name: String,
    val unit: String,
    val totalQuantity: Double,
    val totalPrice: Double = 0.0,
    val allergens: String = ""
)

data class DayCost(val date: String, val cost: Double)
data class NameCount(val name: String, val count: Int)
data class DateCount(val date: String, val count: Int)
