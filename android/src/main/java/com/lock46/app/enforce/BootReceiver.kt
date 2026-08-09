package com.lock46.app.enforce

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lock46.app.Graph

/**
 * Restores duty state after a reboot.
 *
 * Both LOCKED_BOOT_COMPLETED and BOOT_COMPLETED are handled: duty state lives in
 * device-protected storage, so enforcement can come back before the first unlock, which is
 * exactly the window someone would use to slip past it.
 *
 * TIME_SET / TIMEZONE_CHANGED are also handled so a clock change is reconciled promptly
 * rather than at the next tick.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            ACTION_QUICKBOOT_POWERON,
            ACTION_HTC_QUICKBOOT_POWERON,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Graph.ensure(context)
                DutyController.restore(context)
            }

            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Graph.ensure(context)
                // refresh() applies clock reconciliation and ends duty if it has elapsed.
                Graph.duty.refresh()
                DutyController.restore(context)
            }
        }
    }

    private companion object {
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
