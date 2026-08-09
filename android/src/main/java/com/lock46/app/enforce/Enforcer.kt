package com.lock46.app.enforce

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.lock46.app.Graph
import com.lock46.app.core.AccessPolicy
import com.lock46.app.core.DutyStatus
import com.lock46.app.util.InstalledApps

/**
 * Single decision point for enforcement.
 *
 * Every foreground signal funnels into [onForegroundPackage] — the poller in
 * [DutyService], plus the direct calls made when the whitelist changes or the app is
 * resumed — so the allow/block rule lives in exactly one place.
 */
object Enforcer {

    private lateinit var appContext: Context
    private var overlay: BlockOverlay? = null
    private val main = Handler(Looper.getMainLooper())

    /**
     * Cached always-allowed packages. Recomputed when duty starts rather than on every
     * event: resolving the launcher, keyboard and dialer hits PackageManager, and these
     * only change when the user changes a system default.
     */
    @Volatile
    private var alwaysAllowed: AccessPolicy.AlwaysAllowed? = null

    /** Last package we acted on, to avoid re-adding the overlay on every repeated event. */
    @Volatile
    private var lastHandledPackage: String? = null

    @Synchronized
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        Graph.ensure(appContext)
    }

    fun refreshAlwaysAllowed() {
        if (!::appContext.isInitialized) return
        alwaysAllowed = InstalledApps.resolveAlwaysAllowed(appContext)
    }

    private fun alwaysAllowedOrResolve(): AccessPolicy.AlwaysAllowed {
        alwaysAllowed?.let { return it }
        val resolved = InstalledApps.resolveAlwaysAllowed(appContext)
        alwaysAllowed = resolved
        return resolved
    }

    fun remainingMs(): Long =
        if (::appContext.isInitialized) Graph.duty.remainingMs() else 0L

    /**
     * Handles a foreground-app signal.
     *
     * Safe to call from any thread and at any frequency; overlay mutations are marshalled
     * to the main thread and repeated events for the same package are collapsed.
     */
    fun onForegroundPackage(packageName: String?) {
        if (!::appContext.isInitialized) return

        val status = Graph.duty.refresh()
        if (status !is DutyStatus.OnDuty) {
            lastHandledPackage = null
            main.post { overlay?.hide() }
            return
        }

        val blocked = AccessPolicy.shouldBlock(
            packageName = packageName,
            onDuty = true,
            whitelist = Graph.whitelist.get(),
            alwaysAllowed = alwaysAllowedOrResolve()
        )

        if (!blocked) {
            if (overlay?.isShowing == true) main.post { overlay?.hide() }
            lastHandledPackage = packageName
            return
        }

        if (lastHandledPackage == packageName && overlay?.isShowing == true) return
        lastHandledPackage = packageName

        val label = InstalledApps.labelFor(appContext, packageName.orEmpty())
        main.post {
            val target = overlay ?: BlockOverlay(appContext).also { overlay = it }
            val shown = target.show(packageName.orEmpty(), label, status.remainingMs)
            if (!shown) {
                // No overlay permission: fall back to bouncing the user home. Weaker, and
                // the README says so, but it still prevents casual use of a blocked app.
                goHome()
            }
        }
    }

    /**
     * Sends the user to the home screen.
     *
     * Holding SYSTEM_ALERT_WINDOW is one of Android's documented exemptions from the
     * background-activity-start restrictions, which is what makes this work from a service
     * with no visible Activity — the same permission that lets the overlay be drawn.
     */
    fun goHome() {
        if (!::appContext.isInitialized) return
        runCatching {
            appContext.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Tears down any visible blocking UI — called when duty ends for any reason. */
    fun clear() {
        lastHandledPackage = null
        main.post { overlay?.hide() }
    }
}
