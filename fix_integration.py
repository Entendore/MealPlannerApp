#!/usr/bin/env python3
"""Applies the consolidated integration fixes. Idempotent; exits 1 on any mismatch."""
import pathlib, sys

FIXES = [
    ("app/src/main/AndroidManifest.xml",
     '    <uses-permission android:name="android.permission.CAMERA" />\n',
     '    <uses-permission android:name="android.permission.CAMERA" />\n    <uses-permission android:name="android.permission.VIBRATE" />\n',
     "add VIBRATE permission"),
    ("app/src/main/res/values/strings.xml",
     '    <!-- Plurals -->',
     '    <string name="checked_count">%1$d of %2$d checked</string>\n'
     '    <string name="pdf_page">Page %1$d</string>\n'
     '    <string name="servings_count_planned">%1$d servings</string>\n\n'
     '    <!-- Plurals -->',
     "add checked_count / pdf_page / servings_count_planned strings"),
    ("app/src/main/java/com/example/mealplanner/MealPlannerViewModel.kt",
     'import com.example.mealplanner.data.MealPlan\nimport com.example.mealplanner.data.MealPlanDao\n',
     'import com.example.mealplanner.data.MealPlan\nimport com.example.mealplanner.data.MealPlanDao\nimport com.example.mealplanner.data.MealPlanWithRecipe\n',
     "import MealPlanWithRecipe in ViewModel"),
    ("app/src/main/java/com/example/mealplanner/MealPlannerViewModel.kt",
     '    fun mealsForDate(date: String): Flow<List<MealPlan>> = mealPlanDaoFlow(date)\n'
     '    private fun mealPlanDaoFlow(date: String) = mealPlanDao.getMealsForDate(date)\n',
     '    fun mealsForDate(date: String): Flow<List<MealPlanWithRecipe>> = mealPlanDao.getMealsForDate(date)\n',
     "fix mealsForDate return type"),
    ("app/src/main/java/com/example/mealplanner/ui/components/CommonComponents.kt",
     'import androidx.compose.material.icons.rounded.WaterDrop\n',
     'import androidx.compose.material.icons.rounded.WaterDrop\nimport androidx.compose.material.icons.rounded.WbSunny\n',
     "import WbSunny icon"),
    ("app/src/main/java/com/example/mealplanner/ui/components/CommonComponents.kt",
     'private val WbSunnyIcon get() = Icons.Rounded.WbSunny\n'
     'fun categoryIconOrNull(category: String): ImageVector = categoryIcon(category)\n'
     '@Suppress("unused")\n'
     'private fun keepWbSunnyImport() = WbSunnyIcon\n\n',
     '',
     "remove WbSunny workaround block"),
    ("app/src/main/java/com/example/mealplanner/ui/screens/RecipeListScreen.kt",
     'import androidx.compose.foundation.background\n',
     'import androidx.compose.foundation.ExperimentalFoundationApi\nimport androidx.compose.foundation.background\n',
     "import ExperimentalFoundationApi"),
    ("app/src/main/java/com/example/mealplanner/ui/screens/RecipeListScreen.kt",
     '    var pendingImport by remember { mutableStateOf<JsonBackupResult.Success?>(null) }\n'
     '    var importProfile by remember { mutableStateOf(false) }\n\n',
     '',
     "remove late pendingImport declaration"),
    ("app/src/main/java/com/example/mealplanner/ui/screens/RecipeListScreen.kt",
     '    val exportLauncher = rememberLauncherForActivityResult(\n'
     '        ActivityResultContracts.CreateDocument("application/json")\n',
     '    var pendingImport by remember { mutableStateOf<JsonBackupResult.Success?>(null) }\n'
     '    var importProfile by remember { mutableStateOf(false) }\n\n'
     '    val exportLauncher = rememberLauncherForActivityResult(\n'
     '        ActivityResultContracts.CreateDocument("application/json")\n',
     "declare pendingImport before launchers"),
    ("app/src/main/java/com/example/mealplanner/ui/screens/RecipeDetailScreen.kt",
     '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun RecipeDetailScreen(',
     '@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)\n@Composable\nfun RecipeDetailScreen(',
     "OptIn ExperimentalLayoutApi on detail screen"),
    ("app/src/main/java/com/example/mealplanner/ui/screens/RecipeEditorScreen.kt",
     'import androidx.compose.foundation.layout.height\n',
     'import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.navigationBarsPadding\n',
     "import navigationBarsPadding"),
    ("app/src/main/java/com/example/mealplanner/ui/screens/RecipeEditorScreen.kt",
     '.androidx.compose.foundation.layout.NavigationBarsPadding(),',
     '.navigationBarsPadding(),',
     "fix editor bottom-bar modifier chain"),
    ("app/build.gradle.kts",
     '    testImplementation("org.robolectric:robolectric:4.13")\n',
     '    testImplementation("org.robolectric:robolectric:4.13")\n    testImplementation("androidx.test:core-ktx:1.6.1")\n',
     "add androidx.test:core-ktx for Robolectric ApplicationProvider"),
]

failures = []
for path, old, new, desc in FIXES:
    p = pathlib.Path(path)
    if not p.exists():
        failures.append(f"MISSING FILE: {path} ({desc})"); continue
    text = p.read_text(encoding="utf-8")
    if new == "":
        if old not in text:
            print(f"  already applied: {desc}"); continue
    else:
        if new in text:
            print(f"  already applied: {desc}"); continue
    count = text.count(old)
    if count != 1:
        failures.append(f"expected 1 occurrence, found {count}: {desc} ({path})"); continue
    p.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"  fixed: {desc}")

if failures:
    print("\nSome fixes FAILED — inspect fix_integration.py and apply manually:")
    for f in failures:
        print("  - " + f)
    sys.exit(1)
print("All integration fixes applied.")
