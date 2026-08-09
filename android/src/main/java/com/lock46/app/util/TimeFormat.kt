package com.lock46.app.util

import java.util.Locale
import java.util.concurrent.TimeUnit

/** Formatting helpers for the countdown and end-of-duty time. Pure and unit-tested. */
object TimeFormat {

    /** `HH:MM:SS`, always zero-padded, never negative. Used for the large countdown. */
    fun countdown(remainingMs: Long): String {
        val clamped = remainingMs.coerceAtLeast(0L)
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(clamped)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    /** Compact form for the ongoing notification, e.g. "5h 42m" / "42m" / "<1m". */
    fun compact(remainingMs: Long): String {
        val clamped = remainingMs.coerceAtLeast(0L)
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(clamped)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            totalMinutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
}
