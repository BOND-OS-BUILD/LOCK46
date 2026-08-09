package com.lock46.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Duty lifecycle: start, tick, expiry, process death and reboot restore.
 *
 * Time is injected, so these run instantly and deterministically — an 8-hour duty period
 * is tested by moving the clock, not by waiting.
 */
class DutyLifecycleTest {

    private class FakeClock(var now: Long = 1_700_000_000_000L) {
        fun advance(ms: Long) { now += ms }
        fun rewind(ms: Long) { now -= ms }
    }

    private fun repo(store: KeyValueStore, clock: FakeClock) =
        DutyRepository(store) { clock.now }

    @Test
    fun `starts free`() {
        val duty = repo(InMemoryKeyValueStore(), FakeClock())
        assertTrue(duty.status.value is DutyStatus.Free)
        assertFalse(duty.isOnDuty)
        assertEquals(0L, duty.remainingMs())
    }

    @Test
    fun `start puts the app on duty with the requested duration`() {
        val clock = FakeClock()
        val duty = repo(InMemoryKeyValueStore(), clock)

        val session = duty.start(DutyDuration.HOUR_MS)

        assertTrue(duty.isOnDuty)
        assertEquals(clock.now, session.startedAtWallMs)
        assertEquals(clock.now + DutyDuration.HOUR_MS, session.endsAtWallMs)
        assertEquals(DutyDuration.HOUR_MS, duty.remainingMs())
        assertEquals(clock.now + DutyDuration.HOUR_MS, duty.endsAtWallMs())
    }

    @Test
    fun `remaining time counts down as the clock advances`() {
        val clock = FakeClock()
        val duty = repo(InMemoryKeyValueStore(), clock)
        duty.start(2 * DutyDuration.HOUR_MS)

        clock.advance(30 * DutyDuration.MINUTE_MS)
        duty.refresh()
        assertEquals(90 * DutyDuration.MINUTE_MS, duty.remainingMs())

        clock.advance(89 * DutyDuration.MINUTE_MS)
        duty.refresh()
        assertEquals(DutyDuration.MINUTE_MS, duty.remainingMs())
    }

    @Test
    fun `duty expires on its own when the timer runs out`() {
        val clock = FakeClock()
        val duty = repo(InMemoryKeyValueStore(), clock)
        duty.start(30 * DutyDuration.MINUTE_MS)

        clock.advance(30 * DutyDuration.MINUTE_MS)

        assertTrue(duty.refresh() is DutyStatus.Free)
        assertFalse(duty.isOnDuty)
        assertEquals(0L, duty.remainingMs())
    }

    @Test
    fun `expiry is inclusive at the exact end instant`() {
        val clock = FakeClock()
        val session = DutyClock.start(clock.now, DutyDuration.HOUR_MS)

        assertFalse(DutyClock.isExpired(session, session.endsAtWallMs - 1))
        assertTrue(DutyClock.isExpired(session, session.endsAtWallMs))
        assertTrue(DutyClock.isExpired(session, session.endsAtWallMs + 1))
    }

    @Test
    fun `duty survives process death`() {
        val clock = FakeClock()
        val store = InMemoryKeyValueStore()

        repo(store, clock).start(4 * DutyDuration.HOUR_MS)
        clock.advance(DutyDuration.HOUR_MS)

        // A new repository over the same store is exactly what happens when the app's
        // process is killed and something later touches duty state again.
        val revived = repo(store, clock)

        assertTrue(revived.isOnDuty)
        assertEquals(3 * DutyDuration.HOUR_MS, revived.remainingMs())
    }

    @Test
    fun `duty survives reboot and restores the same end time`() {
        val clock = FakeClock()
        val store = InMemoryKeyValueStore()
        val endsAt = repo(store, clock).start(8 * DutyDuration.HOUR_MS).endsAtWallMs

        // Reboot: the process is gone and only persisted state remains.
        val persisted = InMemoryKeyValueStore(store.snapshot())
        clock.advance(2 * DutyDuration.HOUR_MS)

        val afterBoot = repo(persisted, clock)

        assertTrue(afterBoot.isOnDuty)
        assertEquals(endsAt, afterBoot.endsAtWallMs())
        assertEquals(6 * DutyDuration.HOUR_MS, afterBoot.remainingMs())
    }

    @Test
    fun `duty that elapsed while the device was off comes back as free`() {
        val clock = FakeClock()
        val store = InMemoryKeyValueStore()
        repo(store, clock).start(DutyDuration.HOUR_MS)

        val persisted = InMemoryKeyValueStore(store.snapshot())
        clock.advance(3 * DutyDuration.HOUR_MS)

        assertTrue(repo(persisted, clock).refresh() is DutyStatus.Free)
    }

    @Test
    fun `end clears duty immediately`() {
        val clock = FakeClock()
        val store = InMemoryKeyValueStore()
        val duty = repo(store, clock)
        duty.start(DutyDuration.HOUR_MS)

        duty.end()

        assertTrue(duty.status.value is DutyStatus.Free)
        assertNull(duty.currentSession())
        // And it stays ended across a restart.
        assertFalse(repo(InMemoryKeyValueStore(store.snapshot()), clock).isOnDuty)
    }

    @Test
    fun `starting twice does not extend or reset the running period`() {
        val clock = FakeClock()
        val duty = repo(InMemoryKeyValueStore(), clock)
        val first = duty.start(DutyDuration.HOUR_MS)

        clock.advance(10 * DutyDuration.MINUTE_MS)
        val second = duty.start(8 * DutyDuration.HOUR_MS)

        assertEquals(first.endsAtWallMs, second.endsAtWallMs)
        assertEquals(50 * DutyDuration.MINUTE_MS, duty.remainingMs())
    }

    @Test
    fun `a corrupted duty record fails safe to free mode`() {
        val clock = FakeClock()
        val store = InMemoryKeyValueStore()
        // Active flag set but the timings never written — e.g. a write interrupted by a
        // kill. This must not throw inside a boot receiver.
        store.putBoolean("duty.active", true)

        assertTrue(repo(store, clock).refresh() is DutyStatus.Free)
    }

    @Test
    fun `session state is exposed for the ui`() {
        val clock = FakeClock()
        val duty = repo(InMemoryKeyValueStore(), clock)
        duty.start(DutyDuration.HOUR_MS)

        val status = duty.status.value
        assertTrue(status is DutyStatus.OnDuty)
        assertNotNull(duty.currentSession())
        assertEquals(DutyDuration.HOUR_MS, (status as DutyStatus.OnDuty).session.plannedDurationMs)
    }
}
