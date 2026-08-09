package com.lock46.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lock46.app.Graph
import com.lock46.app.core.PinHasher
import com.lock46.app.core.PinRepository
import com.lock46.app.ui.theme.Lock46Colors
import com.lock46.app.util.TimeFormat

/**
 * Sets or changes the admin PIN.
 *
 * When a PIN already exists the current one is required, so someone who picks up an
 * unlocked phone mid-duty cannot simply overwrite it and end duty.
 */
@Composable
fun ChangePinDialog(onDismiss: () -> Unit) {
    val configured = Graph.pin.isConfigured()

    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lock46Colors.PanelRaised,
        titleContentColor = Lock46Colors.TextPrimary,
        textContentColor = Lock46Colors.TextSecondary,
        title = {
            Text(
                if (configured) "CHANGE ADMIN PIN" else "SET ADMIN PIN",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                if (done) {
                    Text("PIN updated.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "${PinHasher.MIN_LENGTH}–${PinHasher.MAX_LENGTH} digits. Not a " +
                            "repeated digit and not a simple run.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(14.dp))
                    if (configured) {
                        PinField(
                            value = currentPin,
                            onValueChange = {
                                currentPin = it.filter(Char::isDigit).take(PinHasher.MAX_LENGTH)
                            },
                            label = "Current PIN"
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    PinField(
                        value = newPin,
                        onValueChange = {
                            newPin = it.filter(Char::isDigit).take(PinHasher.MAX_LENGTH)
                        },
                        label = "New PIN"
                    )
                    Spacer(Modifier.height(10.dp))
                    PinField(
                        value = confirmPin,
                        onValueChange = {
                            confirmPin = it.filter(Char::isDigit).take(PinHasher.MAX_LENGTH)
                        },
                        label = "Confirm new PIN"
                    )
                    if (error != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = Lock46Colors.Alert
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (done) {
                        onDismiss()
                        return@TextButton
                    }
                    error = validateAndApply(configured, currentPin, newPin, confirmPin)
                    if (error == null) done = true
                }
            ) {
                Text(if (done) "CLOSE" else "SAVE", color = Lock46Colors.Duty)
            }
        },
        dismissButton = if (done) {
            null
        } else {
            {
                TextButton(onClick = onDismiss) {
                    Text("CANCEL", color = Lock46Colors.TextSecondary)
                }
            }
        }
    )
}

/** Returns an error message, or null when the PIN was changed successfully. */
private fun validateAndApply(
    configured: Boolean,
    currentPin: String,
    newPin: String,
    confirmPin: String
): String? {
    if (newPin != confirmPin) return "The two entries do not match."

    when (val validation = PinHasher.validate(newPin)) {
        is PinHasher.PinValidation.Rejected -> return validation.reason
        is PinHasher.PinValidation.Ok -> Unit
    }

    if (!configured) {
        Graph.pin.setPin(newPin)
        return null
    }

    return when (val result = Graph.pin.changePin(currentPin, newPin, System.currentTimeMillis())) {
        is PinRepository.Result.Accepted -> null
        is PinRepository.Result.Rejected ->
            "Current PIN is incorrect. ${result.attemptsRemaining} attempt(s) remaining."
        is PinRepository.Result.LockedOut ->
            "Too many attempts. Try again in ${TimeFormat.compact(result.retryAfterMs)}."
        is PinRepository.Result.NotConfigured ->
            "No PIN is configured."
    }
}
