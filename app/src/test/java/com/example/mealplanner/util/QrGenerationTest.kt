package com.example.mealplanner.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QrGenerationTest {

    @Test
    fun `normal payloads produce a square bitmap`() {
        val payload = RecipeQr.encode(
            com.example.mealplanner.data.RecipeWithIngredients(
                com.example.mealplanner.data.Recipe(name = "Pancakes"),
                listOf(com.example.mealplanner.data.Ingredient(name = "Flour"))
            )
        )
        val bmp = RecipeQr.generateBitmap(payload, 240)
        assertNotNull(bmp)
        assertEquals(bmp!!.width, bmp.height)
        assertEquals(240, bmp.width)
        bmp.recycle()
    }

    @Test
    fun `oversized payloads return null instead of throwing`() {
        val huge = "MEALPLANNER:1:" + JSONObject().put("n", "a".repeat(4000))
        assertNull(RecipeQr.generateBitmap(huge, 240))
    }
}
