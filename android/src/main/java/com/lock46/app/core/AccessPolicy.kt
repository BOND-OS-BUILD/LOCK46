package com.lock46.app.core

/**
 * The single place that answers "may this package be used right now?".
 *
 * Policy: DEFAULT = BLOCK, EXPLICITLY APPROVED = ALLOW. Nothing is allowed because of what
 * category it belongs to; the only implicit allowances are the packages that must keep
 * working for the phone to remain a phone (see [AlwaysAllowed]).
 */
object AccessPolicy {

    /**
     * Packages that are allowed regardless of the user's whitelist.
     *
     * These are resolved at runtime from the device rather than hard-coded, because the
     * launcher, system UI, keyboard and dialer differ per OEM.
     */
    data class AlwaysAllowed(
        /** LOCK46 itself — blocking it would make the app unable to show its own screens. */
        val selfPackage: String,
        /** Home / launcher packages: blocking these leaves nowhere to return to. */
        val homePackages: Set<String> = emptySet(),
        /** System UI, framework and (on some OEMs) the status bar host. */
        val systemPackages: Set<String> = emptySet(),
        /** Enabled input methods: blocking the keyboard would break allowed apps. */
        val inputMethodPackages: Set<String> = emptySet(),
        /** Default dialer, so emergency calling is never intentionally blocked. */
        val dialerPackages: Set<String> = emptySet()
    ) {
        val all: Set<String> =
            buildSet {
                add(selfPackage)
                addAll(homePackages)
                addAll(systemPackages)
                addAll(inputMethodPackages)
                addAll(dialerPackages)
            }.filter { it.isNotBlank() }.toSet()
    }

    /** Why a package was allowed or blocked — surfaced in logs and the settings screen. */
    enum class Decision { ALLOW_FREE_MODE, ALLOW_INFRASTRUCTURE, ALLOW_WHITELISTED, BLOCK }

    /**
     * @param packageName foreground package, or null when it could not be determined.
     * @param onDuty whether a duty period is currently running.
     * @param whitelist packages the user explicitly approved.
     */
    fun decide(
        packageName: String?,
        onDuty: Boolean,
        whitelist: Set<String>,
        alwaysAllowed: AlwaysAllowed
    ): Decision {
        if (!onDuty) return Decision.ALLOW_FREE_MODE
        if (packageName.isNullOrBlank()) return Decision.ALLOW_INFRASTRUCTURE
        if (packageName in alwaysAllowed.all) return Decision.ALLOW_INFRASTRUCTURE
        if (packageName in whitelist) return Decision.ALLOW_WHITELISTED
        return Decision.BLOCK
    }

    fun shouldBlock(
        packageName: String?,
        onDuty: Boolean,
        whitelist: Set<String>,
        alwaysAllowed: AlwaysAllowed
    ): Boolean = decide(packageName, onDuty, whitelist, alwaysAllowed) == Decision.BLOCK
}
