package com.lock46.app.enforce

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Backstop foreground-app detection built on UsageStats.
 *
 * The accessibility service is the primary signal because it is event-driven and fires the
 * instant a window changes. This poller exists for the case the system stops that service:
 * it is slower and coarser, but it means enforcement degrades rather than disappears.
 */
class ForegroundAppMonitor(context: Context) {

    private val usageStats: UsageStatsManager? =
        runCatching { context.getSystemService(UsageStatsManager::class.java) }.getOrNull()

    /**
     * The package that most recently moved to the foreground within [lookbackMs].
     * Returns null when usage access has not been granted or nothing changed.
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
            // ACTIVITY_RESUMED is the current name for the event formerly called
            // MOVE_TO_FOREGROUND; both are constant 1, so this covers every supported API.
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.timeStamp >= latestTime) {
                    latestTime = event.timeStamp
                    latestPackage = event.packageName
                }
            }
        }
        return latestPackage
    }

    companion object {
        private const val DEFAULT_LOOKBACK_MS = 10_000L
    }
}
