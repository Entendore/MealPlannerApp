package com.example.mealplanner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mealplanner.R
import com.example.mealplanner.ui.screens.PlanMealDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class PlanMealDialogTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun baseDayPlusRepeatChipsProduceSortedDistinctDates() {
        var confirmed: List<String> = emptyList()
        rule.setContent {
            MaterialTheme {
                PlanMealDialog(
                    defaultServings = 2,
                    onConfirm = { dates, _, _ -> confirmed = dates },
                    onDismiss = {}
                )
            }
        }
        val tomorrowLabel = LocalDate.now().plusDays(1)
            .format(DateTimeFormatter.ofPattern("EEE d"))    // same locale-sensitive pattern as the app
        rule.onNodeWithText(tomorrowLabel).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.add_to_plan)).performClick()

        assertEquals(2, confirmed.size)
        assertEquals(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE), confirmed[0])
        assertEquals(LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE), confirmed[1])
    }
}
