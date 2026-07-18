package com.roadtrip.app.ui.trips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadtrip.core.trips.PlannedTripCard

/**
 * The planned "next trip" card (ANDTRIP-006/007): name, verbatim approximate start,
 * staged itinerary preview, and — for parents, online — the activate/edit/delete actions.
 * Kids get the same card with every action hidden (read-only by reducer construction).
 */
@Composable
fun PlannedTripCardView(
    card: PlannedTripCard,
    tripError: String?,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Next trip: ${card.trip.name}",
                style = MaterialTheme.typography.titleSmall,
            )
            card.plannedStartText?.let {
                Text("Planned start: $it", style = MaterialTheme.typography.labelMedium)
            }
            if (card.itinerary.isEmpty()) {
                Text(
                    if (card.canStageDestinations) {
                        "No stops staged yet — add them from the map screen."
                    } else {
                        "No stops staged yet."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "Itinerary: " + card.itinerary.joinToString(" → ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row {
                if (card.startAction.visible) {
                    TextButton(onClick = onStart, enabled = card.startAction.enabled) {
                        Text("Road trip starts now")
                    }
                }
                if (card.renameAction.visible) {
                    TextButton(onClick = onEdit, enabled = card.renameAction.enabled) {
                        Text("Edit")
                    }
                }
                if (card.deleteAction.visible) {
                    TextButton(onClick = onDelete, enabled = card.deleteAction.enabled) {
                        Text("Delete")
                    }
                }
            }
            val disabledReason = card.startAction.disabledReason ?: card.renameAction.disabledReason
            if (disabledReason != null) {
                Text(
                    disabledReason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            tripError?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
