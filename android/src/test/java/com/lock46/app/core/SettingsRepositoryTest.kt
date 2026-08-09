package com.lock46.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun `setup starts incomplete and persists once finished`() {
        val store = InMemoryKeyValueStore()
        val settings = SettingsRepository(store)

        assertFalse(settings.isSetupComplete)
        settings.isSetupComplete = true

        assertTrue(SettingsRepository(InMemoryKeyValueStore(store.snapshot())).isSetupComplete)
    }

    @Test
    fun `default duration falls back to one hour`() {
        assertEquals(
            DutyDuration.DEFAULT_MS,
            SettingsRepository(InMemoryKeyValueStore()).defaultDurationMs
        )
    }

    @Test
    fun `a valid default duration is stored`() {
        val settings = SettingsRepository(InMemoryKeyValueStore())
        settings.defaultDurationMs = 4 * DutyDuration.HOUR_MS
        assertEquals(4 * DutyDuration.HOUR_MS, settings.defaultDurationMs)
    }

    @Test
    fun `an invalid default duration is ignored rather than stored`() {
        val settings = SettingsRepository(InMemoryKeyValueStore())
        settings.defaultDurationMs = 2 * DutyDuration.HOUR_MS
        settings.defaultDurationMs = -1

        assertEquals(2 * DutyDuration.HOUR_MS, settings.defaultDurationMs)
    }

    @Test
    fun `a corrupted stored duration falls back to the default`() {
        val store = InMemoryKeyValueStore()
        store.putLong("settings.default_duration", 999 * DutyDuration.HOUR_MS)

        assertEquals(DutyDuration.DEFAULT_MS, SettingsRepository(store).defaultDurationMs)
    }
}
