package com.example.mealplanner.util

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenFoodFactsTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun url() = server.url("/api/v2/product/").toString()

    @Test
    fun `parses product and maps allergen tags`() {
        server.enqueue(MockResponse().setBody(
            """{"status":1,"product":{"product_name":"Nutella","quantity":"400 g",
               "allergens_tags":["en:milk","en:nuts"],"traces_tags":["en:soybeans"]}}"""
        ))
        val found = OpenFoodFacts.lookup("3017620422003", url()) as BarcodeLookupResult.Found
        assertEquals("Nutella", found.ingredient.name)
        assertEquals(400.0, found.ingredient.quantity, 1e-9)
        assertEquals("g", found.ingredient.unit)
        assertEquals(setOf("Dairy", "Nuts", "Soy"), found.ingredient.allergens.toSet())
    }

    @Test
    fun `404 maps to NotFound and garbage maps to Error`() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertTrue(OpenFoodFacts.lookup("0", url()) is BarcodeLookupResult.NotFound)
        server.enqueue(MockResponse().setBody("not json"))
        assertTrue(OpenFoodFacts.lookup("1", url()) is BarcodeLookupResult.Error)
    }

    @Test
    fun `off tag mapping ignores language prefixes and unknown tags`() {
        assertEquals(listOf("Dairy", "Gluten"),
            OpenFoodFacts.mapOffTags(listOf("en:milk", "de:gluten", "en:celery")))
    }
}
