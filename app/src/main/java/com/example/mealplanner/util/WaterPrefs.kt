package com.example.mealplanner.util

import android.content.Context

/**
 * Daily hydration log in SharedPreferences — a per-day scalar with no
 * relations or query needs, so it doesn't justify a Room table.
 */
object WaterPrefs {

    const val WATER_GOAL = 8

    private const val FILE = "water"
    private const val PREFIX = "water_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun get(context: Context, date: String): Int = prefs(context).getInt(PREFIX + date, 0)

    fun set(context: Context, date: String, glasses: Int) {
        prefs(context).edit().putInt(PREFIX + date, glasses.coerceAtLeast(0)).apply()
    }

    /** All logged days — used by JSON export/import. */
    fun all(context: Context): Map<String, Int> =
        prefs(context).all.entries
            .filter { it.key.startsWith(PREFIX) }
            .mapNotNull { (k, v) -> (v as? Int)?.let { k.removePrefix(PREFIX) to it } }
            .toMap()
}
