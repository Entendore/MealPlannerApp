package com.example.mealplanner.util

import com.example.mealplanner.data.DietaryProfile
import com.example.mealplanner.data.Ingredient
import com.example.mealplanner.data.Recipe
import com.example.mealplanner.data.RecipeWithIngredients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllergenUtilTest {

    @Test
    fun `detects common allergens`() {
        assertEquals(listOf("Dairy"), AllergenUtil.detectAllergens("Whole milk"))
        assertTrue("Gluten" in AllergenUtil.detectAllergens("All-purpose flour"))
        assertEquals(listOf("Eggs"), AllergenUtil.detectAllergens("2 eggs"))
        assertTrue("Fish" in AllergenUtil.detectAllergens("smoked salmon"))
        assertTrue("Shellfish" in AllergenUtil.detectAllergens("king prawns"))
    }

    @Test
    fun `peanut butter is peanuts not dairy`() {
        assertEquals(listOf("Peanuts"), AllergenUtil.detectAllergens("Peanut butter"))
    }

    @Test
    fun `eggplant is not eggs`() {
        assertTrue(AllergenUtil.detectAllergens("Eggplant").isEmpty())
    }

    @Test
    fun `known false positive is pinned as documentation`() {
        // "oat milk" flags Gluten (oats) + Dairy (milk) — documented limitation
        assertEquals(setOf("Gluten", "Dairy"), AllergenUtil.detectAllergens("oat milk").toSet())
    }

    @Test
    fun `case insensitive and safe on empty or neutral names`() {
        assertEquals(listOf("Sesame"), AllergenUtil.detectAllergens("TAHINI"))
        assertTrue(AllergenUtil.detectAllergens("").isEmpty())
        assertTrue(AllergenUtil.detectAllergens("salt").isEmpty())
    }

    @Test
    fun `clash helpers compare against the profile`() {
        val item = RecipeWithIngredients(
            recipe = Recipe(name = "Test", diets = listOf("Vegetarian")),
            ingredients = listOf(Ingredient(name = "Milk", allergens = listOf("Dairy")))
        )
        val profile = DietaryProfile(
            allergies = listOf("Dairy", "Nuts"),
            diets = listOf("Vegetarian", "Vegan")
        )
        AllergenUtil.run {
            assertEquals(setOf("Dairy"), item.allergenClash(profile))
            assertEquals(listOf("Vegan"), item.dietClash(profile))
            assertTrue(item.hasClash(profile))
        }
    }
}
