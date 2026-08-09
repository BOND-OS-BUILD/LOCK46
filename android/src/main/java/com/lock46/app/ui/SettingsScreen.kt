package com.lock46.app.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lock46.app.BuildConfigInfo
import com.lock46.app.Graph
import com.lock46.app.core.DutyDuration
import com.lock46.app.core.DutyStatus
import com.lock46.app.ui.theme.Lock46Colors
import com.lock46.app.util.Permissions
import com.lock46.app.util.RequiredPermission

/**
 * Settings and setup status.
 *
 * Permission state is re-read on every recomposition rather than cached: the user leaves
 * the app to grant these in system settings, and a stale "granted" tick here would be
 * actively misleading about whether enforcement works.
 */
@Composable
fun SettingsScreen(
    refreshKey: Int,
    onOpenEssentialApps: () -> Unit,
    onChangePin: () -> Unit,
    onOpenSystemSetting: (RequiredPermission) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val status by Graph.duty.status.collectAsStateWithLifecycle()
    var showDefaultDuration by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 26.dp)
    ) {
        Text("SETTINGS", style = MaterialTheme.typography.titleLarge, color = Lock46Colors.TextPrimary)

        Spacer(Modifier.height(26.dp))
        SectionLabel("Setup status")
        Spacer(Modifier.height(10.dp))
        Panel {
            RequiredPermission.entries.forEachIndexed { index, permission ->
                if (index > 0) Spacer(Modifier.height(14.dp))
                PermissionRow(
                    context = context,
                    permission = permission,
                    refreshKey = refreshKey,
                    onClick = { onOpenSystemSetting(permission) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Security")
        Spacer(Modifier.height(10.dp))
        Panel {
            SettingRow(
                title = "Admin PIN",
                subtitle = if (Graph.pin.isConfigured()) {
                    "Configured. Required to end duty early."
                } else {
                    "Not configured — End Duty cannot be authorised."
                },
                subtitleColor = if (Graph.pin.isConfigured()) {
                    Lock46Colors.TextSecondary
                } else {
                    Lock46Colors.Alert
                },
                onClick = onChangePin
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Duty")
        Spacer(Modifier.height(10.dp))
        Panel {
            SettingRow(
                title = "Essential apps",
                subtitle = "${Graph.whitelist.get().size} app(s) approved for duty periods",
                onClick = onOpenEssentialApps
            )
            Spacer(Modifier.height(14.dp))
            SettingRow(
                title = "Default duration",
                subtitle = DutyDuration.label(Graph.settings.defaultDurationMs),
                onClick = { showDefaultDuration = true }
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Privacy")
        Spacer(Modifier.height(10.dp))
        Panel {
            Note(
                "LOCK46 works entirely offline. It has no internet permission, no account, " +
                    "no server and no analytics.\n\n" +
                    "It does not read or store calls, messages, contacts, location, " +
                    "microphone or camera data, files, or browsing history.\n\n" +
                    "The only things stored on this device are: your approved app list, " +
                    "the current duty period's start and end time, your duty-duration " +
                    "default, and a salted PBKDF2 hash of your admin PIN. The PIN itself is " +
                    "never stored and never logged."
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("About")
        Spacer(Modifier.height(10.dp))
        Panel {
            DetailRow("Version", BuildConfigInfo.VERSION_NAME)
            DetailRow("Application ID", BuildConfigInfo.APPLICATION_ID)
            DetailRow(
                "Enforcement",
                if (Permissions.allRequiredGranted(context)) "Armed" else "Degraded",
                valueColor = if (Permissions.allRequiredGranted(context)) {
                    Lock46Colors.Ready
                } else {
                    Lock46Colors.Alert
                }
            )
            DetailRow(
                "Current mode",
                if (status is DutyStatus.OnDuty) "Duty Mode" else "Free Mode",
                valueColor = if (status is DutyStatus.OnDuty) Lock46Colors.Duty else Lock46Colors.Ready
            )
            Spacer(Modifier.height(10.dp))
            Note(
                "V1 is a prototype authorisation mechanism running as a normal installed " +
                    "app. It is not Device Owner enforcement and is not tamper-proof: " +
                    "someone with physical access can disable the accessibility service, " +
                    "boot into safe mode, or uninstall LOCK46."
            )
        }

        Spacer(Modifier.height(28.dp))
        SecondaryAction("Back", onBack)
        Spacer(Modifier.height(20.dp))
    }

    if (showDefaultDuration) {
        DurationPickerDialog(
            initialMs = Graph.settings.defaultDurationMs,
            onConfirm = {
                Graph.settings.defaultDurationMs = it
                showDefaultDuration = false
            },
            onDismiss = { showDefaultDuration = false }
        )
    }
}

@Composable
private fun PermissionRow(
    context: Context,
    permission: RequiredPermission,
    refreshKey: Int,
    onClick: () -> Unit
) {
    val granted = remember(permission, refreshKey) { Permissions.isGranted(context, permission) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = permission.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Lock46Colors.TextPrimary
            )
            Text(
                text = when {
                    granted -> "GRANTED"
                    permission.required -> "REQUIRED"
                    else -> "OPTIONAL"
                },
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    granted -> Lock46Colors.Ready
                    permission.required -> Lock46Colors.Alert
                    else -> Lock46Colors.TextDim
                }
            )
        }
        Spacer(Modifier.height(4.dp))
        Note(permission.why)
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    subtitleColor: androidx.compose.ui.graphics.Color = Lock46Colors.TextSecondary
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = Lock46Colors.TextPrimary)
        Spacer(Modifier.height(3.dp))
        Note(subtitle, color = subtitleColor)
    }
}
