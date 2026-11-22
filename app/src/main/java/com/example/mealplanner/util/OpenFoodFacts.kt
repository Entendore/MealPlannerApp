package com.example.mealplanner.util

import com.example.mealplanner.data.Ingredient
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

sealed class BarcodeLookupResult {
    data class Found(val ingredient: Ingredient) : BarcodeLookupResult()
    object NotFound : BarcodeLookupResult()
    object Error : BarcodeLookupResult()
}

/**
 * Free, open product database (ODbL — attribute "Open Food Facts" in your UI,
 * keep the User-Agent identifying the app). Product data is licensed ODbL.
 */
object OpenFoodFacts {

    private const val BASE = "https://world.openfoodfacts.org/api/v2/product/"

    /** OFF tag (after stripping the language prefix) → canonical AllergenUtil key. */
    private val TAG_MAP = mapOf(
        "milk" to "Dairy", "dairy" to "Dairy", "lactose" to "Dairy",
        "gluten" to "Gluten", "wheat" to "Gluten", "barley" to "Gluten", "rye" to "Gluten",
        "egg" to "Eggs", "eggs" to "Eggs",
        "nuts" to "Nuts", "tree-nuts" to "Nuts", "almonds" to "Nuts", "hazelnuts" to "Nuts",
        "walnuts" to "Nuts", "cashews" to "Nuts", "pistachios" to "Nuts", "pecans" to "Nuts",
        "peanuts" to "Peanuts",
        "soybeans" to "Soy", "soy" to "Soy", "soya" to "Soy",
        "fish" to "Fish",
        "crustaceans" to "Shellfish", "molluscs" to "Shellfish", "shellfish" to "Shellfish",
        "sesame-seeds" to "Sesame", "sesame" to "Sesame"
    )

    /** "en:milk" → "Dairy"; unmapped tags (celery, mustard, …) are dropped. */
    fun mapOffTags(tags: List<String>): List<String> = tags.mapNotNull { raw ->
        TAG_MAP[raw.substringAfterLast(':').lowercase()]
    }.distinct()

    /** baseUrl is injectable for tests (MockWebServer). */
    fun lookup(code: String, baseUrl: String = BASE): BarcodeLookupResult = try {
        val conn = URL(
            baseUrl + URLEncoder.encode(code, "UTF-8") +
                ".json?fields=product_name,quantity,allergens_tags,traces_tags"
        ).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", "MealPlanner - Android - Version 1.0")
            if (conn.responseCode != 200) {
                if (conn.responseCode == 404) BarcodeLookupResult.NotFound else BarcodeLookupResult.Error
            } else {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parse(body)
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        BarcodeLookupResult.Error
    }

    private fun parse(body: String): BarcodeLookupResult {
        val root = JSONObject(body)
        if (root.optInt("status", 0) != 1) return BarcodeLookupResult.NotFound
        val product = root.optJSONObject("product") ?: return BarcodeLookupResult.NotFound
        val name = product.optString("product_name").trim()
        if (name.isEmpty()) return BarcodeLookupResult.NotFound
        val (qty, unit) = splitQuantity(product.optString("quantity"))
        val tags = readTags(product, "allergens_tags") + readTags(product, "traces_tags")
        return BarcodeLookupResult.Found(
            Ingredient(name = name, quantity = qty, unit = unit, allergens = mapOffTags(tags))
        )
    }

    private fun readTags(product: JSONObject, key: String): List<String> {
        val arr = product.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }

    /** "500 g" → (500.0, "g"); "250ml" → (250.0, "ml"); anything else → (1.0, "") */
    private fun splitQuantity(raw: String): Pair<Double, String> {
        val match = Regex("^\\s*([0-9]+(?:[.,][0-9]+)?)\\s*(.*)$").find(raw)
            ?: return 1.0 to ""
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 1.0 to ""
        return value to match.groupValues[2].trim()
    }
}
