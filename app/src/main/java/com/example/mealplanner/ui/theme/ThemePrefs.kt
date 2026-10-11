package com.example.mealplanner.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Persisted appearance choice; a State inside a singleton so all screens recompose. */
object ThemePrefs {
    const val SYSTEM = 0; const val LIGHT = 1; const val DARK = 2
    private const val FILE = "settings"
    private const val KEY = "theme_mode"

    var themeMode by mutableIntStateOf(SYSTEM)
        private set

    fun init(context: Context) {
        themeMode = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY, SYSTEM)
    }

    fun set(context: Context, mode: Int) {
        themeMode = mode
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(KEY, mode).apply()
    }
}
