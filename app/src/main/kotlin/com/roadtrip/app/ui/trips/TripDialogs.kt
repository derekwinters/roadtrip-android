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

/**
 * Create or rename the single planned "next trip" (ANDTRIP-006). The approximate start
 * stays a plain free-text field ("~ early August") — it is stored and displayed verbatim,
 * never parsed.
 */
@Composable
fun PlanTripDialog(
    initialName: String,
    initialStart: String,
    editing: Boolean,
    onConfirm: (name: String?, plannedStartAt: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var start by remember { mutableStateOf(initialStart) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) "Edit the planned trip" else "Plan the next trip") },
        text = {
            Column {
                Text("Sketch the next road trip — name it, note roughly when it starts, and stage stops from the map.")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Trip name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it },
                    label = { Text("Approximate start (optional, e.g. \"~ early August\")") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.takeIf { it.isNotBlank() }, start.takeIf { it.isNotBlank() }) }) {
                Text(if (editing) "Save" else "Plan it")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Confirm activating the planned trip ("Road trip starts now", ANDTRIP-008). */
@Composable
fun ActivatePlannedTripDialog(
    tripName: String,
    stagedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start \"$tripName\" now?") },
        text = {
            Text(
                if (stagedCount > 0) {
                    "The $stagedCount staged stop(s) become the trip's itinerary, and " +
                        "everything the family does from now on belongs to this trip."
                } else {
                    "Everything the family does from now on belongs to this trip."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Road trip starts now") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not yet") }
        },
    )
}

/** Confirm deleting the planned trip and its staged itinerary (ANDTRIP-006). */
@Composable
fun DeletePlannedTripDialog(
    tripName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete the plan \"$tripName\"?") },
        text = { Text("The planned trip and its staged stops are removed. Past trips are not affected.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete plan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep it") }
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
