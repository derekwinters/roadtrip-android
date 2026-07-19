package com.roadtrip.app.ui.trip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.roadtrip.core.settings.LegRow
import com.roadtrip.core.settings.TripReducer
import com.roadtrip.core.settings.TripScreenState
import java.util.Locale

/**
 * Trip screen (ANDSET-004): whole-trip summary (miles, hours wall/moving, states, stops,
 * games) plus per-leg summaries. A leg-arrival deep link highlights that leg
 * (ANDJRNL-004). Past trips are reachable through the history browser (ANDTRIP-003).
 */
@Composable
fun TripScreen(
    container: AppContainer,
    highlightDestinationId: String?,
    onOpenHistory: () -> Unit = {},
) {
    val tick by container.refreshTick.collectAsState()
    val online by container.onlineMonitor.online.collectAsState()

    val state: TripScreenState? = remember(tick) {
        val legs = container.legsCache.read()?.value
        val summary = container.tripSummaryCache.read()?.value
        if (legs == null || summary == null) return@remember null
        val profiles = container.profilesCache.read()?.value.orEmpty().associateBy { it.id }
        TripReducer.reduce(legs, summary, profiles)
    }

    val legDestinations: Map<Int, String?> = remember(tick) {
        container.legsCache.read()?.value.orEmpty().associate { it.legIndex to it.destinationId }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!online) OfflineBanner("Offline — showing saved trip summaries")

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "This trip",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenHistory) { Text("Past trips") }
        }

        if (state == null) {
            Text(
                "No trip data yet — waiting for the first sync.",
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item { SectionHeader("Whole trip") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "${fmt(state.summary.miles)} mi · ${fmt(state.summary.wallHours)} h total · " +
                                "${fmt(state.summary.movingHours)} h moving",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "${state.summary.statesCount} states · ${state.summary.stopCount} stops · " +
                                "${state.summary.gamesPlayed} games",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            item { SectionHeader("Legs") }
            items(state.legs, key = { it.legIndex }) { leg ->
                val highlighted = highlightDestinationId != null &&
                    legDestinations[leg.legIndex] == highlightDestinationId
                LegCard(leg, highlighted)
            }
        }
    }
}

@Composable
private fun LegCard(leg: LegRow, highlighted: Boolean) {
    OutlinedCard(
        border = if (highlighted) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            CardDefaults.outlinedCardBorder()
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Leg ${leg.legIndex + 1}: ${leg.destinationName ?: "…"}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (leg.arrived) "arrived" else "en route",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (leg.arrived) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            val details = buildList {
                leg.miles?.let { add("${fmt(it)} mi") }
                leg.wallHours?.let { add("${fmt(it)} h") }
                leg.movingHours?.let { add("${fmt(it)} h moving") }
                leg.stopCount?.let { add("$it stops") }
                leg.gamesPlayed?.let { add("$it games") }
            }
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
            if (leg.states.isNotEmpty()) {
                Text("States: ${leg.states.joinToString(", ")}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun fmt(value: Double): String = String.format(Locale.US, "%.1f", value)
