package com.example.mealplanner.util

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WaterPrefsTest {

    private fun context() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `per-day counters are independent and clamp negatives`() {
        WaterPrefs.set(context(), "2024-06-01", 3)
        WaterPrefs.set(context(), "2024-06-02", 5)
        assertEquals(3, WaterPrefs.get(context(), "2024-06-01"))
        assertEquals(5, WaterPrefs.get(context(), "2024-06-02"))
        WaterPrefs.set(context(), "2024-06-01", -2)
        assertEquals(0, WaterPrefs.get(context(), "2024-06-01"))
    }

    @Test
    fun `unknown days default to zero and all exposes only logged days`() {
        assertEquals(0, WaterPrefs.get(context(), "1999-01-01"))
        WaterPrefs.set(context(), "2024-06-01", 2)
        assertEquals(mapOf("2024-06-01" to 2), WaterPrefs.all(context()))
    }
}
