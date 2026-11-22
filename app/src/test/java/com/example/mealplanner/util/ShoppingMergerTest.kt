package com.example.mealplanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingMergerTest {

    private fun item(name: String, unit: String, qty: Double, price: Double = 0.0, allergens: String = "") =
        com.example.mealplanner.data.ShoppingItem(name, unit, qty, price, allergens)

    @Test
    fun `merges volume units into the largest used unit`() {
        val merged = ShoppingMerger.merge(listOf(
            item("Milk", "ml", 500.0, 0.6),
            item("Milk", "l", 1.0, 1.2)
        ))
        assertEquals(1, merged.size)
        assertEquals("l", merged[0].unit)
        assertEquals(1.5, merged[0].totalQuantity, 1e-9)
        assertEquals(1.8, merged[0].totalPrice, 1e-9)
    }

    @Test
    fun `merges weight and falls back to smallest used unit when values are small`() {
        val merged = ShoppingMerger.merge(listOf(
            item("Flour", "g", 300.0),
            item("Flour", "kg", 0.2)
        ))
        assertEquals("g", merged.single().unit)            // 500 g: no used unit ≥ 1 except g
        assertEquals(500.0, merged.single().totalQuantity, 1e-9)
    }

    @Test
    fun `count units with different keys never merge`() {
        assertEquals(2, ShoppingMerger.merge(listOf(
            item("Garlic", "clove", 3.0), item("Garlic", "pc", 1.0)
        )).size)
    }

    @Test
    fun `count aliases normalize before merging`() {
        val merged = ShoppingMerger.merge(listOf(item("Eggs", "pc", 2.0), item("Eggs", "pcs", 4.0)))
        assertEquals(1, merged.size)
        assertEquals(6.0, merged.single().totalQuantity, 1e-9)
    }

    @Test
    fun `prices sum and allergens union`() {
        val merged = ShoppingMerger.merge(listOf(
            item("Yogurt", "g", 200.0, 1.0, "Dairy"),
            item("Yogurt", "g", 300.0, 2.0, "Dairy,Nuts")
        ))
        assertEquals(setOf("Dairy", "Nuts"), merged.single().allergens.split(',').toSet())
        assertEquals(3.0, merged.single().totalPrice, 1e-9)
    }

    @Test
    fun `different names never merge`() {
        assertEquals(2, ShoppingMerger.merge(listOf(
            item("Milk", "ml", 100.0), item("Cream", "ml", 100.0)
        )).size)
    }

    // consolidation fix: pick() selects the largest used unit with value >= 1 —
    // 3 tbsp (44.37 ml) + 100 ml = 144.37 ml → 9.76 tbsp (tbsp is the larger unit)
    @Test
    fun `volume mixes normalize to the largest used unit`() {
        val merged = ShoppingMerger.merge(listOf(
            item("Oil", "tbsp", 3.0),
            item("Oil", "ml", 100.0)
        ))
        assertEquals("tbsp", merged.single().unit)
        assertEquals(9.76, merged.single().totalQuantity, 0.01)
    }

    @Test
    fun `empty input merges to empty`() = assertTrue(ShoppingMerger.merge(emptyList()).isEmpty())

    @Test
    fun `single unknown-unit item passes through unchanged`() {
        val merged = ShoppingMerger.merge(listOf(item("Herbs", "handfuls", 2.0)))
        assertEquals("handfuls", merged.single().unit)
        assertEquals(2.0, merged.single().totalQuantity, 1e-9)
    }

    @Test
    fun `names differing only in case do not merge - documented behavior`() {
        assertEquals(2, ShoppingMerger.merge(listOf(
            item("Milk", "ml", 100.0), item("milk", "ml", 100.0)
        )).size)
    }
}
