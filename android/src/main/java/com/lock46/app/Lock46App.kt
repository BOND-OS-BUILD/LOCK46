package com.lock46.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.lock46.app.core.DutyRepository
import com.lock46.app.core.KeyValueStore
import com.lock46.app.core.PinRepository
import com.lock46.app.core.SettingsRepository
import com.lock46.app.core.SharedPreferencesStore
import com.lock46.app.core.WhitelistRepository

/**
 * Application entry point and the app's single composition root.
 *
 * A hand-rolled container is used instead of a DI framework: LOCK46 V1 has five
 * singletons, and every dependency it needs must also be reachable from a boot receiver
 * and an accessibility service, both of which are created by the system.
 */
class Lock46App : Application() {

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            DUTY_CHANNEL_ID,
            "Duty Mode",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows that Duty Mode is active and how much time remains."
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val DUTY_CHANNEL_ID = "lock46.duty"
    }
}

/**
 * Process-wide singletons.
 *
 * [init] is idempotent and is called from every system entry point (Application,
 * BootReceiver, the accessibility service) because a receiver can be constructed in a
 * process where Application.onCreate has not run to completion.
 */
object Graph {

    @Volatile
    private var store: KeyValueStore? = null

    lateinit var duty: DutyRepository
        private set
    lateinit var whitelist: WhitelistRepository
        private set
    lateinit var pin: PinRepository
        private set
    lateinit var settings: SettingsRepository
        private set

    @Synchronized
    fun init(context: Context) {
        if (store != null) return
        val kv = SharedPreferencesStore(context.applicationContext)
        store = kv
        duty = DutyRepository(kv)
        whitelist = WhitelistRepository(kv)
        pin = PinRepository(kv)
        settings = SettingsRepository(kv)
    }

    /** Convenience for system-created components. */
    fun ensure(context: Context) = init(context)
}
