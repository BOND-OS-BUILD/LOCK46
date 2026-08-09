package com.lock46.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns duty state and its persistence.
 *
 * The persisted record is the source of truth, not the in-memory flow: the app's process
 * can be killed at any moment and the enforcement service can be restarted independently,
 * so every entry point re-derives status from the store via [refresh].
 *
 * [nowMs] is injected so the whole lifecycle — start, tick, expiry, reboot restore — is
 * testable without waiting for real time to pass.
 */
class DutyRepository(
    private val store: KeyValueStore,
    private val nowMs: () -> Long = System::currentTimeMillis
) {

    companion object {
        private const val KEY_ACTIVE = "duty.active"
        private const val KEY_STARTED_AT = "duty.started_at"
        private const val KEY_ENDS_AT = "duty.ends_at"
        private const val KEY_DURATION = "duty.duration"
        private const val KEY_LAST_SEEN = "duty.last_seen_wall"
    }

    private val _status = MutableStateFlow<DutyStatus>(DutyStatus.Free)
    val status: StateFlow<DutyStatus> = _status.asStateFlow()

    init {
        refresh()
    }

    val isOnDuty: Boolean get() = _status.value is DutyStatus.OnDuty

    fun currentSession(): DutySession? = (_status.value as? DutyStatus.OnDuty)?.session

    /**
     * Re-reads persisted state, applies clock-change reconciliation, clears the record if
     * the period has elapsed, and republishes status.
     *
     * @return the freshly computed status.
     */
    fun refresh(): DutyStatus {
        val now = nowMs()
        val stored = readSession()

        if (stored == null) {
            _status.value = DutyStatus.Free
            return DutyStatus.Free
        }

        val lastSeen = store.getLong(KEY_LAST_SEEN, 0L)
        val reconciled = DutyClock.reconcile(stored, lastSeen, now)
        if (reconciled != stored) writeSession(reconciled)
        store.putLong(KEY_LAST_SEEN, now)

        val status = DutyClock.statusFor(reconciled, now)
        if (status is DutyStatus.Free) clearSession()
        _status.value = status
        return status
    }

    /**
     * Starts a duty period.
     * @throws IllegalArgumentException when [durationMs] is outside the accepted range.
     * @return the started session, or the existing one if duty is already running
     *         (starting twice must not silently extend or reset the period).
     */
    fun start(durationMs: Long): DutySession {
        require(DutyDuration.isValid(durationMs)) {
            "Duty duration must be between ${DutyDuration.MIN_MS} ms and ${DutyDuration.MAX_MS} ms"
        }
        (refresh() as? DutyStatus.OnDuty)?.let { return it.session }

        val now = nowMs()
        val session = DutyClock.start(now, durationMs)
        writeSession(session)
        store.putLong(KEY_LAST_SEEN, now)
        _status.value = DutyStatus.OnDuty(session, DutyClock.remainingMs(session, now))
        return session
    }

    /** Ends the duty period. Callers are responsible for having authorised this. */
    fun end() {
        clearSession()
        _status.value = DutyStatus.Free
    }

    /** Milliseconds left, or 0 when free. */
    fun remainingMs(): Long = (_status.value as? DutyStatus.OnDuty)?.remainingMs ?: 0L

    /** Wall-clock end time, or null when free. */
    fun endsAtWallMs(): Long? = currentSession()?.endsAtWallMs

    private fun readSession(): DutySession? {
        if (!store.getBoolean(KEY_ACTIVE, false)) return null
        val startedAt = store.getLong(KEY_STARTED_AT, 0L)
        val endsAt = store.getLong(KEY_ENDS_AT, 0L)
        val duration = store.getLong(KEY_DURATION, 0L)
        // A partially written or corrupted record must fail safe to Free Mode rather than
        // throwing inside a boot receiver.
        if (startedAt <= 0L || endsAt <= 0L || duration <= 0L || endsAt < startedAt) {
            clearSession()
            return null
        }
        return DutySession(startedAt, endsAt, duration)
    }

    private fun writeSession(session: DutySession) {
        store.putBoolean(KEY_ACTIVE, true)
        store.putLong(KEY_STARTED_AT, session.startedAtWallMs)
        store.putLong(KEY_ENDS_AT, session.endsAtWallMs)
        store.putLong(KEY_DURATION, session.plannedDurationMs)
    }

    private fun clearSession() {
        store.putBoolean(KEY_ACTIVE, false)
        store.remove(KEY_STARTED_AT)
        store.remove(KEY_ENDS_AT)
        store.remove(KEY_DURATION)
        store.remove(KEY_LAST_SEEN)
    }
}
