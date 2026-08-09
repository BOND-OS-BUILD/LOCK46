package com.lock46.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lock46.app.core.DutyDuration
import com.lock46.app.ui.theme.Lock46Colors

/**
 * Duration chooser: the five presets plus a custom hours/minutes entry.
 *
 * The confirm button stays disabled until the selection produces a duration that passes
 * [DutyDuration.isValid], so an invalid period can never reach [com.lock46.app.core.DutyRepository].
 */
@Composable
fun DurationPickerDialog(
    initialMs: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPreset by remember {
        mutableStateOf(if (initialMs in DutyDuration.PRESETS_MS) initialMs else DutyDuration.DEFAULT_MS)
    }
    var custom by remember { mutableStateOf(false) }
    var hoursText by remember { mutableStateOf("1") }
    var minutesText by remember { mutableStateOf("0") }

    val customMs: Long? = DutyDuration.fromHoursMinutes(
        hoursText.toIntOrNull() ?: -1,
        minutesText.toIntOrNull() ?: -1
    )
    val resolved: Long? = if (custom) customMs else selectedPreset

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lock46Colors.PanelRaised,
        titleContentColor = Lock46Colors.TextPrimary,
        title = { Text("DUTY DURATION", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                DutyDuration.PRESETS_MS.forEach { preset ->
                    DurationOption(
                        label = DutyDuration.label(preset),
                        selected = !custom && selectedPreset == preset,
                        onSelect = {
                            custom = false
                            selectedPreset = preset
                        }
                    )
                }
                DurationOption(
                    label = "Custom",
                    selected = custom,
                    onSelect = { custom = true }
                )

                if (custom) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NumberField(
                            value = hoursText,
                            onValueChange = { hoursText = it.filter(Char::isDigit).take(2) },
                            label = "Hours",
                            modifier = Modifier.width(110.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        NumberField(
                            value = minutesText,
                            onValueChange = { minutesText = it.filter(Char::isDigit).take(2) },
                            label = "Minutes",
                            modifier = Modifier.width(110.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Note(
                        text = if (customMs == null) {
                            "Enter between 1 minute and 24 hours."
                        } else {
                            "Duty period: ${DutyDuration.label(customMs)}"
                        },
                        color = if (customMs == null) Lock46Colors.Alert else Lock46Colors.TextSecondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { resolved?.let(onConfirm) },
                enabled = resolved != null
            ) {
                Text("START DUTY", color = Lock46Colors.Duty)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Lock46Colors.TextSecondary)
            }
        }
    )
}

@Composable
private fun DurationOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = Lock46Colors.Duty,
                unselectedColor = Lock46Colors.TextDim
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) Lock46Colors.TextPrimary else Lock46Colors.TextSecondary
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Lock46Colors.Duty,
            unfocusedBorderColor = Lock46Colors.Line,
            focusedTextColor = Lock46Colors.TextPrimary,
            unfocusedTextColor = Lock46Colors.TextPrimary,
            cursorColor = Lock46Colors.Duty,
            focusedLabelColor = Lock46Colors.TextSecondary,
            unfocusedLabelColor = Lock46Colors.TextDim
        ),
        modifier = modifier
    )
}
