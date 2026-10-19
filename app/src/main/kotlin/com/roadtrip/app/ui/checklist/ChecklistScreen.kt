package com.roadtrip.app.ui.checklist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadtrip.app.di.AppContainer
import com.roadtrip.app.ui.common.OfflineBanner
import com.roadtrip.app.ui.common.SectionHeader
import com.roadtrip.app.ui.common.formatFeedTime
import com.roadtrip.core.api.Profile
import com.roadtrip.core.common.Role
import com.roadtrip.core.settings.ChecklistExpansion
import com.roadtrip.core.settings.ChecklistReducer
import com.roadtrip.core.settings.ChecklistScreenState
import com.roadtrip.core.settings.StopNameDraft
import com.roadtrip.core.settings.StopNameSave
import com.roadtrip.core.settings.StopNaming

/**
 * Checklist screen (ANDSET-003): one States section whose rows are collapsible groups of
 * their cities (ANDSET-009–012), then stops, from the cached GET /api/checklist read model;
 * states light up as crossings arrive via the event feed (applied to the cache by the sync
 * pass). Deep links from state-crossing journal rows highlight and expand the crossed state
 * (ANDJRNL-004, ANDSET-011). A parent can press-and-hold a stop row to name it
 * (ANDSET-013–016). Grouping, expansion and naming rules live in core; this only renders.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChecklistScreen(
    container: AppContainer,
    profile: Profile,
    highlightStateCode: String?,
) {
    val tick by container.refreshTick.collectAsState()
    val online by container.onlineMonitor.online.collectAsState()

    // Pending stop names overlay the cache so a saved name shows at once, offline too (ANDSET-016).
    val state: ChecklistScreenState? = remember(tick) {
        container.checklistCache.read()?.value?.let {
            ChecklistReducer.fromDto(it, container.outboxStore.pending())
        }
    }
    var naming by remember { mutableStateOf<StopNameDraft?>(null) }

    naming?.let { draft ->
        StopNameDialog(
            draft = draft,
            onSave = { name ->
                if (container.nameStop(draft.stopId, name) is StopNameSave.Saved) naming = null
            },
            onDismiss = { naming = null },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!online) OfflineBanner("Offline — showing the saved checklist")

        if (state == null) {
            Text(
                "No checklist yet — waiting for the first sync.",
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }

        // View-local, never persisted (ANDSET-011); re-seeded when a new deep link arrives.
        var expanded by remember(highlightStateCode) {
            mutableStateOf(ChecklistExpansion.initial(state.stateGroups, highlightStateCode))
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            val groups = state.stateGroups
            item { SectionHeader("States (${groups.size})") }
            groups.forEach { group ->
                val row = group.state
                val entered = row.firstEnteredAt != null
                val highlighted = row.stateCode == highlightStateCode
                val isExpanded = row.stateCode in expanded
                item(key = "state-${row.stateCode}") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = ChecklistExpansion.toggle(expanded, row.stateCode) }
                            .padding(vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = if (entered) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (entered) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        )
                        Text(
                            text = "  ${row.state} (${row.stateCode})",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (highlighted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        row.firstEnteredAt?.let {
                            Text(formatFeedTime(it), style = MaterialTheme.typography.labelSmall)
                        }
                        Text(
                            text = "  ${cityCountLabel(group.cityCount)}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse ${row.state}" else "Expand ${row.state}",
                        )
                    }
                }
                if (isExpanded) {
                    items(group.cities, key = { "city-${it.city}-${it.stateCode}" }) { city ->
                        Row(modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 4.dp, bottom = 4.dp)) {
                            Text(
                                city.city,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            city.firstAt?.let {
                                Text(formatFeedTime(it), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Stops (${state.stops.size})") }
            items(state.stops, key = { "stop-${it.stopId}" }) { row ->
                // Press-and-hold names the stop — parents only; kids get no affordance (ANDSET-014).
                val canName = profile.role == Role.PARENT
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (canName) {
                                Modifier.combinedClickable(
                                    onLongClickLabel = "Name this stop",
                                    onLongClick = { naming = StopNaming.open(row, profile.role) },
                                    onClick = {},
                                )
                            } else {
                                Modifier
                            },
                        )
                        .padding(vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.Place, contentDescription = null)
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(row.displayName, style = MaterialTheme.typography.bodyLarge)
                        val details = buildList {
                            row.startedAt?.let { add(formatFeedTime(it)) }
                            row.durationMin?.let { add("${it.toInt()} min") }
                        }
                        if (details.isNotEmpty()) {
                            Text(details.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private fun cityCountLabel(count: Int): String = if (count == 1) "1 city" else "$count cities"

/**
 * "Name this stop" (ANDSET-014/015): pre-filled by core, Save disabled while the name is
 * blank — a stop's name can be changed but never cleared.
 */
@Composable
private fun StopNameDialog(
    draft: StopNameDraft,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(draft) { mutableStateOf(draft.initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this stop") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Stop name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = StopNaming.canSave(name)) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
