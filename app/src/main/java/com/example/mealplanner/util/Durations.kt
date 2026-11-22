package com.example.mealplanner.util

/** Parses cook-step durations like "25 minutes", "1 hour 30 minutes", "90 sec". */
object Durations {

    private val REGEX = Regex(
        """(\d+)\s*(hours?|hrs?|h|minutes?|mins?|min|m|seconds?|secs?|s)\b""",
        RegexOption.IGNORE_CASE
    )

    /** Total seconds of all duration mentions in a step, or null. */
    fun parseSeconds(step: String): Long? {
        var total = 0L; var found = false
        REGEX.findAll(step).forEach { m ->
            val n = m.groupValues[1].toLongOrNull() ?: return@forEach
            val unit = m.groupValues[2].lowercase()
            total += when {
                unit.startsWith("h") -> n * 3600
                unit.startsWith("m") -> n * 60
                else -> n
            }
            found = true
        }
        return if (found && total > 0) total else null
    }
}
