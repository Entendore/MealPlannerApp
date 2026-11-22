package com.example.mealplanner.util

import com.example.mealplanner.data.MealPlan
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Pure planning math for "copy week" — no DB access, fully unit-testable. */
object WeekCopy {

    fun buildCopies(
        source: List<MealPlan>,
        weeks: Long,
        existingTarget: List<MealPlan>,
        replace: Boolean
    ): List<MealPlan> {
        val maxOrder = mutableMapOf<Pair<String, String>, Int>()
        if (!replace) {
            existingTarget.forEach {
                val key = it.date to it.mealType
                maxOrder[key] = maxOf(maxOrder[key] ?: -1, it.sortOrder)
            }
        }
        return source.sortedBy { it.sortOrder }.map { plan ->
            val newDate = LocalDate.parse(plan.date).plusWeeks(weeks)
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
            val key = newDate to plan.mealType
            val order = (maxOrder[key] ?: -1) + 1
            maxOrder[key] = order
            // id=0 → Room inserts a fresh row; a copy is a plan, never history
            plan.copy(id = 0, date = newDate, sortOrder = order, cookedAt = "")
        }
    }
}
