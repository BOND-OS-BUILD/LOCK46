package com.lock46.app.core

import android.content.Context
import android.content.SharedPreferences

/**
 * [KeyValueStore] backed by SharedPreferences.
 *
 * Writes are committed synchronously. That is deliberate: duty state is read by a boot
 * receiver and by a service that can be killed at any time, and an `apply()` that had not
 * yet flushed would silently drop the user back into Free Mode.
 *
 * Device-protected storage is used so the record is readable after LOCKED_BOOT_COMPLETED,
 * before the user has unlocked the device for the first time.
 */
class SharedPreferencesStore(context: Context) : KeyValueStore {

    private val prefs: SharedPreferences =
        context.applicationContext
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "lock46_state"
    }

    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun getStringSet(key: String): Set<String> =
        prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).commit()
    }

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).commit()
    }

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).commit()
    }

    override fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).commit()
    }

    override fun putStringSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, LinkedHashSet(value)).commit()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).commit()
    }
}
