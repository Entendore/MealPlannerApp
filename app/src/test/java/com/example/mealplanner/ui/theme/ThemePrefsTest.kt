package com.example.mealplanner.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemePrefsTest {

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        context().getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `defaults to system and persists the choice across init`() {
        ThemePrefs.init(context())
        assertEquals(ThemePrefs.SYSTEM, ThemePrefs.themeMode)

        ThemePrefs.set(context(), ThemePrefs.DARK)
        assertEquals(ThemePrefs.DARK, ThemePrefs.themeMode)

        ThemePrefs.init(context())          // simulates a fresh process
        assertEquals(ThemePrefs.DARK, ThemePrefs.themeMode)
    }
}
