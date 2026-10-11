package com.example.mealplanner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- "Basil & Sage" palette (hand-tuned M3 tonal pairs) ---
private val Basil10 = Color(0xFF002106); private val Basil40 = Color(0xFF35693D)
private val Basil80 = Color(0xFF9BD49C); private val Basil90 = Color(0xFFB7F1B8)
private val Sage10  = Color(0xFF101F10); private val Sage40  = Color(0xFF52634F)
private val Sage80  = Color(0xFFB9CCB3); private val Sage90  = Color(0xFFD5E8CE)
private val Teal10  = Color(0xFF002023); private val Teal40  = Color(0xFF38656A)
private val Teal80  = Color(0xFFA0CFD3); private val Teal90  = Color(0xFFBCEBEF)

private val LightColors = lightColorScheme(
    primary = Basil40, onPrimary = Color.White,
    primaryContainer = Basil90, onPrimaryContainer = Basil10,
    secondary = Sage40, onSecondary = Color.White,
    secondaryContainer = Sage90, onSecondaryContainer = Sage10,
    tertiary = Teal40, onTertiary = Color.White,
    tertiaryContainer = Teal90, onTertiaryContainer = Teal10,
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF9FAF4), onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFF9FAF4), onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFDEE5D9), onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF72796F), outlineVariant = Color(0xFFC2C9BD)
)

private val DarkColors = darkColorScheme(
    primary = Basil80, onPrimary = Color(0xFF013910),
    primaryContainer = Color(0xFF1C5125), onPrimaryContainer = Basil90,
    secondary = Sage80, onSecondary = Color(0xFF243424),
    secondaryContainer = Color(0xFF3A4B39), onSecondaryContainer = Sage90,
    tertiary = Teal80, onTertiary = Color(0xFF00363A),
    tertiaryContainer = Color(0xFF1F4D51), onTertiaryContainer = Teal90,
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF11140F), onBackground = Color(0xFFE2E3DC),
    surface = Color(0xFF11140F), onSurface = Color(0xFFE2E3DC),
    surfaceVariant = Color(0xFF424940), onSurfaceVariant = Color(0xFFC2C9BD),
    outline = Color(0xFF8C9388), outlineVariant = Color(0xFF424940)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)

/** Consistent spacing scale — use instead of magic numbers. */
object Spacing {
    val xs = 4.dp; val sm = 8.dp; val md = 16.dp; val lg = 24.dp; val xl = 32.dp
}

@Composable
fun MealPlannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,   // true = follow Android 12+ wallpaper colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
