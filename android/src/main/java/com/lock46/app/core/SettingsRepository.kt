package com.lock46.app.core

/** Non-sensitive app preferences: onboarding progress and the default duty duration. */
class SettingsRepository(private val store: KeyValueStore) {

    companion object {
        private const val KEY_SETUP_COMPLETE = "setup.complete"
        private const val KEY_DEFAULT_DURATION = "settings.default_duration"
    }

    var isSetupComplete: Boolean
        get() = store.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) = store.putBoolean(KEY_SETUP_COMPLETE, value)

    var defaultDurationMs: Long
        get() {
            val stored = store.getLong(KEY_DEFAULT_DURATION, DutyDuration.DEFAULT_MS)
            return if (DutyDuration.isValid(stored)) stored else DutyDuration.DEFAULT_MS
        }
        set(value) {
            if (DutyDuration.isValid(value)) store.putLong(KEY_DEFAULT_DURATION, value)
        }
}
