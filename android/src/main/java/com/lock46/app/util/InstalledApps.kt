package com.lock46.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.inputmethod.InputMethodManager
import com.lock46.app.core.AccessPolicy

/** One installed, launchable application as shown in the app picker. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    /** True when the package is allowed regardless of the whitelist and cannot be toggled off. */
    val alwaysAllowed: Boolean,
    /** Short reason shown next to a pinned row, e.g. "Emergency calling". */
    val pinnedReason: String? = null
)

/**
 * Reads the installed-app list and resolves the packages that must never be blocked.
 *
 * Package visibility on API 30+ is granted by the `<queries>` block in the manifest, which
 * declares only the MAIN/LAUNCHER intent and the input-method service — LOCK46 never
 * requests QUERY_ALL_PACKAGES.
 */
object InstalledApps {

    fun resolveAlwaysAllowed(context: Context): AccessPolicy.AlwaysAllowed {
        val pm = context.packageManager
        return AccessPolicy.AlwaysAllowed(
            selfPackage = context.packageName,
            homePackages = homePackages(pm),
            systemPackages = setOf("android", "com.android.systemui"),
            inputMethodPackages = inputMethodPackages(context),
            dialerPackages = dialerPackages(pm)
        )
    }

    private fun homePackages(pm: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    private fun inputMethodPackages(context: Context): Set<String> = runCatching {
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm.enabledInputMethodList.mapNotNull { it.packageName }.toSet()
    }.getOrDefault(emptySet())

    /**
     * The default dialer, plus anything that can handle a dial intent.
     *
     * These stay allowed even when the user has not whitelisted the phone app, so LOCK46
     * never stands between the user and an emergency call.
     */
    private fun dialerPackages(pm: PackageManager): Set<String> {
        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
        return pm.queryIntentActivities(dial, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    /** Launchable apps, alphabetical, with pinned always-allowed entries surfaced first. */
    fun listLaunchable(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val alwaysAllowed = resolveAlwaysAllowed(context)
        val dialers = dialerPackages(pm)

        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)

        return resolved
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                val pkg = info.packageName
                InstalledApp(
                    packageName = pkg,
                    label = runCatching { pm.getApplicationLabel(info).toString() }
                        .getOrDefault(pkg),
                    icon = runCatching { pm.getApplicationIcon(info) }.getOrNull(),
                    alwaysAllowed = pkg in alwaysAllowed.all,
                    pinnedReason = when {
                        pkg in dialers -> "Emergency calling"
                        pkg in alwaysAllowed.homePackages -> "Home screen"
                        pkg in alwaysAllowed.inputMethodPackages -> "Keyboard"
                        pkg in alwaysAllowed.all -> "System"
                        else -> null
                    }
                )
            }
            .sortedWith(
                compareByDescending<InstalledApp> { it.alwaysAllowed }
                    .thenBy { it.label.lowercase() }
            )
    }

    /** Best-effort human label for a package that may since have been uninstalled. */
    fun labelFor(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

}
