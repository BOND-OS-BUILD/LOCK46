package com.lock46.app.enforce

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Foreground-app detection, built on UsageStats.
 *
 * This is LOCK46's only detection mechanism, and that is a deliberate choice rather than a
 * limitation of the code. An AccessibilityService would report a window change instantly,
 * but Google Play Protect hard-blocks the installation of any sideloaded app whose manifest
 * declares BIND_ACCESSIBILITY_SERVICE — with no "install anyway" option. An app nobody can
 * install enforces nothing, so LOCK46 polls instead and accepts sub-second latency.
 *
 * Polling only runs while a duty period is active; in Free Mode nothing here executes.
 */
class ForegroundAppMonitor(context: Context) {

    private val usageStats: UsageStatsManager? =
        runCatching { context.getSystemService(UsageStatsManager::class.java) }.getOrNull()

    /** Last package reported, so callers can cheaply detect a change. */
    @Volatile
    var lastKnownPackage: String? = null
        private set

    /**
     * The package that most recently came to the foreground within [lookbackMs].
     *
     * Returns null when usage access has not been granted, or when no foreground event has
     * occurred in the window — in which case the previously known package is still current
     * and callers should keep using [lastKnownPackage].
     */
    fun currentForegroundPackage(lookbackMs: Long = DEFAULT_LOOKBACK_MS): String? {
        val manager = usageStats ?: return null
        val now = System.currentTimeMillis()
        val events = runCatching { manager.queryEvents(now - lookbackMs, now) }.getOrNull()
            ?: return null

        var latestPackage: String? = null
        var latestTime = Long.MIN_VALUE
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // ACTIVITY_RESUMED is the current name for the event once called
            // MOVE_TO_FOREGROUND; both are constant 1, so this covers every supported API.
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.timeStamp >= latestTime) {
                    latestTime = event.timeStamp
                    latestPackage = event.packageName
                }
            }
        }

        if (latestPackage != null) lastKnownPackage = latestPackage
        return latestPackage
    }

    /**
     * Best-known current foreground package: the newest event in the window, or the last
     * one seen if the window was quiet (the user is still sitting in the same app).
     */
    fun resolveForegroundPackage(): String? =
        currentForegroundPackage() ?: lastKnownPackage

    fun reset() {
        lastKnownPackage = null
    }

    companion object {
        /**
         * Window queried on each poll. Wide enough to survive a missed tick or a device that
         * batches events, narrow enough that the query stays cheap at this frequency.
         */
        private const val DEFAULT_LOOKBACK_MS = 10_000L
    }
}
