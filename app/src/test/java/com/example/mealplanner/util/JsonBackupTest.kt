package com.example.mealplanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonBackupTest {

    @Test
    fun `parses a full backup`() {
        val json = """
        {"app":"meal-planner","exportedAt":"2024-06-01T10:00:00",
         "recipes":[{"name":"Oats","category":"Breakfast","prepTimeMinutes":5,"cookTimeMinutes":0,
            "servings":1,"instructions":"Stir","diets":["Vegetarian"],"adaptedNote":"","isFavorite":true,
            "tags":["Quick"],
            "ingredients":[{"name":"Oats","quantity":0.5,"unit":"cups","price":0.3,"allergens":["Gluten"]}]}],
         "mealPlans":[{"date":"2024-06-02","mealType":"Breakfast","servings":1,"sortOrder":0,
            "recipe":"Oats","cookedAt":"2024-06-02T08:00"}],
         "budgets":[{"date":"2024-06-02","amount":25}],
         "profile":{"allergies":["Dairy"],"diets":[],"hideClashing":false},
         "water":{"2024-06-02":5}}
        """.trimIndent()

        val parsed = JsonBackup.parse(json)
        assertEquals(1, parsed.recipes.size)
        assertTrue(parsed.recipes[0].recipe.isFavorite)
        assertEquals(listOf("Quick"), parsed.recipes[0].recipe.tags)
        assertEquals("Oats", parsed.plans.single().recipeName)
        assertEquals("2024-06-02T08:00", parsed.plans.single().cookedAt)
        assertEquals(25.0, parsed.budgets.single().amount, 1e-9)
        assertEquals(listOf("Dairy"), parsed.profile!!.allergies)
        assertEquals(5, parsed.water["2024-06-02"])
    }

    @Test
    fun `rejects foreign files`() {
        var threw = false
        try { JsonBackup.parse("""{"app":"something-else"}""") } catch (e: Exception) { threw = true }
        assertTrue(threw)
    }

    @Test
    fun `older backups without optional keys parse`() {
        val parsed = JsonBackup.parse("""{"app":"meal-planner","recipes":[{"name":"X"}]}""")
        assertEquals(1, parsed.recipes.size)
        assertTrue(parsed.plans.isEmpty() && parsed.budgets.isEmpty() && parsed.water.isEmpty())
        assertNull(parsed.profile)
    }

    @Test
    fun `malformed json throws`() {
        var threw = false
        try { JsonBackup.parse("""{not json""") } catch (e: Exception) { threw = true }
        assertTrue(threw)
    }

    @Test
    fun `plans are parsed even when their recipe is missing - resolution is the importer's job`() {
        val parsed = JsonBackup.parse(
            """{"app":"meal-planner","mealPlans":[{"date":"2024-06-01","mealType":"Dinner","recipe":"Ghost"}]}"""
        )
        assertEquals("Ghost", parsed.plans.single().recipeName)
    }
}
