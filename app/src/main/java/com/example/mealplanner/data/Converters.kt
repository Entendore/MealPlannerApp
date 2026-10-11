package com.example.mealplanner.data

import androidx.room.TypeConverter

/**
 * Tags are canonical single words (no commas) — the editor strips commas from
 * user input to preserve this invariant (pinned by ConverterCommaInvariantTest).
 */
class Converters {
    @TypeConverter fun fromList(list: List<String>): String = list.joinToString(",")
    @TypeConverter fun toList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(",")
}
