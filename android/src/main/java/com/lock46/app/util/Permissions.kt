package com.lock46.app.util

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Every permission LOCK46 asks for, why it is needed, and how to check it.
 *
 * Nothing is requested "because it exists": each entry below maps to a specific behaviour
 * that stops working without it, and that reason is shown verbatim in Settings.
 */
enum class RequiredPermission(
    val title: String,
    val why: String,
    val required: Boolean
) {
    USAGE_ACCESS(
        title = "Usage access",
        why = "Lets LOCK46 see which app you just opened, so an app you have not approved " +
            "can be blocked. LOCK46 reads only the package name of the app in front — " +
            "never screen content, and never your usage history.",
        required = true
    ),
    OVERLAY(
        title = "Display over other apps",
        why = "Draws the LOCK46 blocking screen on top of a blocked app. Without it, " +
            "LOCK46 can only send you back to the home screen.",
        required = true
    ),
    NOTIFICATIONS(
        title = "Notifications",
        why = "Shows the ongoing Duty Mode notification with remaining time. Android " +
            "requires a visible notification for the enforcement service.",
        required = false
    ),
    BATTERY(
        title = "Unrestricted battery",
        why = "Stops aggressive battery optimisation from killing the enforcement " +
            "service mid-duty.",
        required = false
    );
}

object Permissions {

    fun isGranted(context: Context, permission: RequiredPermission): Boolean = when (permission) {
        RequiredPermission.OVERLAY -> Settings.canDrawOverlays(context)
        RequiredPermission.USAGE_ACCESS -> hasUsageAccess(context)
        RequiredPermission.NOTIFICATIONS -> hasNotificationPermission(context)
        RequiredPermission.BATTERY -> isIgnoringBatteryOptimizations(context)
    }

    /** True when every permission marked [RequiredPermission.required] is granted. */
    fun allRequiredGranted(context: Context): Boolean =
        RequiredPermission.entries.filter { it.required }.all { isGranted(context, it) }

    fun settingsIntentFor(context: Context, permission: RequiredPermission): Intent =
        when (permission) {
            RequiredPermission.OVERLAY ->
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )

            RequiredPermission.USAGE_ACCESS ->
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

            RequiredPermission.NOTIFICATIONS ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

            RequiredPermission.BATTERY ->
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)


    fun hasUsageAccess(context: Context): Boolean = runCatching {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        if (mode == AppOpsManager.MODE_DEFAULT) {
            ContextCompat.checkSelfPermission(
                context,
                "android.permission.PACKAGE_USAGE_STATS"
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }.getOrDefault(false)

    fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean = runCatching {
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)
}
