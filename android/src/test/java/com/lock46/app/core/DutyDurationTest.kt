package com.lock46.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Duration validation — the guard that stops a bad value ever reaching duty state. */
class DutyDurationTest {

    @Test
    fun `the five required presets are offered in order`() {
        assertEquals(
            listOf(
                30 * DutyDuration.MINUTE_MS,
                DutyDuration.HOUR_MS,
                2 * DutyDuration.HOUR_MS,
                4 * DutyDuration.HOUR_MS,
                8 * DutyDuration.HOUR_MS
            ),
            DutyDuration.PRESETS_MS
        )
    }

    @Test
    fun `every preset is valid`() {
        DutyDuration.PRESETS_MS.forEach { assertTrue(DutyDuration.isValid(it)) }
    }

    @Test
    fun `zero and negative durations are rejected`() {
        assertFalse(DutyDuration.isValid(0L))
        assertFalse(DutyDuration.isValid(-1L))
        assertFalse(DutyDuration.isValid(Long.MIN_VALUE))
    }

    @Test
    fun `durations beyond the daily cap are rejected`() {
        assertTrue(DutyDuration.isValid(DutyDuration.MAX_MS))
        assertFalse(DutyDuration.isValid(DutyDuration.MAX_MS + 1))
        assertFalse(DutyDuration.isValid(Long.MAX_VALUE))
    }

    @Test
    fun `sub-minute durations are rejected`() {
        assertFalse(DutyDuration.isValid(DutyDuration.MIN_MS - 1))
        assertTrue(DutyDuration.isValid(DutyDuration.MIN_MS))
    }

    @Test
    fun `custom entry builds a duration`() {
        assertEquals(
            2 * DutyDuration.HOUR_MS + 30 * DutyDuration.MINUTE_MS,
            DutyDuration.fromHoursMinutes(2, 30)
        )
    }

    @Test
    fun `custom entry rejects nonsense rather than clamping it`() {
        assertNull(DutyDuration.fromHoursMinutes(0, 0))
        assertNull(DutyDuration.fromHoursMinutes(-1, 30))
        assertNull(DutyDuration.fromHoursMinutes(1, -5))
        assertNull(DutyDuration.fromHoursMinutes(1, 60))
        assertNull(DutyDuration.fromHoursMinutes(25, 0))
        assertNull(DutyDuration.fromHoursMinutes(24, 1))
    }

    @Test
    fun `the repository refuses an invalid duration`() {
        val duty = DutyRepository(InMemoryKeyValueStore()) { 1_700_000_000_000L }

        listOf(0L, -1L, DutyDuration.MAX_MS + 1, DutyDuration.MIN_MS - 1).forEach { bad ->
            var threw = false
            try {
                duty.start(bad)
            } catch (_: IllegalArgumentException) {
                threw = true
            }
            assertTrue("start($bad) should have been rejected", threw)
        }
        assertFalse(duty.isOnDuty)
    }

    @Test
    fun `labels read the way the ui shows them`() {
        assertEquals("30 min", DutyDuration.label(30 * DutyDuration.MINUTE_MS))
        assertEquals("1 h", DutyDuration.label(DutyDuration.HOUR_MS))
        assertEquals("8 h", DutyDuration.label(8 * DutyDuration.HOUR_MS))
        assertEquals("2 h 30 min", DutyDuration.label(150 * DutyDuration.MINUTE_MS))
    }
}
