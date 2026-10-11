package com.example.mealplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mealplanner.ui.AppNavigation
import com.example.mealplanner.ui.theme.MealPlannerTheme
import com.example.mealplanner.ui.theme.ThemePrefs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePrefs.init(this)
        enableEdgeToEdge()
        setContent {
            MealPlannerTheme(
                darkTheme = when (ThemePrefs.themeMode) {
                    ThemePrefs.LIGHT -> false
                    ThemePrefs.DARK -> true
                    else -> isSystemInDarkTheme()
                }
            ) {
                AppNavigation(viewModel())
            }
        }
    }
}
