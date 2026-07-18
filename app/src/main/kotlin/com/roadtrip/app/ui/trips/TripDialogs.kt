package com.roadtrip.app.ui.trips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Confirm dialogs for the parent-only, online-only trip lifecycle actions (ANDTRIP-001/004).
 * The server arbitrates the single active trip; these only confirm intent.
 */

@Composable
fun StartTripDialog(
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Road trip starts now?") },
        text = {
            Column {
                Text("Everything the family does from now on belongs to this trip.")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Trip name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.takeIf { it.isNotBlank() }) }) {
                Text("Start the trip")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not yet") }
        },
    )
}

@Composable
fun EndTripDialog(
    tripName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("End \"$tripName\"?") },
        text = {
            Text(
                "The trip's journal, checklist, and summary freeze as a browsable " +
                    "history. Location tracking stops until the next trip starts.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("End the trip") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep going") }
        },
    )
}
