package com.roadtrip.app.ui.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.roadtrip.app.di.AppContainer
import com.roadtrip.app.ui.common.OfflineBanner
import com.roadtrip.app.ui.common.SectionHeader
import com.roadtrip.app.ui.common.formatFeedTime
import com.roadtrip.core.api.BingoCard
import com.roadtrip.core.api.BingoLogAction
import com.roadtrip.core.api.Profile
import com.roadtrip.core.bingo.BingoCellView
import com.roadtrip.core.bingo.BingoReducer
import com.roadtrip.core.bingo.BingoScreenState
import com.roadtrip.core.sync.OutboxEntry
import com.roadtrip.core.trips.TripHistoryBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * License plate bingo (docs/spec/10-bingo.md): the 50-states + DC grid, tap-to-spot with
 * confirm (any profile, offline-queued with a pending marker, ANDBNG-001), a detail sheet
 * with the spotter-or-parent Remove action (ANDBNG-002), the chronological log
 * (ANDBNG-003), and the per-profile leaderboard (ANDBNG-004). With [historyTripId] set it
 * renders a past trip's card read-only from the ?trip= scoped read model (ANDBNG-004).
 */
@Composable
fun BingoScreen(
    container: AppContainer,
    profile: Profile,
    historyTripId: String?,
) {
    val tick by container.refreshTick.collectAsState()
    val online by container.onlineMonitor.online.collectAsState()

    val profilesById = remember(tick) {
        container.profilesCache.read()?.value.orEmpty().associateBy { it.id }
    }

    // Read-only history variant: a past trip's card via ?trip=<id> (ANDBNG-004).
    var historyCard by remember(historyTripId) { mutableStateOf<BingoCard?>(null) }
    var historyError by remember(historyTripId) { mutableStateOf<String?>(null) }
    if (historyTripId != null) {
        LaunchedEffect(historyTripId, online) {
            if (!online) return@LaunchedEffect
            historyError = null
            val result = withContext(Dispatchers.IO) {
                runCatching { TripHistoryBrowser(container.api).bingo(historyTripId) }
            }
            result.fold(
                onSuccess = { historyCard = it },
                onFailure = { historyError = "Couldn't load this trip's bingo card — check the connection." },
            )
        }
    }

    val state: BingoScreenState = remember(tick, historyCard, online) {
        if (historyTripId != null) {
            BingoReducer.reduce(
                card = historyCard ?: BingoCard(),
                pendingOutbox = emptyList(),
                selfProfile = profile,
                profilesById = profilesById,
                readOnly = true,
            )
        } else {
            val pending = container.outboxStore.pending().filter {
                it.type == OutboxEntry.TYPE_PLATE_SPOTTED || it.type == OutboxEntry.TYPE_PLATE_UNSPOTTED
            }
            BingoReducer.reduce(
                card = container.bingoCache.read()?.value ?: BingoCard(),
                pendingOutbox = pending,
                selfProfile = profile,
                profilesById = profilesById,
            )
        }
    }

    var confirmSpot by remember { mutableStateOf<BingoCellView?>(null) }
    var detailCell by remember { mutableStateOf<BingoCellView?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (historyTripId != null && !online && historyCard == null) {
            OfflineBanner("Connect to the trip server to browse this trip's bingo card")
        }
        historyError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 72.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Leaderboard row: standing counts per profile (ANDBNG-004).
            item(span = { GridItemSpan(maxLineSpan) }) {
                val spottedCount = state.cells.count { it.spotted }
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        "$spottedCount of ${state.cells.size} spotted" +
                            if (state.readOnly) " · read-only" else "",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (state.standings.isNotEmpty()) {
                        Text(
                            state.standings.joinToString(" · ") { "${it.profileName} ${it.count}" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(state.cells, key = { it.stateCode }) { cell ->
                BingoCellBox(
                    cell = cell,
                    onClick = {
                        if (cell.spotted) detailCell = cell else if (cell.canSpot) confirmSpot = cell
                    },
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Log") }
            if (state.log.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("No plates spotted yet.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            // Chronological history from the backend log (ANDBNG-003).
            items(
                items = state.log,
                span = { GridItemSpan(maxLineSpan) },
            ) { row ->
                Text(
                    text = buildString {
                        append(formatFeedTime(row.ts))
                        append(" — ")
                        append(row.profileName)
                        append(
                            when (row.action) {
                                BingoLogAction.SPOTTED -> " spotted "
                                BingoLogAction.REMOVED -> " removed "
                            },
                        )
                        append(row.stateName)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }

    // Tap + confirm to spot an empty cell (ANDBNG-001).
    confirmSpot?.let { cell ->
        AlertDialog(
            onDismissRequest = { confirmSpot = null },
            title = { Text("Spotted a ${cell.stateName} plate?") },
            text = { Text("It counts for you, ${profile.name} — saved offline and synced later if needed.") },
            confirmButton = {
                TextButton(onClick = {
                    container.spotPlate(cell.stateCode)
                    confirmSpot = null
                }) {
                    Text("Spotted it!")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSpot = null }) { Text("Cancel") }
            },
        )
    }

    // Detail sheet for a filled cell; Remove for the spotter or a parent (ANDBNG-002).
    detailCell?.let { cell ->
        AlertDialog(
            onDismissRequest = { detailCell = null },
            title = { Text(cell.stateName) },
            text = {
                Column {
                    Text("Spotted by ${cell.spottedByName ?: "someone"}")
                    cell.spottedAt?.let { Text(formatFeedTime(it), style = MaterialTheme.typography.labelMedium) }
                    if (cell.pending) {
                        Text("Syncing…", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                if (cell.canRemove) {
                    TextButton(onClick = {
                        container.unspotPlate(cell.stateCode)
                        detailCell = null
                    }) {
                        Text("Remove")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { detailCell = null }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun BingoCellBox(cell: BingoCellView, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = cell.spotted || cell.canSpot,
        colors = CardDefaults.cardColors(
            containerColor = if (cell.spotted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier.aspectRatio(1f),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                cell.stateCode,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = when {
                    cell.pending -> "syncing…"
                    cell.spotted -> cell.spottedByName ?: ""
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
