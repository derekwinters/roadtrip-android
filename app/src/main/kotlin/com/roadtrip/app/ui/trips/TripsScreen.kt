package com.roadtrip.app.ui.trips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadtrip.app.di.AppContainer
import com.roadtrip.app.ui.common.OfflineBanner
import com.roadtrip.app.ui.common.SectionHeader
import com.roadtrip.app.ui.common.formatFeedTime
import com.roadtrip.core.api.Checklist
import com.roadtrip.core.api.JournalPage
import com.roadtrip.core.api.Profile
import com.roadtrip.core.api.Trip
import com.roadtrip.core.api.TripStatus
import com.roadtrip.core.api.TripSummary
import com.roadtrip.core.common.Timestamps
import com.roadtrip.core.trips.PlannerState
import com.roadtrip.core.trips.TripHistoryBrowser
import com.roadtrip.core.trips.TripPlannerReducer
import com.roadtrip.core.trips.lifecycleInstant
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One past trip's read-only views, fetched through the ?trip=<id> scoped read models. */
private data class TripDetail(
    val journal: JournalPage,
    val checklist: Checklist,
    val summary: TripSummary?,
)

/**
 * Trip history browser (ANDTRIP-003): lists trips (from the cached trips read model,
 * refreshed by the sync pass) and opens a read-only journal, checklist, summary, and
 * bingo view per trip via the core [TripHistoryBrowser]. Histories are read-only; the
 * planned "next trip" renders as its card on top instead (create/rename/delete/start
 * for parents, ANDTRIP-006).
 */
@Composable
fun TripsScreen(
    container: AppContainer,
    profile: Profile,
    initialTripId: String?,
    onOpenBingo: (String) -> Unit,
) {
    val tick by container.refreshTick.collectAsState()
    val online by container.onlineMonitor.online.collectAsState()
    var selectedTripId by remember { mutableStateOf(initialTripId) }

    // Planned trips have no history — they live on the planner card (ANDTRIP-006).
    val trips: List<Trip> = remember(tick) {
        container.tripsCache.read()?.value.orEmpty()
            .filter { it.status != TripStatus.PLANNED }
            .sortedWith(
                compareBy<Trip> { it.status != TripStatus.ACTIVE }
                    .thenByDescending { it.lifecycleInstant() },
            )
    }
    val plannerState: PlannerState = remember(tick, online, profile) {
        val allTrips = container.tripsCache.read()?.value.orEmpty()
        val staged = TripPlannerReducer.plannedTrip(allTrips)
            ?.let { container.stagedDestinationsCache(it.id).read()?.value }
            .orEmpty()
        TripPlannerReducer.reduce(allTrips, staged, profile.role, online)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!online) OfflineBanner("Offline — trip histories need the trip server")

        val selected = trips.firstOrNull { it.id == selectedTripId }
        if (selected == null) {
            TripList(
                container = container,
                trips = trips,
                plannerState = plannerState,
                onOpen = { selectedTripId = it.id },
            )
        } else {
            TripDetailView(
                container = container,
                trip = selected,
                online = online,
                onBack = { selectedTripId = null },
                onOpenBingo = onOpenBingo,
            )
        }
    }
}

@Composable
private fun TripList(
    container: AppContainer,
    trips: List<Trip>,
    plannerState: PlannerState,
    onOpen: (Trip) -> Unit,
) {
    var showPlanDialog by remember { mutableStateOf(false) }
    var showActivateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val tripError by container.tripActionError.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // The planned "next trip" card (ANDTRIP-006/007) — read-only for kids.
        val plannedCard = plannerState.card
        if (plannedCard != null || plannerState.planAction.visible) {
            item { SectionHeader("Next trip") }
        }
        if (plannedCard != null) {
            item {
                PlannedTripCardView(
                    card = plannedCard,
                    tripError = tripError,
                    onStart = { showActivateDialog = true },
                    onEdit = { showPlanDialog = true },
                    onDelete = { showDeleteDialog = true },
                )
            }
        } else if (plannerState.planAction.visible) {
            item {
                TextButton(
                    onClick = { showPlanDialog = true },
                    enabled = plannerState.planAction.enabled,
                ) {
                    Text("Plan the next trip")
                }
            }
        }

        item { SectionHeader("Road trips") }
        if (trips.isEmpty()) {
            item {
                Text(
                    "No road trips yet — this is where past trips will live.",
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        items(trips, key = { it.id }) { trip ->
            Card(
                onClick = { onOpen(trip) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            trip.name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (trip.status == TripStatus.ACTIVE) "active" else "ended",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (trip.status == TripStatus.ACTIVE) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(tripDates(trip), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    if (showPlanDialog) {
        val editingTrip = plannerState.card?.trip
        PlanTripDialog(
            initialName = editingTrip?.name.orEmpty(),
            initialStart = editingTrip?.plannedStartAt.orEmpty(),
            editing = editingTrip != null,
            onConfirm = { name, plannedStartAt ->
                showPlanDialog = false
                if (editingTrip == null) {
                    container.createPlannedTrip(name, plannedStartAt)
                } else {
                    container.updatePlannedTrip(editingTrip.id, name, plannedStartAt)
                }
            },
            onDismiss = { showPlanDialog = false },
        )
    }
    plannerState.card?.let { card ->
        if (showActivateDialog) {
            ActivatePlannedTripDialog(
                tripName = card.trip.name,
                stagedCount = card.itinerary.size,
                onConfirm = {
                    showActivateDialog = false
                    container.activatePlannedTrip(card.trip.id)
                },
                onDismiss = { showActivateDialog = false },
            )
        }
        if (showDeleteDialog) {
            DeletePlannedTripDialog(
                tripName = card.trip.name,
                onConfirm = {
                    showDeleteDialog = false
                    container.deletePlannedTrip(card.trip.id)
                },
                onDismiss = { showDeleteDialog = false },
            )
        }
    }
}

@Composable
private fun TripDetailView(
    container: AppContainer,
    trip: Trip,
    online: Boolean,
    onBack: () -> Unit,
    onOpenBingo: (String) -> Unit,
) {
    var detail by remember(trip.id) { mutableStateOf<TripDetail?>(null) }
    var error by remember(trip.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(trip.id, online) {
        if (!online) return@LaunchedEffect
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val browser = TripHistoryBrowser(container.api)
                TripDetail(
                    journal = browser.journal(trip.id),
                    checklist = browser.checklist(trip.id),
                    summary = runCatching { browser.summary(trip.id) }.getOrNull(),
                )
            }
        }
        result.fold(
            onSuccess = { detail = it },
            onFailure = { error = "Couldn't load this trip's history — check the connection." },
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(trip.name, style = MaterialTheme.typography.titleMedium)
                    Text("${tripDates(trip)} · read-only", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onBack) { Text("All trips") }
            }
        }

        val loaded = detail
        when {
            error != null -> item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            loaded == null -> item {
                Text(
                    if (online) "Loading trip history…" else "Connect to the trip server to browse this trip.",
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            else -> {
                val summary = loaded.summary
                if (summary != null) {
                    item { SectionHeader("Summary") }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "${fmt(summary.miles)} mi · ${fmt(summary.wallMinutes / 60.0)} h total · " +
                                        "${fmt(summary.movingMinutes / 60.0)} h moving",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "${summary.statesCount} states · ${summary.stopCount} stops · " +
                                        "${summary.gamesPlayed} games",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                // The trip's license-plate bingo card, read-only (ANDBNG-004).
                item {
                    TextButton(onClick = { onOpenBingo(trip.id) }) {
                        Text("License plate bingo card")
                    }
                }

                item { SectionHeader("States visited") }
                if (loaded.checklist.states.isEmpty()) {
                    item { Text("No states recorded.", style = MaterialTheme.typography.bodyMedium) }
                } else {
                    item {
                        Text(
                            loaded.checklist.states.joinToString(", ") { it.stateCode },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                item { SectionHeader("Journal") }
                if (loaded.journal.entries.isEmpty()) {
                    item { Text("No journal entries.", style = MaterialTheme.typography.bodyMedium) }
                } else {
                    items(loaded.journal.entries, key = { it.seq }) { entry ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(entry.text, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    formatFeedTime(Timestamps.parse(entry.ts)),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun tripDates(trip: Trip): String {
    val start = trip.startedAt?.let { formatFeedTime(Timestamps.parse(it)) }
        ?: return trip.plannedStartAt?.let { "planned $it" } ?: "planned"
    val end = trip.endedAt?.let { formatFeedTime(Timestamps.parse(it)) }
    return if (end != null) "$start — $end" else "since $start"
}

private fun fmt(value: Double): String = String.format(Locale.US, "%.1f", value)
