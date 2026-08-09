package com.lock46.app.enforce

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.lock46.app.Graph
import com.lock46.app.core.DutySession

/**
 * Orchestrates everything that has to happen when duty starts, ends, or is restored.
 *
 * Kept separate from [DutyService] so the UI, the boot receiver and the expiry alarm all
 * go through the same path — the service is one of the things being controlled, not the
 * controller.
 */
object DutyController {

    /**
     * Starts a duty period.
     * @throws IllegalArgumentException for an out-of-range duration.
     */
    fun start(context: Context, durationMs: Long): DutySession {
        Graph.ensure(context)
        val session = Graph.duty.start(durationMs)
        Enforcer.init(context)
        Enforcer.refreshAlwaysAllowed()
        scheduleExpiryAlarm(context, session.endsAtWallMs)
        DutyService.start(context)
        return session
    }

    /**
     * Ends the duty period. Authorisation is the caller's responsibility — the UI requires
     * the admin PIN before reaching this point.
     */
    fun end(context: Context) {
        Graph.ensure(context)
        Graph.duty.end()
        cancelExpiryAlarm(context)
        Enforcer.clear()
        DutyService.stop(context)
    }

    /**
     * Re-establishes enforcement from persisted state — used after boot and after the app
     * process is recreated. Ends duty cleanly if the period already elapsed while the
     * device was off.
     */
    fun restore(context: Context) {
        Graph.ensure(context)
        Enforcer.init(context)
        val session = Graph.duty.refresh()
        if (session is com.lock46.app.core.DutyStatus.OnDuty) {
            Enforcer.refreshAlwaysAllowed()
            scheduleExpiryAlarm(context, session.session.endsAtWallMs)
            DutyService.start(context)
        } else {
            cancelExpiryAlarm(context)
            Enforcer.clear()
            DutyService.stop(context)
        }
    }

    /**
     * Backstop alarm so duty still ends if the service is killed and never restarted.
     *
     * Deliberately inexact: an exact alarm would require SCHEDULE_EXACT_ALARM, which is not
     * justified here — the running service already ends duty on time, and this alarm only
     * has to fire eventually.
     */
    private fun scheduleExpiryAlarm(context: Context, endsAtWallMs: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            endsAtWallMs,
            expiryPendingIntent(context)
        )
    }

    private fun cancelExpiryAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(expiryPendingIntent(context))
    }

    private fun expiryPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_EXPIRY,
            Intent(context, DutyExpiryReceiver::class.java)
                .setAction(DutyExpiryReceiver.ACTION_DUTY_EXPIRED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    internal fun startServiceCompat(context: Context, intent: Intent) {
        ContextCompat.startForegroundService(context, intent)
    }

    private const val REQUEST_EXPIRY = 4601
}
