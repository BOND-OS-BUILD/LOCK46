package com.lock46.app.enforce

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.lock46.app.R
import com.lock46.app.util.TimeFormat

/**
 * The LOCK46 blocking screen, drawn as a system overlay on top of a blocked app.
 *
 * An overlay is used rather than starting an Activity because Android 10+ restricts
 * background activity starts; holding SYSTEM_ALERT_WINDOW is one of the documented ways
 * to reliably put UI in front of the user from a service. Deliberately, there is no
 * "disable LOCK46" affordance on this screen — the only exit is Home, or the PIN-protected
 * End Duty action inside the app.
 *
 * The overlay is also what makes sub-second polling acceptable: it covers the blocked app
 * the moment the poll fires, so the user sees LOCK46 rather than a usable app.
 */
class BlockOverlay(private val context: Context) {

    private val windowManager =
        context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var root: View? = null
    private var remainingView: TextView? = null
    private var blockedLabelView: TextView? = null
    private var ticking = false

    /** Package currently being blocked, or null when the overlay is not shown. */
    var blockedPackage: String? = null
        private set

    val isShowing: Boolean get() = root != null

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    /**
     * Shows (or retargets) the overlay.
     * @return false when the overlay permission is missing, so the caller can fall back.
     */
    fun show(packageName: String, appLabel: String, remainingMs: Long): Boolean {
        if (!canShow()) return false
        blockedPackage = packageName

        if (root == null) {
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_block, null)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.OPAQUE
            )

            view.isFocusableInTouchMode = true
            view.setOnKeyListener { _, keyCode, _ ->
                // Swallow BACK so the blocking screen cannot be dismissed by reflex.
                keyCode == KeyEvent.KEYCODE_BACK
            }
            view.findViewById<Button>(R.id.overlay_return_home).setOnClickListener {
                goHome()
            }

            remainingView = view.findViewById(R.id.overlay_remaining)
            blockedLabelView = view.findViewById(R.id.overlay_blocked_app)

            runCatching { windowManager.addView(view, params) }
                .onFailure { return false }

            root = view
            view.requestFocus()
        }

        blockedLabelView?.text = context.getString(R.string.overlay_blocked_app, appLabel)
        updateRemaining(remainingMs)
        startTicking()
        return true
    }

    fun updateRemaining(remainingMs: Long) {
        remainingView?.text = context.getString(
            R.string.overlay_remaining,
            TimeFormat.countdown(remainingMs)
        )
    }

    fun hide() {
        stopTicking()
        root?.let { view ->
            runCatching { windowManager.removeViewImmediate(view) }
        }
        root = null
        remainingView = null
        blockedLabelView = null
        blockedPackage = null
    }

    private fun goHome() {
        hide()
        Enforcer.goHome()
    }

    private fun startTicking() {
        if (ticking) return
        ticking = true
        handler.post(tick)
    }

    private fun stopTicking() {
        ticking = false
        handler.removeCallbacks(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!ticking) return
            updateRemaining(Enforcer.remainingMs())
            handler.postDelayed(this, 1_000L)
        }
    }
}
