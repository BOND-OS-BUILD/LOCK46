package com.lock46.app.util

import com.lock46.app.core.DutyDuration
import org.junit.Assert.assertEquals
import org.junit.Test

/** Countdown formatting, including the edges the UI would otherwise show badly. */
class TimeFormatTest {

    @Test
    fun `countdown is zero-padded hours minutes seconds`() {
        assertEquals("00:00:00", TimeFormat.countdown(0))
        assertEquals("00:00:01", TimeFormat.countdown(1_000))
        assertEquals("00:01:00", TimeFormat.countdown(DutyDuration.MINUTE_MS))
        assertEquals("01:00:00", TimeFormat.countdown(DutyDuration.HOUR_MS))
        assertEquals("05:42:18", TimeFormat.countdown(5 * 3_600_000L + 42 * 60_000L + 18_000L))
    }

    @Test
    fun `countdown truncates sub-second remainders rather than rounding up`() {
        assertEquals("00:00:01", TimeFormat.countdown(1_999))
    }

    @Test
    fun `countdown never shows a negative value`() {
        assertEquals("00:00:00", TimeFormat.countdown(-1))
        assertEquals("00:00:00", TimeFormat.countdown(-DutyDuration.HOUR_MS))
    }

    @Test
    fun `countdown handles the maximum duty period`() {
        assertEquals("24:00:00", TimeFormat.countdown(DutyDuration.MAX_MS))
    }

    @Test
    fun `compact form suits the notification`() {
        assertEquals("5h 42m", TimeFormat.compact(5 * 3_600_000L + 42 * 60_000L))
        assertEquals("42m", TimeFormat.compact(42 * 60_000L))
        assertEquals("1h 0m", TimeFormat.compact(DutyDuration.HOUR_MS))
        assertEquals("<1m", TimeFormat.compact(30_000L))
        assertEquals("<1m", TimeFormat.compact(0L))
        assertEquals("<1m", TimeFormat.compact(-5L))
    }
}
