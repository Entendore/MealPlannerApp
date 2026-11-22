package com.example.mealplanner.util

import android.graphics.Bitmap
import android.graphics.Color
import com.example.mealplanner.data.DietaryProfile
import com.example.mealplanner.data.Ingredient
import com.example.mealplanner.data.Recipe
import com.example.mealplanner.data.RecipeWithIngredients
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONArray
import org.json.JSONObject

data class SharedRecipe(val recipe: Recipe, val ingredients: List<Ingredient>)

/** Compact versioned wire format for sharing recipes between devices. */
object RecipeQr {

    private const val PREFIX = "MEALPLANNER:1:"

    fun isRecipePayload(text: String) = text.startsWith(PREFIX)

    fun encode(item: RecipeWithIngredients): String {
        val r = item.recipe
        val json = JSONObject()
            .put("n", r.name)
            .put("c", r.category)
            .put("p", r.prepTimeMinutes)
            .put("k", r.cookTimeMinutes)
            .put("s", r.servings)
            .put("d", r.instructions)
            .put("t", JSONArray(r.diets))
            .put("a", r.adaptedNote)
            .put("g", JSONArray().apply {
                item.ingredients.forEach {
                    put(JSONArray(listOf(it.name, it.quantity, it.unit)))
                }
            })
        return PREFIX + json
    }

    /** Returns null if the payload is malformed. Photos are not shared via QR.
     *  Ingredients are allergen-tagged at decode time so imports arrive pre-tagged. */
    fun decode(text: String): SharedRecipe? = runCatching {
        val json = JSONObject(text.removePrefix(PREFIX))
        val name = json.getString("n").trim()
        if (name.isEmpty()) return null
        val recipe = Recipe(
            name = name,
            category = json.optString("c", "Dinner"),
            prepTimeMinutes = json.optInt("p", 0),
            cookTimeMinutes = json.optInt("k", 0),
            servings = json.optInt("s", 1).coerceAtLeast(1),
            instructions = json.optString("d", ""),
            diets = json.optJSONArray("t")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optString(i).ifBlank { null } }
            } ?: emptyList(),
            adaptedNote = json.optString("a", "")
        )
        val ingredients = mutableListOf<Ingredient>()
        val rows = json.optJSONArray("g") ?: JSONArray()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONArray(i)
            val ingName = row.optString(0).trim()
            if (ingName.isNotEmpty()) {
                ingredients += Ingredient(
                    name = ingName,
                    quantity = row.optDouble(1, 1.0),
                    unit = row.optString(2, ""),
                    allergens = AllergenUtil.detectAllergens(ingName)
                )
            }
        }
        SharedRecipe(recipe, ingredients)
    }.getOrNull()

    /** QR bitmap; null if the content exceeds QR capacity. */
    fun generateBitmap(content: String, sizePx: Int): Bitmap? = try {
        val matrix = QRCodeWriter().encode(
            content, BarcodeFormat.QR_CODE, sizePx, sizePx,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,  // max capacity
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1
            )
        )
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[y * w + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    } catch (e: Exception) {
        null
    }
}

/** Profile sharing — tiny payloads, always well under QR capacity. */
object ProfileQr {

    private const val PREFIX = "MEALPLANNER-PROFILE:1:"

    fun isProfilePayload(text: String) = text.startsWith(PREFIX)

    fun encode(profile: DietaryProfile): String {
        val json = JSONObject()
            .put("a", JSONArray(profile.allergies))
            .put("d", JSONArray(profile.diets))
        return PREFIX + json
    }

    /** hideClashing is deliberately not shared — it's a per-device display choice. */
    fun decode(text: String): DietaryProfile? = runCatching {
        val json = JSONObject(text.removePrefix(PREFIX))
        fun read(key: String): List<String> {
            val arr = json.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i -> arr.optString(i).ifBlank { null } }
        }
        DietaryProfile(allergies = read("a"), diets = read("d"))
    }.getOrNull()
}
