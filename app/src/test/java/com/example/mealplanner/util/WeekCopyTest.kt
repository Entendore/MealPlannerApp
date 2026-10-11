package com.example.mealplanner.util

import com.example.mealplanner.data.MealPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekCopyTest {

    private fun plan(date: String, type: String, order: Int, cooked: String = "") =
        MealPlan(id = order + 1, date = date, mealType = type, recipeId = 1,
            sortOrder = order, cookedAt = cooked)

    @Test
    fun `copies shift by weeks and never inherit cooked state`() {
        val copies = WeekCopy.buildCopies(
            source = listOf(plan("2024-06-03", "Dinner", 0, cooked = "2024-06-03T18:00")),
            weeks = 1, existingTarget = emptyList(), replace = true
        )
        assertEquals("2024-06-10", copies.single().date)
        assertEquals("", copies.single().cookedAt)
        assertEquals(0, copies.single().sortOrder)
        assertEquals(0, copies.single().id)          // id=0 → Room inserts a fresh row
    }

    @Test
    fun `append mode continues the target week's sort orders`() {
        val copies = WeekCopy.buildCopies(
            source = listOf(plan("2024-06-03", "Dinner", 0)), weeks = 1,
            existingTarget = listOf(plan("2024-06-10", "Dinner", 2)), replace = false
        )
        assertEquals(3, copies.single().sortOrder)
    }

    @Test
    fun `replace mode restarts ordering and stacks per section`() {
        val copies = WeekCopy.buildCopies(
            source = listOf(
                plan("2024-06-03", "Dinner", 0),
                plan("2024-06-03", "Dinner", 1),
                plan("2024-06-04", "Lunch", 0)
            ),
            weeks = 2,
            existingTarget = listOf(plan("2024-06-17", "Dinner", 5)),
            replace = true
        )
        val dinner = copies.filter { it.mealType == "Dinner" }.sortedBy { it.sortOrder }
        assertEquals(listOf(0, 1), dinner.map { it.sortOrder })
        assertEquals("2024-06-17", dinner[0].date)
        assertEquals(0, copies.first { it.mealType == "Lunch" }.sortOrder)
    }

    @Test
    fun `copying backward shifts dates by negative weeks`() {
        val copies = WeekCopy.buildCopies(
            listOf(plan("2024-06-03", "Dinner", 0)), weeks = -1,
            existingTarget = emptyList(), replace = true
        )
        assertEquals("2024-05-27", copies.single().date)
    }

    @Test
    fun `empty source produces no copies`() {
        assertTrue(WeekCopy.buildCopies(emptyList(), 1, emptyList(), true).isEmpty())
    }
}
