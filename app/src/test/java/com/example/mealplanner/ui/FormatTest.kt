package com.example.mealplanner.ui

import com.example.mealplanner.util.Durations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {

    @Test
    fun `cooking fractions render from scaled quantities`() {
        assertEquals("½", formatQuantity(0.5))
        assertEquals("1½", formatQuantity(1.5))
        assertEquals("¾", formatQuantity(0.75))
        assertEquals("⅔", formatQuantity(0.6666))
        assertEquals("0.4", formatQuantity(0.4))     // outside tolerance stays decimal
        assertEquals("2", formatQuantity(2.0))
    }

    @Test
    fun `pdf-safe mode drops exotic fractions only`() {
        assertEquals("0.33", formatQuantity(1.0 / 3.0, allowExoticFractions = false))
        assertEquals("½", formatQuantity(0.5, allowExoticFractions = false))
    }

    @Test
    fun `amounts pluralize correctly`() {
        assertEquals("½ cup", formatAmount(0.5, "cup"))
        assertEquals("2 cups", formatAmount(2.0, "cup"))
        assertEquals("3 cloves", formatAmount(3.0, "clove"))
        assertEquals("1 tbsp", formatAmount(1.0, "tbsp"))
        assertEquals("2 handfuls", formatAmount(2.0, "handfuls"))   // unknown unit passes through
    }

    @Test
    fun `durations format`() {
        assertEquals("0:59", formatDuration(59))
        assertEquals("1:01", formatDuration(61))
        assertEquals("1:01:01", formatDuration(3661))
    }

    @Test
    fun `step duration parsing`() {
        assertEquals(1500L, Durations.parseSeconds("Simmer for 25 minutes"))
        assertEquals(5400L, Durations.parseSeconds("Bake 1 hour 30 minutes"))
        assertEquals(3600L, Durations.parseSeconds("Bake 1 Hour"))
        assertEquals(90L, Durations.parseSeconds("rest 90 sec"))
        assertNull(Durations.parseSeconds("Whisk the eggs"))
        assertNull(Durations.parseSeconds("1 medium onion"))          // "m" must not match "medium"
        assertNull(Durations.parseSeconds("Add 350 g flour"))         // weight, not time
    }

    // consolidation fix: the regex requires a word boundary after the unit, so
    // compact forms like "1h30" match NOTHING (h is followed by a digit).
    // Documented limitation — extend the regex if compact input is ever wanted.
    @Test
    fun `compact h-m notation is unsupported - pinned`() {
        assertNull(Durations.parseSeconds("1h30"))
    }

    @Test
    fun `plural unit words and sums parse`() {
        assertEquals(7200L, Durations.parseSeconds("2 hrs"))
        assertEquals(2700L, Durations.parseSeconds("45min"))
        assertEquals(10L, Durations.parseSeconds("rest 10s"))
        assertEquals(9000L, Durations.parseSeconds("Soak 2 hours then bake 30 minutes"))
    }
}
