package com.lock46.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PIN hashing, verification, storage format and attempt throttling. */
class PinTest {

    private val now = 1_700_000_000_000L

    // ---- hashing -----------------------------------------------------------------

    @Test
    fun `correct pin verifies`() {
        val record = PinHasher.hash("4619")
        assertTrue(PinHasher.verify("4619", record))
    }

    @Test
    fun `wrong pin is rejected`() {
        val record = PinHasher.hash("4619")
        assertFalse(PinHasher.verify("4618", record))
        assertFalse(PinHasher.verify("46190", record))
        assertFalse(PinHasher.verify("461", record))
        assertFalse(PinHasher.verify("", record))
    }

    @Test
    fun `the pin is never present in the stored record`() {
        val pin = "917352"
        val record = PinHasher.hash(pin)
        assertFalse("stored record must not contain the PIN", record.contains(pin))
    }

    @Test
    fun `the same pin hashes differently every time`() {
        // Distinct salts: two users with the same PIN must not share a record.
        assertNotEquals(PinHasher.hash("4619"), PinHasher.hash("4619"))
    }

    @Test
    fun `the record carries its algorithm and iteration count`() {
        val parts = PinHasher.hash("4619").split('$')
        assertEquals(4, parts.size)
        assertEquals("pbkdf2-sha256", parts[0])
        assertTrue((parts[1].toIntOrNull() ?: 0) >= 100_000)
        assertEquals(32, parts[2].length) // 16-byte salt as hex
        assertEquals(64, parts[3].length) // 32-byte key as hex
    }

    @Test
    fun `a malformed record fails closed instead of throwing`() {
        listOf(
            null,
            "",
            "   ",
            "not-a-record",
            "pbkdf2-sha256\$oops\$aa\$bb",
            "pbkdf2-sha256\$1000\$zz\$bb",
            "bcrypt\$1000\$aa\$bb",
            "pbkdf2-sha256\$1000\$aa"
        ).forEach { record ->
            assertFalse("record <$record> must not verify", PinHasher.verify("4619", record))
        }
    }

    @Test
    fun `needsRehash flags records below current policy`() {
        assertFalse(PinHasher.needsRehash(PinHasher.hash("4619")))
        assertTrue(PinHasher.needsRehash("pbkdf2-sha256\$1000\$aabb\$ccdd"))
        assertTrue(PinHasher.needsRehash(null))
    }

    // ---- policy ------------------------------------------------------------------

    @Test
    fun `pin policy accepts a sensible pin`() {
        assertTrue(PinHasher.validate("4619") is PinHasher.PinValidation.Ok)
        assertTrue(PinHasher.validate("917352") is PinHasher.PinValidation.Ok)
    }

    @Test
    fun `pin policy rejects weak or malformed entries`() {
        listOf(
            "123",        // too short
            "123456789",  // too long
            "12a4",       // not digits
            "1111",       // repeated digit
            "1234",       // ascending run
            "4321"        // descending run
        ).forEach {
            assertTrue(
                "<$it> should be rejected",
                PinHasher.validate(it) is PinHasher.PinValidation.Rejected
            )
        }
    }

    // ---- repository + throttling --------------------------------------------------

    @Test
    fun `repository reports whether a pin is configured`() {
        val repo = PinRepository(InMemoryKeyValueStore())
        assertFalse(repo.isConfigured())
        repo.setPin("4619")
        assertTrue(repo.isConfigured())
    }

    @Test
    fun `verification before setup reports not configured`() {
        val repo = PinRepository(InMemoryKeyValueStore())
        assertTrue(repo.verify("4619", now) is PinRepository.Result.NotConfigured)
    }

    @Test
    fun `correct pin is accepted and wrong pin is rejected`() {
        val repo = PinRepository(InMemoryKeyValueStore())
        repo.setPin("4619")

        assertTrue(repo.verify("4619", now) is PinRepository.Result.Accepted)
        assertTrue(repo.verify("0000", now) is PinRepository.Result.Rejected)
    }

    @Test
    fun `repeated wrong pins trigger a lockout`() {
        val repo = PinRepository(InMemoryKeyValueStore())
        repo.setPin("4619")

        repeat(PinRepository.ATTEMPTS_BEFORE_LOCKOUT - 1) {
            assertTrue(repo.verify("0000", now) is PinRepository.Result.Rejected)
        }
        val locked = repo.verify("0000", now)
        assertTrue(locked is PinRepository.Result.LockedOut)
        assertEquals(PinRepository.BASE_LOCKOUT_MS, (locked as PinRepository.Result.LockedOut).retryAfterMs)
    }

    @Test
    fun `the correct pin is refused while locked out`() {
        val repo = PinRepository(InMemoryKeyValueStore())
        repo.setPin("4619")
        repeat(PinRepository.ATTEMPTS_BEFORE_LOCKOUT) { repo.verify("0000", now) }

        assertTrue(repo.verify("4619", now) is PinRepository.Result.LockedOut)
    }

    @Test
    fun `the lockout lifts once it expires and a correct pin clears the counter`() {
        val repo = PinRepository(InMemoryKeyValueStore())
        repo.setPin("4619")
        repeat(PinRepository.ATTEMPTS_BEFORE_LOCKOUT) { repo.verify("0000", now) }

        val later = now + PinRepository.BASE_LOCKOUT_MS + 1
        assertTrue(repo.verify("4619", later) is PinRepository.Result.Accepted)
        assertEquals(0L, repo.lockedOutForMs(later))
        // Counter reset: the next wrong attempt is an ordinary rejection, not a lockout.
        assertTrue(repo.verify("0000", later) is PinRepository.Result.Rejected)
    }

    @Test
    fun `lockouts get longer and stay capped`() {
        val repo = PinRepository(InMemoryKeyValueStore())
        repo.setPin("4619")

        var previous = 0L
        var clock = now
        repeat(4) {
            repeat(PinRepository.ATTEMPTS_BEFORE_LOCKOUT - 1) { repo.verify("0000", clock) }
            val locked = repo.verify("0000", clock) as PinRepository.Result.LockedOut
            assertTrue(locked.retryAfterMs >= previous)
            assertTrue(locked.retryAfterMs <= PinRepository.MAX_LOCKOUT_MS)
            previous = locked.retryAfterMs
            clock += locked.retryAfterMs + 1
        }
    }

    @Test
    fun `changing the pin requires the current one`() {
        val repo = PinRepository(InMemoryKeyValueStore())
        repo.setPin("4619")

        assertTrue(repo.changePin("0000", "8842", now) is PinRepository.Result.Rejected)
        assertTrue(repo.verify("4619", now) is PinRepository.Result.Accepted)

        assertTrue(repo.changePin("4619", "8842", now) is PinRepository.Result.Accepted)
        assertTrue(repo.verify("8842", now) is PinRepository.Result.Accepted)
        assertTrue(repo.verify("4619", now) is PinRepository.Result.Rejected)
    }

    @Test
    fun `the pin record survives a process restart`() {
        val store = InMemoryKeyValueStore()
        PinRepository(store).setPin("4619")

        val revived = PinRepository(InMemoryKeyValueStore(store.snapshot()))
        assertTrue(revived.isConfigured())
        assertTrue(revived.verify("4619", now) is PinRepository.Result.Accepted)
    }

    @Test
    fun `no plaintext pin is written anywhere in the store`() {
        val store = InMemoryKeyValueStore()
        PinRepository(store).setPin("917352")

        store.snapshot().forEach { (key, value) ->
            assertFalse("$key leaked the PIN", value.toString().contains("917352"))
        }
    }
}
