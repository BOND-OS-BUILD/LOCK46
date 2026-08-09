package com.lock46.app.enforce

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lock46.app.Graph
import com.lock46.app.core.DutyStatus

/**
 * Backstop for duty expiry.
 *
 * The running service normally ends duty on the tick. This receiver covers the case where
 * the service was killed and not restarted: it re-evaluates persisted state and tears
 * enforcement down, so a dead process can never leave the phone locked down indefinitely.
 */
class DutyExpiryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DUTY_EXPIRED) return
        Graph.ensure(context)

        when (Graph.duty.refresh()) {
            is DutyStatus.Free -> DutyController.end(context)
            // Woke early (inexact alarm): re-arm by restoring, which reschedules.
            is DutyStatus.OnDuty -> DutyController.restore(context)
        }
    }

    companion object {
        const val ACTION_DUTY_EXPIRED = "com.lock46.app.action.DUTY_EXPIRED"
    }
}
