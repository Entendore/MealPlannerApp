package com.example.mealplanner.util

import com.example.mealplanner.data.DietaryProfile
import com.example.mealplanner.data.Ingredient
import com.example.mealplanner.data.Recipe
import com.example.mealplanner.data.RecipeWithIngredients
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadTest {

    private fun sample() = RecipeWithIngredients(
        Recipe(name = "Pancakes", category = "Breakfast", prepTimeMinutes = 10,
            cookTimeMinutes = 15, servings = 4, instructions = "1. Mix\n2. Cook",
            diets = listOf("Vegetarian"), adaptedNote = "nut-free for Sam"),
        listOf(Ingredient(name = "Flour", quantity = 2.0, unit = "cups", price = 1.2),
               Ingredient(name = "Milk", quantity = 1.5, unit = "cups"))
    )

    @Test
    fun `recipe round trip preserves fields`() {
        val decoded = RecipeQr.decode(RecipeQr.encode(sample()))!!
        assertEquals("Pancakes", decoded.recipe.name)
        assertEquals("Breakfast", decoded.recipe.category)
        assertEquals(4, decoded.recipe.servings)
        assertEquals("Vegetarian", decoded.recipe.diets.single())
        assertEquals("nut-free for Sam", decoded.recipe.adaptedNote)
        assertEquals(2, decoded.ingredients.size)
        assertEquals(1.5, decoded.ingredients[1].quantity, 1e-9)
    }

    @Test
    fun `garbage is rejected`() {
        assertNull(RecipeQr.decode("hello world"))
        assertFalse(RecipeQr.isRecipePayload("hello"))
    }

    @Test
    fun `legacy payload without newer fields still decodes`() {
        val decoded = RecipeQr.decode("MEALPLANNER:1:{\"n\":\"Old\",\"g\":[]}")!!
        assertEquals("Old", decoded.recipe.name)
        assertTrue(decoded.recipe.diets.isEmpty())
        assertEquals("", decoded.recipe.adaptedNote)
    }

    @Test
    fun `profile round trip`() {
        val profile = DietaryProfile(allergies = listOf("Dairy"), diets = listOf("Vegan"))
        val decoded = ProfileQr.decode(ProfileQr.encode(profile))!!
        assertEquals(listOf("Dairy"), decoded.allergies)
        assertEquals(listOf("Vegan"), decoded.diets)
        assertFalse(decoded.hideClashing)   // deliberately not shared
    }

    @Test
    fun `unicode names and empty ingredients survive`() {
        val item = RecipeWithIngredients(Recipe(name = "Crème brûlée"), emptyList())
        val decoded = RecipeQr.decode(RecipeQr.encode(item))!!
        assertEquals("Crème brûlée", decoded.recipe.name)
        assertTrue(decoded.ingredients.isEmpty())
    }

    @Test
    fun `empty profile round trips`() {
        val decoded = ProfileQr.decode(ProfileQr.encode(DietaryProfile()))!!
        assertTrue(decoded.allergies.isEmpty() && decoded.diets.isEmpty())
    }

    @Test
    fun `decoded ingredients are allergen tagged`() {
        val item = RecipeWithIngredients(Recipe(name = "X"),
            listOf(Ingredient(name = "Milk")))
        val decoded = RecipeQr.decode(RecipeQr.encode(item))!!
        assertEquals(listOf("Dairy"), decoded.ingredients.single().allergens)
    }

    @Suppress("unused")
    private fun keepJsonImport() = JSONObject()
}
