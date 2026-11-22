package com.example.mealplanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Recipe::class, Ingredient::class, MealPlan::class, Budget::class,
        DietaryProfile::class, Purchase::class],
    version = 8,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MealPlannerDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun budgetDao(): BudgetDao
    abstract fun dietaryProfileDao(): DietaryProfileDao
    abstract fun purchaseDao(): PurchaseDao

    /** Seeds sample data only when the database file is first created. */
    private class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            db.execSQL(
                """INSERT INTO recipes (name, category, prepTimeMinutes, cookTimeMinutes, servings, instructions, diets)
                   VALUES ('Fluffy Pancakes', 'Breakfast', 10, 15, 4,
                   '1. Whisk the dry ingredients. 2. Whisk in milk, eggs and melted butter. 3. Cook on a greased pan until golden on both sides.',
                   'Vegetarian')"""
            )
            db.execSQL(
                """INSERT INTO recipes (name, category, prepTimeMinutes, cookTimeMinutes, servings, instructions, diets)
                   VALUES ('Chicken Caesar Salad', 'Lunch', 20, 12, 2,
                   '1. Season and cook the chicken. 2. Chop the lettuce. 3. Toss with dressing, croutons and parmesan. 4. Slice the chicken on top.',
                   '')"""
            )
            db.execSQL(
                """INSERT INTO recipes (name, category, prepTimeMinutes, cookTimeMinutes, servings, instructions, diets)
                   VALUES ('Spaghetti Bolognese', 'Dinner', 15, 40, 4,
                   '1. Fry onion and garlic in olive oil. 2. Add beef and brown. 3. Add tomato sauce and simmer 25 minutes. 4. Serve over spaghetti with parmesan.',
                   '')"""
            )
            db.execSQL(
                """INSERT INTO recipes (name, category, prepTimeMinutes, cookTimeMinutes, servings, instructions, diets)
                   VALUES ('Overnight Oats', 'Breakfast', 10, 0, 1,
                   '1. Stir all ingredients together in a jar. 2. Refrigerate overnight. 3. Add extra milk before serving if needed.',
                   'Vegetarian')"""
            )

            fun ingredientsFor(recipe: String, vararg rows: String) = rows.forEach { row ->
                db.execSQL(
                    """INSERT INTO ingredients (recipeId, name, quantity, unit, allergens)
                       SELECT id, $row FROM recipes WHERE name = '$recipe'"""
                )
            }
            ingredientsFor(
                "Fluffy Pancakes",
                "'Flour', 2, 'cups', 'Gluten'", "'Milk', 1.5, 'cups', 'Dairy'",
                "'Eggs', 2, 'pcs', 'Eggs'", "'Sugar', 2, 'tbsp', ''",
                "'Baking powder', 1, 'tbsp', ''", "'Butter', 2, 'tbsp', 'Dairy'",
                "'Salt', 0.5, 'tsp', ''"
            )
            ingredientsFor(
                "Chicken Caesar Salad",
                "'Chicken breast', 2, 'pcs', ''", "'Romaine lettuce', 1, 'head', ''",
                "'Caesar dressing', 4, 'tbsp', 'Dairy,Eggs'", "'Parmesan', 0.5, 'cups', 'Dairy'",
                "'Croutons', 1, 'cup', 'Gluten'", "'Olive oil', 1, 'tbsp', ''"
            )
            ingredientsFor(
                "Spaghetti Bolognese",
                "'Spaghetti', 400, 'g', 'Gluten'", "'Ground beef', 500, 'g', ''",
                "'Tomato sauce', 500, 'g', ''", "'Onion', 1, 'pc', ''",
                "'Garlic', 3, 'cloves', ''", "'Olive oil', 2, 'tbsp', ''",
                "'Parmesan', 0.5, 'cups', 'Dairy'"
            )
            ingredientsFor(
                "Overnight Oats",
                "'Rolled oats', 0.5, 'cups', 'Gluten'", "'Milk', 0.5, 'cups', 'Dairy'",
                "'Greek yogurt', 2, 'tbsp', 'Dairy'", "'Honey', 1, 'tbsp', ''",
                "'Blueberries', 0.25, 'cups', ''", "'Chia seeds', 1, 'tbsp', ''"
            )

            fun plan(recipe: String, dayOffset: String, mealType: String) = db.execSQL(
                """INSERT INTO meal_plans (date, mealType, recipeId, servings, sortOrder)
                   SELECT date('now', '$dayOffset', 'localtime'), '$mealType', id, servings, 0
                   FROM recipes WHERE name = '$recipe'"""
            )
            plan("Fluffy Pancakes", "", "Breakfast")          // today
            plan("Spaghetti Bolognese", "", "Dinner")         // today
            plan("Chicken Caesar Salad", "+1 day", "Lunch")   // tomorrow
        }
    }

    companion object {
        @Volatile private var INSTANCE: MealPlannerDatabase? = null

        // ---- migration chain v1 → v8 (used when upgrading existing installs) ----

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN photoPath TEXT")
                db.execSQL("ALTER TABLE meal_plans ADD COLUMN servings INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE meal_plans ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ingredients ADD COLUMN price REAL NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS budgets " +
                        "(date TEXT NOT NULL PRIMARY KEY, amount REAL NOT NULL)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ingredients ADD COLUMN allergens TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE recipes ADD COLUMN diets TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS dietary_profile " +
                        "(id INTEGER NOT NULL PRIMARY KEY, allergies TEXT NOT NULL, diets TEXT NOT NULL)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN adaptedNote TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE dietary_profile ADD COLUMN hideClashing INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS purchases (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL, name TEXT NOT NULL, unit TEXT NOT NULL,
                        quantity REAL NOT NULL, estimatedPrice REAL NOT NULL, actualPrice REAL NOT NULL)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_purchases_date ON purchases(date)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE meal_plans ADD COLUMN cookedAt TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): MealPlannerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MealPlannerDatabase::class.java,
                    "meal_planner.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
                    )
                    .addCallback(SeedCallback())
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
