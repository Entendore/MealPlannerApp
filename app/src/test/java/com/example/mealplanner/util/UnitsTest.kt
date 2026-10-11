package com.example.mealplanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsTest {

    @Test
    fun `resolves canonical keys aliases and plurals`() {
        assertEquals("ml", Units.resolve("ml")!!.key)
        assertEquals("cup", Units.resolve("cups")!!.key)
        assertEquals("lb", Units.resolve("LBS")!!.key)
        assertEquals("clove", Units.resolve("cloves")!!.key)
        assertEquals("tbsp", Units.resolve("tablespoon")!!.key)
    }

    @Test
    fun `unknown units resolve to null`() {
        assertNull(Units.resolve("handful"))
        assertNull(Units.resolve(""))
    }

    @Test
    fun `classifies volume weight count`() {
        assertEquals(UnitType.VOLUME, Units.typeOf("l"))
        assertEquals(UnitType.WEIGHT, Units.typeOf("grams"))
        assertEquals(UnitType.COUNT, Units.typeOf("clove"))
        assertNull(Units.typeOf("splash"))
    }

    @Test
    fun `displayForm uses singular for fractional quantities`() {
        val cup = Units.resolve("cup")!!
        assertEquals("cup", Units.displayForm(cup, 0.5))
        assertEquals("cup", Units.displayForm(cup, 1.0))
        assertEquals("cups", Units.displayForm(cup, 1.5))
        assertEquals("cups", Units.displayForm(cup, 3.0))
        val tbsp = Units.resolve("tbsp")!!
        assertEquals("tbsp", Units.displayForm(tbsp, 3.0))   // abbreviations never pluralize
    }

    @Test
    fun `suggestions match query and defaults exist`() {
        assertTrue(Units.suggestions("kilo").any { it.key == "kg" })
        assertTrue(Units.suggestions("").isNotEmpty())
    }
}
