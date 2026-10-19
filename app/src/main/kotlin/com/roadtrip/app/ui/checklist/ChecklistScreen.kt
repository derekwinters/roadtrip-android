package com.roadtrip.app.ui.checklist

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.roadtrip.core.settings.ChecklistExpansion
import com.roadtrip.core.settings.ChecklistReducer
import com.roadtrip.core.settings.ChecklistScreenState

/**
 * Checklist screen (ANDSET-003): one States section whose rows are collapsible groups of
 * their cities (ANDSET-009–012), then stops, from the cached GET /api/checklist read model;
 * states light up as crossings arrive via the event feed (applied to the cache by the sync
 * pass). Deep links from state-crossing journal rows highlight and expand the crossed state
 * (ANDJRNL-004, ANDSET-011). Grouping and expansion rules live in core; this only renders.
 */
@Composable
fun ChecklistScreen(
    container: AppContainer,
    highlightStateCode: String?,
) {
    val tick by container.refreshTick.collectAsState()
    val online by container.onlineMonitor.online.collectAsState()

    val state: ChecklistScreenState? = remember(tick) {
        container.checklistCache.read()?.value?.let(ChecklistReducer::fromDto)
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
            items(state.stops) { row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.Place, contentDescription = null)
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            row.place ?: "%.4f, %.4f".let {
                                String.format(java.util.Locale.US, it, row.lat, row.lon)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
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
