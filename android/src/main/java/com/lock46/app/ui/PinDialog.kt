package com.lock46.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lock46.app.core.PinHasher
import com.lock46.app.ui.theme.Lock46Colors

/**
 * Modal PIN prompt.
 *
 * The entered value lives only in this composable's state and is handed straight to
 * [com.lock46.app.core.PinRepository]; it is never logged, never persisted and never put
 * into an intent extra.
 */
@Composable
fun PinPromptDialog(
    title: String,
    message: String,
    confirmLabel: String,
    errorText: String?,
    enabled: Boolean = true,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lock46Colors.PanelRaised,
        titleContentColor = Lock46Colors.TextPrimary,
        textContentColor = Lock46Colors.TextSecondary,
        title = { Text(title.uppercase(), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                PinField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(PinHasher.MAX_LENGTH) },
                    enabled = enabled,
                    onImeDone = { if (enabled && pin.isNotEmpty()) onConfirm(pin) }
                )
                if (errorText != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Lock46Colors.Alert
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pin) },
                enabled = enabled && pin.length >= PinHasher.MIN_LENGTH
            ) {
                Text(confirmLabel.uppercase(), color = Lock46Colors.Duty)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Lock46Colors.TextSecondary)
            }
        }
    )
}

/** Numeric, masked, monospaced PIN input. */
@Composable
fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "PIN",
    enabled: Boolean = true,
    onImeDone: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = { onImeDone() }
        ),
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Lock46Colors.Duty,
            unfocusedBorderColor = Lock46Colors.Line,
            focusedTextColor = Lock46Colors.TextPrimary,
            unfocusedTextColor = Lock46Colors.TextPrimary,
            cursorColor = Lock46Colors.Duty,
            focusedLabelColor = Lock46Colors.TextSecondary,
            unfocusedLabelColor = Lock46Colors.TextDim
        ),
        modifier = modifier.fillMaxWidth()
    )
}
