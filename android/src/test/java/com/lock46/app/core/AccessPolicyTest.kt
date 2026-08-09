package com.lock46.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Allow/block decisions — the rule that the whole product rests on. */
class AccessPolicyTest {

    private val infra = AccessPolicy.AlwaysAllowed(
        selfPackage = "com.lock46.app",
        homePackages = setOf("com.android.launcher3"),
        systemPackages = setOf("android", "com.android.systemui"),
        inputMethodPackages = setOf("com.google.android.inputmethod.latin"),
        dialerPackages = setOf("com.android.dialer")
    )

    private val whitelist = setOf("com.android.camera", "com.example.maps")

    private fun decide(pkg: String?, onDuty: Boolean = true) =
        AccessPolicy.decide(pkg, onDuty, whitelist, infra)

    @Test
    fun `nothing is blocked in free mode`() {
        assertEquals(
            AccessPolicy.Decision.ALLOW_FREE_MODE,
            decide("com.example.socialmedia", onDuty = false)
        )
        assertFalse(
            AccessPolicy.shouldBlock("com.example.socialmedia", false, whitelist, infra)
        )
    }

    @Test
    fun `an unapproved app is blocked on duty`() {
        assertEquals(AccessPolicy.Decision.BLOCK, decide("com.example.socialmedia"))
        assertTrue(AccessPolicy.shouldBlock("com.example.socialmedia", true, whitelist, infra))
    }

    @Test
    fun `default is block — an app absent from every list is blocked`() {
        assertEquals(AccessPolicy.Decision.BLOCK, decide("com.brand.new.app"))
    }

    @Test
    fun `an approved app stays available`() {
        assertEquals(AccessPolicy.Decision.ALLOW_WHITELISTED, decide("com.android.camera"))
        assertEquals(AccessPolicy.Decision.ALLOW_WHITELISTED, decide("com.example.maps"))
    }

    @Test
    fun `the dialer is allowed even when it is not whitelisted`() {
        assertFalse("com.android.dialer" in whitelist)
        assertEquals(AccessPolicy.Decision.ALLOW_INFRASTRUCTURE, decide("com.android.dialer"))
    }

    @Test
    fun `launcher, system ui and keyboard are never blocked`() {
        listOf(
            "com.android.launcher3",
            "android",
            "com.android.systemui",
            "com.google.android.inputmethod.latin"
        ).forEach {
            assertEquals(
                "$it must never be blocked",
                AccessPolicy.Decision.ALLOW_INFRASTRUCTURE,
                decide(it)
            )
        }
    }

    @Test
    fun `lock46 cannot block itself`() {
        assertEquals(AccessPolicy.Decision.ALLOW_INFRASTRUCTURE, decide("com.lock46.app"))
    }

    @Test
    fun `an unknown foreground package is not blocked`() {
        // Better to miss a block than to strand the user behind an overlay we cannot
        // attribute to any app.
        assertEquals(AccessPolicy.Decision.ALLOW_INFRASTRUCTURE, decide(null))
        assertEquals(AccessPolicy.Decision.ALLOW_INFRASTRUCTURE, decide(""))
        assertEquals(AccessPolicy.Decision.ALLOW_INFRASTRUCTURE, decide("   "))
    }

    @Test
    fun `an empty whitelist blocks everything except infrastructure`() {
        assertTrue(AccessPolicy.shouldBlock("com.android.camera", true, emptySet(), infra))
        assertFalse(AccessPolicy.shouldBlock("com.android.dialer", true, emptySet(), infra))
    }

    @Test
    fun `always-allowed set skips blank entries`() {
        val sparse = AccessPolicy.AlwaysAllowed(selfPackage = "com.lock46.app", homePackages = setOf(""))
        assertEquals(setOf("com.lock46.app"), sparse.all)
    }
}
