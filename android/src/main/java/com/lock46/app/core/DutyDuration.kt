package com.lock46.app.core

/**
 * Duty duration presets and validation.
 *
 * A duty period is bounded on both ends deliberately: a zero/negative duration would
 * produce an already-expired session, and an unbounded one would let a mis-tap lock the
 * device for days.
 */
object DutyDuration {

    const val MINUTE_MS: Long = 60_000L
    const val HOUR_MS: Long = 60 * MINUTE_MS

    /** Shortest duty period LOCK46 will accept. */
    const val MIN_MS: Long = 1 * MINUTE_MS

    /** Longest duty period LOCK46 will accept in V1. */
    const val MAX_MS: Long = 24 * HOUR_MS

    /** Presets offered on the Start Duty screen, in order. */
    val PRESETS_MS: List<Long> = listOf(
        30 * MINUTE_MS,
        1 * HOUR_MS,
        2 * HOUR_MS,
        4 * HOUR_MS,
        8 * HOUR_MS
    )

    /** Default preselected preset. */
    const val DEFAULT_MS: Long = 1 * HOUR_MS

    fun isValid(durationMs: Long): Boolean = durationMs in MIN_MS..MAX_MS

    /**
     * Builds a duration from a custom hours/minutes entry.
     * Returns null when the entry is out of range, so callers must handle it explicitly
     * rather than silently clamping the user's input.
     */
    fun fromHoursMinutes(hours: Int, minutes: Int): Long? {
        if (hours < 0 || minutes < 0 || minutes > 59) return null
        val ms = hours * HOUR_MS + minutes * MINUTE_MS
        return if (isValid(ms)) ms else null
    }

    /** Human label for a duration, e.g. "30 min", "1 h", "2 h 30 min". */
    fun label(durationMs: Long): String {
        val totalMinutes = durationMs / MINUTE_MS
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> "$h h $m min"
            h > 0 -> "$h h"
            else -> "$m min"
        }
    }
}
