package com.lock46.app.enforce

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.lock46.app.Graph

/**
 * Primary foreground-app signal.
 *
 * The service is configured with `canRetrieveWindowContent="false"` and reads nothing but
 * `event.packageName`. It never inspects text, never captures the screen, and never
 * touches call, message or contact data — the only thing it needs to know is which app is
 * in front.
 */
class Lock46AccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Graph.ensure(applicationContext)
        Enforcer.init(applicationContext)
        Enforcer.refreshAlwaysAllowed()
        // A duty period may already be running (service restarted mid-duty), so evaluate
        // immediately rather than waiting for the next window change.
        Enforcer.onForegroundPackage(null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        Enforcer.onForegroundPackage(pkg)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: Lock46AccessibilityService? = null

        val isConnected: Boolean get() = instance != null

        /**
         * Sends the user to the home screen using the accessibility global action.
         * No-op when the service is not connected.
         */
        fun performGoHome() {
            runCatching { instance?.performGlobalAction(GLOBAL_ACTION_HOME) }
        }
    }
}
