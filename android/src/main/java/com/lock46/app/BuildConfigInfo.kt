package com.lock46.app

/**
 * Build identity, declared once in source rather than via a generated BuildConfig class.
 *
 * These two strings are also asserted against the Gradle configuration by
 * `BuildIdentityTest`, so they cannot silently drift apart from the APK's real values.
 */
object BuildConfigInfo {
    const val VERSION_NAME: String = "1.0.1"
    const val APPLICATION_ID: String = "com.lock46.app"
}
