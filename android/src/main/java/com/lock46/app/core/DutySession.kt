package com.lock46.app.core

/**
 * A duty period, expressed in wall-clock milliseconds (System.currentTimeMillis).
 *
 * Wall clock is used rather than SystemClock.elapsedRealtime because the session has to
 * survive a reboot, and elapsedRealtime resets to zero on boot. The trade-off is that the
 * wall clock can be changed by the user; [DutyClock.reconcile] handles that.
 */
data class DutySession(
    val startedAtWallMs: Long,
    val endsAtWallMs: Long,
    val plannedDurationMs: Long
) {
    init {
        require(endsAtWallMs >= startedAtWallMs) { "endsAt must not precede startedAt" }
        require(plannedDurationMs > 0) { "plannedDuration must be positive" }
    }
}

/** What LOCK46 is currently doing. */
sealed interface DutyStatus {
    /** No duty period is running: the phone behaves normally. */
    data object Free : DutyStatus

    /** A duty period is running; [session] carries the timings. */
    data class OnDuty(val session: DutySession, val remainingMs: Long) : DutyStatus
}

/**
 * Pure time arithmetic for duty periods. No Android types, no I/O — everything here is
 * directly unit-testable and every caller passes `now` in explicitly.
 */
object DutyClock {

    /**
     * Tolerance for wall-clock jitter (NTP corrections, small drift) before a backwards
     * jump is treated as a clock change.
     */
    const val CLOCK_BACKWARD_TOLERANCE_MS: Long = 5_000L

    fun start(nowWallMs: Long, durationMs: Long): DutySession {
        require(DutyDuration.isValid(durationMs)) { "invalid duty duration: $durationMs" }
        return DutySession(
            startedAtWallMs = nowWallMs,
            endsAtWallMs = nowWallMs + durationMs,
            plannedDurationMs = durationMs
        )
    }

    /**
     * Milliseconds left in [session] at [nowWallMs].
     *
     * Clamped to `0..plannedDuration` so that neither an expired session nor a backwards
     * clock change can ever report more time than was originally requested.
     */
    fun remainingMs(session: DutySession, nowWallMs: Long): Long =
        (session.endsAtWallMs - nowWallMs).coerceIn(0L, session.plannedDurationMs)

    fun isExpired(session: DutySession, nowWallMs: Long): Boolean =
        nowWallMs >= session.endsAtWallMs

    /**
     * Compensates for the wall clock being moved backwards.
     *
     * If the clock is set back by N ms, a naive `endsAt - now` would hand the user N extra
     * milliseconds of duty-free time — or, with a large enough change, an effectively
     * permanent duty period. Shifting `endsAt` by the same delta keeps the remaining time
     * monotonically decreasing across a clock change.
     *
     * Forward jumps are intentionally *not* compensated: they can only end duty early, and
     * treating them as tampering would make an honest timezone/NTP correction extend duty.
     * This is documented as a known V1 limitation.
     */
    fun reconcile(
        session: DutySession,
        lastObservedWallMs: Long,
        nowWallMs: Long
    ): DutySession {
        if (lastObservedWallMs <= 0L) return session
        val backwardsBy = lastObservedWallMs - nowWallMs
        if (backwardsBy <= CLOCK_BACKWARD_TOLERANCE_MS) return session
        return session.copy(
            startedAtWallMs = session.startedAtWallMs - backwardsBy,
            endsAtWallMs = session.endsAtWallMs - backwardsBy
        )
    }

    fun statusFor(session: DutySession?, nowWallMs: Long): DutyStatus {
        if (session == null) return DutyStatus.Free
        val remaining = remainingMs(session, nowWallMs)
        return if (remaining <= 0L) DutyStatus.Free else DutyStatus.OnDuty(session, remaining)
    }
}
