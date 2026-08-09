package com.lock46.app.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lock46.app.Graph
import com.lock46.app.core.DutyDuration
import com.lock46.app.core.DutyStatus
import com.lock46.app.core.PinRepository
import com.lock46.app.enforce.DutyController
import com.lock46.app.ui.theme.Lock46Colors
import com.lock46.app.util.Permissions
import com.lock46.app.util.TimeFormat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The screen the user sees on every launch: current mode, and the one action that matters.
 *
 * There is no "disable" control here while on duty — ending duty always goes through the
 * PIN prompt, and the prompt is the only path to [DutyController.end].
 */
@Composable
fun HomeScreen(
    onOpenEssentialApps: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val status by Graph.duty.status.collectAsStateWithLifecycle()

    var showDurationPicker by remember { mutableStateOf(false) }
    var showPinPrompt by remember { mutableStateOf(false) }
    var showPermissionWarning by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }

    // One-second tick so the countdown stays live while this screen is visible.
    // Keyed on Unit rather than on `status`: refresh() mutates the flow this effect reads,
    // and keying on it would cancel and relaunch the effect on every tick.
    LaunchedEffect(Unit) {
        while (true) {
            Graph.duty.refresh()
            delay(1_000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text("LOCK46", style = MaterialTheme.typography.titleLarge, color = Lock46Colors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Note("On duty. Off distractions.", color = Lock46Colors.TextDim)

        Spacer(Modifier.height(28.dp))
        SectionLabel("Status")
        Spacer(Modifier.height(10.dp))

        when (val current = status) {
            is DutyStatus.Free -> FreeModePanel()
            is DutyStatus.OnDuty -> DutyModePanel(current)
        }

        Spacer(Modifier.height(26.dp))

        when (status) {
            is DutyStatus.Free -> {
                PrimaryAction(
                    label = "Start Duty",
                    onClick = {
                        if (Permissions.allRequiredGranted(context)) {
                            showDurationPicker = true
                        } else {
                            showPermissionWarning = true
                        }
                    },
                    color = Lock46Colors.Duty
                )
                Spacer(Modifier.height(12.dp))
                SecondaryAction("Essential Apps", onOpenEssentialApps)
                Spacer(Modifier.height(10.dp))
                SecondaryAction("Settings", onOpenSettings)
            }

            is DutyStatus.OnDuty -> {
                SecondaryAction("Essential Apps", onOpenEssentialApps)
                Spacer(Modifier.height(10.dp))
                SecondaryAction(
                    label = "End Duty",
                    onClick = {
                        pinError = null
                        showPinPrompt = true
                    },
                    contentColor = Lock46Colors.Alert
                )
                Spacer(Modifier.height(10.dp))
                Note(
                    "End Duty requires authorization.",
                    color = Lock46Colors.TextDim,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(18.dp))
                SecondaryAction("Settings", onOpenSettings)
            }
        }
    }

    if (showDurationPicker) {
        DurationPickerDialog(
            initialMs = Graph.settings.defaultDurationMs,
            onConfirm = { durationMs ->
                showDurationPicker = false
                runCatching { DutyController.start(context, durationMs) }
            },
            onDismiss = { showDurationPicker = false }
        )
    }

    if (showPermissionWarning) {
        SetupRequiredDialog(
            onOpenSettings = {
                showPermissionWarning = false
                onOpenSettings()
            },
            onDismiss = { showPermissionWarning = false }
        )
    }

    if (showPinPrompt) {
        PinPromptDialog(
            title = "Authorization required",
            message = "Enter the admin PIN to end the current duty period.",
            confirmLabel = "End duty",
            errorText = pinError,
            onConfirm = { entered ->
                when (val result = Graph.pin.verify(entered, System.currentTimeMillis())) {
                    is PinRepository.Result.Accepted -> {
                        showPinPrompt = false
                        pinError = null
                        DutyController.end(context)
                    }

                    is PinRepository.Result.Rejected ->
                        pinError = "Incorrect PIN. ${result.attemptsRemaining} attempt(s) " +
                            "before a temporary lockout."

                    is PinRepository.Result.LockedOut ->
                        pinError = "Too many attempts. Try again in " +
                            "${TimeFormat.compact(result.retryAfterMs)}."

                    is PinRepository.Result.NotConfigured ->
                        pinError = "No admin PIN is configured. Set one in Settings."
                }
            },
            onDismiss = {
                showPinPrompt = false
                pinError = null
            }
        )
    }
}

@Composable
private fun FreeModePanel() {
    Panel(accent = Lock46Colors.Line) {
        StatusDot(Lock46Colors.Ready, "Free Mode")
        Spacer(Modifier.height(12.dp))
        Text(
            "Ready for duty.",
            style = MaterialTheme.typography.bodyLarge,
            color = Lock46Colors.TextSecondary
        )
        Spacer(Modifier.height(6.dp))
        Note("All applications are available. No restrictions are being enforced.")
    }
}

@Composable
private fun DutyModePanel(status: DutyStatus.OnDuty) {
    val endsAt = remember(status.session.endsAtWallMs) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(status.session.endsAtWallMs))
    }

    Panel(accent = Lock46Colors.Duty) {
        StatusDot(Lock46Colors.Duty, "Duty Mode Active")
        Spacer(Modifier.height(18.dp))
        SectionLabel("Remaining")
        Spacer(Modifier.height(4.dp))
        Text(
            text = TimeFormat.countdown(status.remainingMs),
            style = MaterialTheme.typography.displayLarge,
            color = Lock46Colors.TextPrimary
        )
        Spacer(Modifier.height(14.dp))
        DetailRow("Ends", endsAt)
        DetailRow("Duty length", DutyDuration.label(status.session.plannedDurationMs))
        DetailRow(
            "Essential apps",
            "${Graph.whitelist.get().size} approved",
            valueColor = Lock46Colors.TextSecondary
        )
    }
}

@Composable
private fun SetupRequiredDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lock46Colors.PanelRaised,
        titleContentColor = Lock46Colors.TextPrimary,
        textContentColor = Lock46Colors.TextSecondary,
        title = { Text("SETUP INCOMPLETE", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    "LOCK46 cannot enforce restrictions until the accessibility service " +
                        "and the display-over-other-apps permission are granted.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                Note("Starting duty now would show the timer but would not block anything.")
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onOpenSettings) {
                Text("OPEN SETUP", color = Lock46Colors.Duty)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Lock46Colors.TextSecondary)
            }
        }
    )
}
