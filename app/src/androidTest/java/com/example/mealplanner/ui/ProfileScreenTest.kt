package com.example.mealplanner.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mealplanner.MealPlannerViewModel
import com.example.mealplanner.R
import com.example.mealplanner.data.DietaryProfile
import com.example.mealplanner.data.MealPlannerDatabase
import com.example.mealplanner.ui.screens.ProfileScreen
import com.example.mealplanner.util.ProfileQr
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {

    private lateinit var db: MealPlannerDatabase
    private lateinit var vm: MealPlannerViewModel

    @get:Rule val rule = createComposeRule()

    @Before fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, MealPlannerDatabase::class.java)
            .allowMainThreadQueries().build()
        vm = MealPlannerViewModel(app, db)
    }
    @After fun tearDown() = db.close()

    @Test
    fun allergyChipsPersistThroughTheViewModel() = runBlocking {
        rule.setContent {
            MaterialTheme {
                ProfileScreen(viewModel = vm, scanResult = null,
                    onScanConsumed = {}, onScanProfile = {})
            }
        }
        rule.onNodeWithText("Dairy").performClick()
        rule.waitUntil(5_000) {
            db.dietaryProfileDao().getProfileOnce()?.allergies?.contains("Dairy") == true
        }
        rule.onNodeWithText("Dairy").performClick()          // toggle off
        rule.waitUntil(5_000) {
            db.dietaryProfileDao().getProfileOnce()?.allergies?.contains("Dairy") == false
        }
    }

    @Test
    fun scannedProfilePayloadAsksThenReplaces() = runBlocking {
        val incoming = ProfileQr.encode(DietaryProfile(allergies = listOf("Nuts")))
        rule.setContent {
            MaterialTheme {
                ProfileScreen(viewModel = vm, scanResult = incoming,
                    onScanConsumed = {}, onScanProfile = {})
            }
        }
        rule.onNodeWithText(rule.activity.getString(R.string.import_profile_title))
            .assertExistsCompat()
        rule.onNodeWithText(rule.activity.getString(R.string.import_label)).performClick()
        rule.waitUntil(5_000) {
            db.dietaryProfileDao().getProfileOnce()?.allergies?.contains("Nuts") == true
        }
    }
}
