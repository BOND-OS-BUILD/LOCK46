package com.lock46.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whitelist persistence across saves and process restarts. */
class WhitelistRepositoryTest {

    @Test
    fun `starts empty`() {
        assertTrue(WhitelistRepository(InMemoryKeyValueStore()).get().isEmpty())
    }

    @Test
    fun `saved packages survive a process restart`() {
        val store = InMemoryKeyValueStore()
        WhitelistRepository(store).save(setOf("com.android.dialer", "com.android.camera"))

        val revived = WhitelistRepository(InMemoryKeyValueStore(store.snapshot()))

        assertEquals(setOf("com.android.dialer", "com.android.camera"), revived.get())
        assertTrue(revived.contains("com.android.camera"))
        assertFalse(revived.contains("com.example.socialmedia"))
    }

    @Test
    fun `saving replaces rather than merges`() {
        val repo = WhitelistRepository(InMemoryKeyValueStore())
        repo.save(setOf("a", "b"))
        repo.save(setOf("b", "c"))

        assertEquals(setOf("b", "c"), repo.get())
    }

    @Test
    fun `saving an empty set clears the whitelist`() {
        val repo = WhitelistRepository(InMemoryKeyValueStore())
        repo.save(setOf("a"))
        repo.save(emptySet())

        assertTrue(repo.get().isEmpty())
    }

    @Test
    fun `blank package names are discarded`() {
        val repo = WhitelistRepository(InMemoryKeyValueStore())
        repo.save(setOf("com.android.camera", "", "   "))

        assertEquals(setOf("com.android.camera"), repo.get())
    }

    @Test
    fun `the state flow tracks saves`() {
        val repo = WhitelistRepository(InMemoryKeyValueStore())
        repo.save(setOf("com.android.camera"))

        assertEquals(setOf("com.android.camera"), repo.packages.value)
    }

    @Test
    fun `reload picks up a write made by another component`() {
        val store = InMemoryKeyValueStore()
        val repo = WhitelistRepository(store)
        // Simulates the enforcement service seeing a whitelist saved by the UI process.
        store.putStringSet("whitelist.packages", setOf("com.android.camera"))

        assertTrue(repo.get().isEmpty())
        repo.reload()
        assertEquals(setOf("com.android.camera"), repo.get())
    }
}
