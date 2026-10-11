package com.example.mealplanner.ui

import com.example.mealplanner.R
import com.example.mealplanner.util.AllergenUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LabelsTest {

    @Test
    fun `every canonical tag and meal type has a label resource`() {
        AllergenUtil.ALLERGENS.forEach { assertNotEquals(0, allergenLabelRes(it)) }
        AllergenUtil.DIETS.forEach { assertNotEquals(0, dietLabelRes(it)) }
        listOf("Breakfast", "Lunch", "Dinner").forEach { assertNotEquals(0, mealTypeLabelRes(it)) }
    }

    @Test
    fun `unknown tags fall back to the last mapping entries`() {
        assertEquals(R.string.allergen_sesame, allergenLabelRes("SomethingNew"))
        assertEquals(R.string.diet_keto, dietLabelRes("SomethingNew"))
    }
}
