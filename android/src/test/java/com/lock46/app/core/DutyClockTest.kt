package com.lock46.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Clock-change handling and remaining-time arithmetic. */
class DutyClockTest {

    private val t0 = 1_700_000_000_000L

    @Test
    fun `remaining never exceeds the planned duration`() {
        val session = DutyClock.start(t0, DutyDuration.HOUR_MS)
        // Clock dragged far into the past: without clamping this would report days left.
        val remaining = DutyClock.remainingMs(session, t0 - 10 * DutyDuration.HOUR_MS)
        assertEquals(DutyDuration.HOUR_MS, remaining)
    }

    @Test
    fun `remaining never goes negative`() {
        val session = DutyClock.start(t0, DutyDuration.HOUR_MS)
        assertEquals(0L, DutyClock.remainingMs(session, t0 + 10 * DutyDuration.HOUR_MS))
    }

    @Test
    fun `small backwards jitter is tolerated without shifting the session`() {
        val session = DutyClock.start(t0, DutyDuration.HOUR_MS)
        val lastSeen = t0 + DutyDuration.MINUTE_MS
        val now = lastSeen - 2_000L // 2s NTP correction, inside tolerance

        assertEquals(session, DutyClock.reconcile(session, lastSeen, now))
    }

    @Test
    fun `winding the clock back does not buy extra duty-free time`() {
        val duration = 2 * DutyDuration.HOUR_MS
        val session = DutyClock.start(t0, duration)

        // 30 minutes in, the user sets the clock back by 90 minutes.
        val lastSeen = t0 + 30 * DutyDuration.MINUTE_MS
        val tampered = lastSeen - 90 * DutyDuration.MINUTE_MS

        val reconciled = DutyClock.reconcile(session, lastSeen, tampered)
        val remaining = DutyClock.remainingMs(reconciled, tampered)

        // Still 90 minutes left — exactly what it would have been without the change.
        assertEquals(90 * DutyDuration.MINUTE_MS, remaining)
    }

    @Test
    fun `reconcile preserves the planned duration`() {
        val session = DutyClock.start(t0, 4 * DutyDuration.HOUR_MS)
        val lastSeen = t0 + DutyDuration.HOUR_MS
        val reconciled = DutyClock.reconcile(session, lastSeen, lastSeen - DutyDuration.HOUR_MS)

        assertEquals(session.plannedDurationMs, reconciled.plannedDurationMs)
        assertEquals(
            session.endsAtWallMs - session.startedAtWallMs,
            reconciled.endsAtWallMs - reconciled.startedAtWallMs
        )
    }

    @Test
    fun `moving the clock forward ends duty rather than extending it`() {
        val session = DutyClock.start(t0, DutyDuration.HOUR_MS)
        val lastSeen = t0 + DutyDuration.MINUTE_MS
        val jumped = lastSeen + 5 * DutyDuration.HOUR_MS

        val reconciled = DutyClock.reconcile(session, lastSeen, jumped)
        assertEquals(session, reconciled)
        assertTrue(DutyClock.statusFor(reconciled, jumped) is DutyStatus.Free)
    }

    @Test
    fun `no reconciliation on the very first observation`() {
        val session = DutyClock.start(t0, DutyDuration.HOUR_MS)
        assertEquals(session, DutyClock.reconcile(session, 0L, t0))
    }

    @Test
    fun `statusFor reports free for a null session`() {
        assertTrue(DutyClock.statusFor(null, t0) is DutyStatus.Free)
    }

    @Test
    fun `statusFor reports remaining time while on duty`() {
        val session = DutyClock.start(t0, DutyDuration.HOUR_MS)
        val status = DutyClock.statusFor(session, t0 + 15 * DutyDuration.MINUTE_MS)

        assertTrue(status is DutyStatus.OnDuty)
        assertEquals(45 * DutyDuration.MINUTE_MS, (status as DutyStatus.OnDuty).remainingMs)
    }
}
