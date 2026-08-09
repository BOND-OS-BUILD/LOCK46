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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lock46.app.Graph
import com.lock46.app.core.PinHasher
import com.lock46.app.ui.theme.Lock46Colors
import com.lock46.app.util.Permissions
import com.lock46.app.util.RequiredPermission

/**
 * First-run setup.
 *
 * The user is never dropped into an Android settings page without first being told what
 * the permission does and why LOCK46 needs it — each step explains itself, then opens the
 * relevant page on an explicit tap.
 */
@Composable
fun OnboardingScreen(
    refreshKey: Int,
    onOpenSystemSetting: (RequiredPermission) -> Unit,
    onOpenEssentialApps: () -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 30.dp)
    ) {
        Text("LOCK46", style = MaterialTheme.typography.titleLarge, color = Lock46Colors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Note("Setup — step ${step + 1} of $TOTAL_STEPS", color = Lock46Colors.TextDim)
        Spacer(Modifier.height(26.dp))

        when (step) {
            0 -> IntroStep(onNext = { step = 1 })
            1 -> PinStep(onNext = { step = 2 })
            2 -> PermissionsStep(
                refreshKey = refreshKey,
                onOpenSystemSetting = onOpenSystemSetting,
                onNext = { step = 3 }
            )
            3 -> EssentialAppsStep(
                onOpenEssentialApps = onOpenEssentialApps,
                onNext = { step = 4 }
            )
            else -> ReadyStep(
                allGranted = Permissions.allRequiredGranted(context),
                pinConfigured = Graph.pin.isConfigured(),
                approvedCount = Graph.whitelist.get().size,
                onFinish = {
                    Graph.settings.isSetupComplete = true
                    onFinish()
                }
            )
        }

        if (step in 1..3) {
            Spacer(Modifier.height(14.dp))
            SecondaryAction("Back", onClick = { step-- })
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun IntroStep(onNext: () -> Unit) {
    Panel {
        Text(
            "What LOCK46 does",
            style = MaterialTheme.typography.titleMedium,
            color = Lock46Colors.TextPrimary
        )
        Spacer(Modifier.height(12.dp))
        Note(
            "LOCK46 turns this phone into an essentials-only device for the length of a " +
                "duty period that you start yourself.\n\n" +
                "You choose how long duty lasts and which apps stay available. Everything " +
                "you have not approved is blocked the moment you try to open it, and a " +
                "LOCK46 screen appears instead.\n\n" +
                "When the timer runs out, the phone returns to normal on its own. Ending " +
                "duty early requires an admin PIN that you set on the next step."
        )
    }
    Spacer(Modifier.height(16.dp))
    Panel {
        Text(
            "What it does not do",
            style = MaterialTheme.typography.titleMedium,
            color = Lock46Colors.TextPrimary
        )
        Spacer(Modifier.height(12.dp))
        Note(
            "No account, no server, no internet connection. Nothing about your calls, " +
                "messages, contacts, location or screen content is read, stored or sent " +
                "anywhere."
        )
    }
    Spacer(Modifier.height(22.dp))
    PrimaryAction("Continue", onNext)
}

@Composable
private fun PinStep(onNext: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Panel {
        Text(
            "Create an admin PIN",
            style = MaterialTheme.typography.titleMedium,
            color = Lock46Colors.TextPrimary
        )
        Spacer(Modifier.height(10.dp))
        Note(
            "This PIN is the only way to end a duty period before the timer expires. " +
                "${PinHasher.MIN_LENGTH}–${PinHasher.MAX_LENGTH} digits. It is stored as a " +
                "salted PBKDF2 hash, never in plain text."
        )
        Spacer(Modifier.height(18.dp))
        PinField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(PinHasher.MAX_LENGTH) },
            label = "New PIN"
        )
        Spacer(Modifier.height(12.dp))
        PinField(
            value = confirm,
            onValueChange = { confirm = it.filter(Char::isDigit).take(PinHasher.MAX_LENGTH) },
            label = "Confirm PIN"
        )
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Note(error!!, color = Lock46Colors.Alert)
        }
    }

    Spacer(Modifier.height(22.dp))
    PrimaryAction(
        label = "Set PIN",
        onClick = {
            error = when {
                pin != confirm -> "The two entries do not match."
                else -> when (val validation = PinHasher.validate(pin)) {
                    is PinHasher.PinValidation.Ok -> null
                    is PinHasher.PinValidation.Rejected -> validation.reason
                }
            }
            if (error == null) {
                Graph.pin.setPin(pin)
                onNext()
            }
        },
        enabled = pin.isNotEmpty() && confirm.isNotEmpty()
    )
}

@Composable
private fun PermissionsStep(
    refreshKey: Int,
    onOpenSystemSetting: (RequiredPermission) -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current

    Panel {
        Text(
            "Android permissions",
            style = MaterialTheme.typography.titleMedium,
            color = Lock46Colors.TextPrimary
        )
        Spacer(Modifier.height(10.dp))
        Note(
            "Android will not let an installed app restrict other apps without these. " +
                "Each one is listed with the exact reason LOCK46 needs it — nothing here " +
                "is requested speculatively."
        )
    }

    Spacer(Modifier.height(16.dp))

    RequiredPermission.entries.forEach { permission ->
        val granted = remember(permission, refreshKey) {
            Permissions.isGranted(context, permission)
        }
        Panel(accent = if (granted) Lock46Colors.Ready else null) {
            Text(
                permission.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Lock46Colors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Note(permission.why)
            Spacer(Modifier.height(12.dp))
            if (granted) {
                StatusDot(Lock46Colors.Ready, "Granted")
            } else {
                SecondaryAction(
                    label = if (permission.required) "Grant (required)" else "Grant (optional)",
                    onClick = { onOpenSystemSetting(permission) },
                    contentColor = if (permission.required) {
                        Lock46Colors.Duty
                    } else {
                        Lock46Colors.TextSecondary
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    Spacer(Modifier.height(10.dp))
    PrimaryAction("Continue", onNext)
    Spacer(Modifier.height(8.dp))
    Note(
        "You can continue without the optional items, but LOCK46 will tell you that " +
            "enforcement is degraded.",
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EssentialAppsStep(onOpenEssentialApps: () -> Unit, onNext: () -> Unit) {
    val approved = Graph.whitelist.get().size

    Panel {
        Text(
            "Choose essential apps",
            style = MaterialTheme.typography.titleMedium,
            color = Lock46Colors.TextPrimary
        )
        Spacer(Modifier.height(10.dp))
        Note(
            "Anything you do not approve is blocked during duty. The home screen, " +
                "keyboard and dialer stay available automatically so the phone still works " +
                "and emergency calling is never blocked."
        )
        Spacer(Modifier.height(16.dp))
        DetailRow(
            "Currently approved",
            "$approved app(s)",
            valueColor = if (approved == 0) Lock46Colors.Alert else Lock46Colors.Ready
        )
    }
    Spacer(Modifier.height(20.dp))
    PrimaryAction("Select essential apps", onOpenEssentialApps)
    Spacer(Modifier.height(10.dp))
    SecondaryAction("Continue", onNext)
}

@Composable
private fun ReadyStep(
    allGranted: Boolean,
    pinConfigured: Boolean,
    approvedCount: Int,
    onFinish: () -> Unit
) {
    Panel(accent = if (allGranted && pinConfigured) Lock46Colors.Ready else Lock46Colors.Duty) {
        StatusDot(
            if (allGranted && pinConfigured) Lock46Colors.Ready else Lock46Colors.Duty,
            if (allGranted && pinConfigured) "Ready" else "Ready — degraded"
        )
        Spacer(Modifier.height(16.dp))
        DetailRow(
            "Admin PIN",
            if (pinConfigured) "Set" else "Missing",
            valueColor = if (pinConfigured) Lock46Colors.Ready else Lock46Colors.Alert
        )
        DetailRow(
            "Required permissions",
            if (allGranted) "Granted" else "Incomplete",
            valueColor = if (allGranted) Lock46Colors.Ready else Lock46Colors.Alert
        )
        DetailRow("Essential apps", "$approvedCount approved")
        Spacer(Modifier.height(12.dp))
        Note(
            if (allGranted) {
                "LOCK46 is armed. Start a duty period from the main screen whenever you " +
                    "report for duty."
            } else {
                "LOCK46 will still run the timer, but blocked apps will not be stopped " +
                    "until the required permissions are granted in Settings."
            }
        )
    }
    Spacer(Modifier.height(24.dp))
    PrimaryAction("Finish setup", onFinish)
}

private const val TOTAL_STEPS = 5
