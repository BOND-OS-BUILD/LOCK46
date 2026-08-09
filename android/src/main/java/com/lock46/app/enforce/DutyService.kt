package com.lock46.app.enforce

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.lock46.app.Graph
import com.lock46.app.Lock46App
import com.lock46.app.R
import com.lock46.app.core.DutyStatus
import com.lock46.app.ui.MainActivity
import com.lock46.app.util.Permissions
import com.lock46.app.util.TimeFormat

/**
 * Foreground service that keeps the app alive for the length of a duty period.
 *
 * It does three things: hold a visible ongoing notification (Android's price for staying
 * alive), tick the clock so duty ends exactly on time, and — the important one — poll for
 * the foreground app so a blocked app is caught the moment it opens.
 *
 * The poll runs at [DETECTION_TICK_MS] while on duty and stops entirely in Free Mode.
 */
class DutyService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var foregroundMonitor: ForegroundAppMonitor
    private var lastNotificationText: String? = null
    private var lastSecondStamp = 0L
    private var started = false

    override fun onCreate() {
        super.onCreate()
        Graph.ensure(applicationContext)
        Enforcer.init(applicationContext)
        foregroundMonitor = ForegroundAppMonitor(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground before anything else: Android kills a service that does not
        // call startForeground quickly after startForegroundService.
        goForeground(initialNotification())

        if (intent?.action == ACTION_STOP) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        val status = Graph.duty.refresh()
        if (status !is DutyStatus.OnDuty) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        // Guard against duplicate tickers if the service is started more than once.
        if (!started) {
            started = true
            handler.post(ticker)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        started = false
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away must not end duty; the service keeps running.
        super.onTaskRemoved(rootIntent)
    }

    private val ticker = object : Runnable {
        override fun run() {
            val status = Graph.duty.refresh()
            if (status !is DutyStatus.OnDuty) {
                DutyController.end(applicationContext)
                stopSelfCleanly()
                return
            }

            // Detection runs on every tick — this is the enforcement path, not a backstop.
            if (Permissions.hasUsageAccess(applicationContext)) {
                Enforcer.onForegroundPackage(foregroundMonitor.resolveForegroundPackage())
            }

            // The notification only needs refreshing once a second, not on every poll.
            val nowSecond = System.currentTimeMillis() / 1_000
            if (nowSecond != lastSecondStamp) {
                lastSecondStamp = nowSecond
                updateNotification(status.remainingMs)
            }

            handler.postDelayed(this, DETECTION_TICK_MS)
        }
    }

    private fun stopSelfCleanly() {
        handler.removeCallbacks(ticker)
        started = false
        Enforcer.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initialNotification(): Notification =
        buildNotification(getString(R.string.notification_starting))

    private fun updateNotification(remainingMs: Long) {
        val text = getString(R.string.notification_remaining, TimeFormat.compact(remainingMs))
        if (text == lastNotificationText) return
        lastNotificationText = text
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Lock46App.DUTY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_duty)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 4601

        /**
         * How often the foreground app is checked while on duty.
         *
         * 600 ms is the balance point found for this workload: fast enough that a blocked
         * app is covered before it is usable, slow enough that querying UsageStats does not
         * become a meaningful battery cost over an 8-hour duty period.
         */
        private const val DETECTION_TICK_MS = 600L

        const val ACTION_STOP = "com.lock46.app.action.STOP_DUTY_SERVICE"

        fun start(context: Context) {
            runCatching {
                DutyController.startServiceCompat(context, Intent(context, DutyService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, DutyService::class.java))
            }
        }
    }
}
