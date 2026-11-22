package com.example.mealplanner.util

import com.example.mealplanner.data.Budget
import com.example.mealplanner.data.DietaryProfile
import com.example.mealplanner.data.Ingredient
import com.example.mealplanner.data.Recipe
import com.example.mealplanner.data.RecipeWithIngredients
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser for the JSON export format. Mirrors MealPlannerViewModel.exportData;
 * every field except the "app" marker and recipe names is optional so older
 * backups import cleanly.
 */
object JsonBackup {

    data class ParsedPlan(
        val date: String,
        val mealType: String,
        val servings: Int,
        val sortOrder: Int,
        val recipeName: String,
        val cookedAt: String = ""
    )

    data class Parsed(
        val recipes: List<RecipeWithIngredients>,
        val plans: List<ParsedPlan>,
        val budgets: List<Budget>,
        val profile: DietaryProfile?,
        val water: Map<String, Int> = emptyMap()
    )

    data class Counts(
        val recipesAdded: Int,
        val recipesUpdated: Int,
        val plansImported: Int,
        val budgetsImported: Int,
        val profileImported: Boolean
    )

    fun parse(text: String): Parsed {
        val root = JSONObject(text)
        if (root.optString("app") != "meal-planner") error("Not a Meal Planner backup")

        val recipes = mutableListOf<RecipeWithIngredients>()
        val recipesArr = root.optJSONArray("recipes") ?: JSONArray()
        for (i in 0 until recipesArr.length()) {
            val o = recipesArr.getJSONObject(i)
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            val recipe = Recipe(
                name = name,
                category = o.optString("category", "Dinner"),
                prepTimeMinutes = o.optInt("prepTimeMinutes", 0),
                cookTimeMinutes = o.optInt("cookTimeMinutes", 0),
                servings = o.optInt("servings", 1).coerceAtLeast(1),
                instructions = o.optString("instructions", ""),
                diets = o.optStringArray("diets"),
                adaptedNote = o.optString("adaptedNote", ""),
                isFavorite = o.optBoolean("isFavorite", false),
                tags = o.optStringArray("tags")
            )
            val ingredients = mutableListOf<Ingredient>()
            val ingArr = o.optJSONArray("ingredients") ?: JSONArray()
            for (j in 0 until ingArr.length()) {
                val io = ingArr.getJSONObject(j)
                val ingName = io.optString("name").trim()
                if (ingName.isNotEmpty()) {
                    ingredients += Ingredient(
                        name = ingName,
                        quantity = io.optDouble("quantity", 1.0),
                        unit = io.optString("unit", ""),
                        price = io.optDouble("price", 0.0),
                        allergens = io.optStringArray("allergens")
                    )
                }
            }
            recipes += RecipeWithIngredients(recipe, ingredients)
        }

        val plans = mutableListOf<ParsedPlan>()
        val plansArr = root.optJSONArray("mealPlans") ?: JSONArray()
        for (i in 0 until plansArr.length()) {
            val o = plansArr.getJSONObject(i)
            val recipeName = o.optString("recipe").trim()
            val date = o.optString("date").trim()
            if (recipeName.isNotEmpty() && date.isNotEmpty()) {
                plans += ParsedPlan(
                    date = date,
                    mealType = o.optString("mealType", "Dinner"),
                    servings = o.optInt("servings", 0),
                    sortOrder = o.optInt("sortOrder", 0),
                    recipeName = recipeName,
                    cookedAt = o.optString("cookedAt", "")
                )
            }
        }

        val budgets = mutableListOf<Budget>()
        val budgetsArr = root.optJSONArray("budgets") ?: JSONArray()
        for (i in 0 until budgetsArr.length()) {
            val o = budgetsArr.getJSONObject(i)
            val date = o.optString("date").trim()
            val amount = o.optDouble("amount", 0.0)
            if (date.isNotEmpty() && amount > 0) budgets += Budget(date, amount)
        }

        val profile = root.optJSONObject("profile")?.let {
            DietaryProfile(
                allergies = it.optStringArray("allergies"),
                diets = it.optStringArray("diets"),
                hideClashing = it.optBoolean("hideClashing", false)
            )
        }

        val water = mutableMapOf<String, Int>()
        root.optJSONObject("water")?.let { w ->
            w.keys().forEach { d -> water[d] = w.optInt(d, 0) }
        }

        return Parsed(recipes, plans, budgets, profile, water)
    }

    private fun JSONObject.optStringArray(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { k -> arr.optString(k).ifBlank { null } }
    }
}
