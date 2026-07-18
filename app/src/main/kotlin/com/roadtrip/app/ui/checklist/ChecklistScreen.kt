package com.roadtrip.app.ui.checklist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadtrip.app.di.AppContainer
import com.roadtrip.app.ui.common.OfflineBanner
import com.roadtrip.app.ui.common.SectionHeader
import com.roadtrip.app.ui.common.formatFeedTime
import com.roadtrip.core.settings.ChecklistReducer
import com.roadtrip.core.settings.ChecklistScreenState

/**
 * Checklist screen (ANDSET-003): states with first-entered times, cities, and stops from
 * the cached GET /api/checklist read model; states light up as crossings arrive via the
 * event feed (applied to the cache by the sync pass). Deep links from state-crossing
 * journal rows highlight the crossed state (ANDJRNL-004).
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

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item { SectionHeader("States (${state.states.size})") }
            items(state.states, key = { "state-${it.stateCode}" }) { row ->
                val entered = row.firstEnteredAt != null
                val highlighted = row.stateCode == highlightStateCode
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                }
            }

            item { SectionHeader("Cities (${state.cities.size})") }
            items(state.cities, key = { "city-${it.city}-${it.stateCode}" }) { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        "${row.city}, ${row.stateCode}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    row.firstAt?.let {
                        Text(formatFeedTime(it), style = MaterialTheme.typography.labelSmall)
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
