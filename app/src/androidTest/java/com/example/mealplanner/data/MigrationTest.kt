package com.example.mealplanner.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MealPlannerDatabase::class.java
    )

    @Test
    fun exportedCurrentSchemaIsValid() {
        // Builds a DB straight from the exported v8 JSON — catches schema drift early.
        helper.createDatabase(TEST_DB, 8).close()
    }

    // ── Template for the next bump — uncomment and adapt when version 9 exists ──
    // @Test
    // fun migrate8To9_preservesData() {
    //     helper.createDatabase(TEST_DB, 8).apply {
    //         execSQL("INSERT INTO recipes (name, servings) VALUES ('Old', 2)")
    //         close()
    //     }
    //     helper.runMigrationsAndValidate(TEST_DB, 9, true, MealPlannerDatabase.MIGRATION_8_9)
    //         .query("SELECT name FROM recipes").use { cursor ->
    //             assertTrue(cursor.moveToFirst())
    //             assertEquals("Old", cursor.getString(0))
    //         }
    // }
}
