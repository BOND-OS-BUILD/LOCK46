package com.lock46.app.core

/**
 * Stores the admin PIN record and throttles verification attempts.
 *
 * Throttling matters here because the whole point of the PIN is that ending duty early is
 * deliberate rather than casual, and a 4-digit PIN with unlimited attempts is not a
 * meaningful speed bump.
 */
class PinRepository(private val store: KeyValueStore) {

    companion object {
        private const val KEY_RECORD = "pin.record"
        private const val KEY_FAILED = "pin.failed_attempts"
        private const val KEY_LOCKED_UNTIL = "pin.locked_until"

        /** Failures tolerated before the first lockout. */
        const val ATTEMPTS_BEFORE_LOCKOUT = 5

        /** Base lockout, doubled for each further group of failures, capped at 15 minutes. */
        const val BASE_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 15 * 60_000L
    }

    sealed interface Result {
        data object Accepted : Result
        data class Rejected(val attemptsRemaining: Int) : Result
        data class LockedOut(val retryAfterMs: Long) : Result
        data object NotConfigured : Result
    }

    fun isConfigured(): Boolean = !store.getString(KEY_RECORD).isNullOrBlank()

    /** Sets the PIN. Callers must have already run [PinHasher.validate]. */
    fun setPin(pin: String) {
        store.putString(KEY_RECORD, PinHasher.hash(pin))
        clearThrottle()
    }

    /** Changes the PIN, requiring the current one. Returns the verification result. */
    fun changePin(currentPin: String, newPin: String, nowMs: Long): Result {
        val verified = verify(currentPin, nowMs)
        if (verified is Result.Accepted) setPin(newPin)
        return verified
    }

    fun lockedOutForMs(nowMs: Long): Long {
        val until = store.getLong(KEY_LOCKED_UNTIL, 0L)
        return (until - nowMs).coerceAtLeast(0L)
    }

    fun verify(pin: String, nowMs: Long): Result {
        if (!isConfigured()) return Result.NotConfigured

        val remainingLockout = lockedOutForMs(nowMs)
        if (remainingLockout > 0L) return Result.LockedOut(remainingLockout)

        return if (PinHasher.verify(pin, store.getString(KEY_RECORD))) {
            clearThrottle()
            Result.Accepted
        } else {
            registerFailure(nowMs)
        }
    }

    private fun registerFailure(nowMs: Long): Result {
        val failures = store.getInt(KEY_FAILED, 0) + 1
        store.putInt(KEY_FAILED, failures)

        if (failures % ATTEMPTS_BEFORE_LOCKOUT == 0) {
            val tier = (failures / ATTEMPTS_BEFORE_LOCKOUT) - 1
            val lockout = (BASE_LOCKOUT_MS shl tier.coerceIn(0, 10)).coerceAtMost(MAX_LOCKOUT_MS)
            store.putLong(KEY_LOCKED_UNTIL, nowMs + lockout)
            return Result.LockedOut(lockout)
        }
        return Result.Rejected(ATTEMPTS_BEFORE_LOCKOUT - (failures % ATTEMPTS_BEFORE_LOCKOUT))
    }

    private fun clearThrottle() {
        store.remove(KEY_FAILED)
        store.remove(KEY_LOCKED_UNTIL)
    }
}
