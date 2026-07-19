package com.roadtrip.app.ui.map

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.roadtrip.app.R
import com.roadtrip.app.di.AppContainer
import com.roadtrip.core.api.Destination
import com.roadtrip.core.api.DestinationStatus
import com.roadtrip.core.api.Profile
import com.roadtrip.core.api.Trip
import com.roadtrip.core.api.TripStatus
import com.roadtrip.core.common.Role
import com.roadtrip.core.map.AddressSearch
import com.roadtrip.core.map.AddressSearchState
import com.roadtrip.core.map.MapMarker
import com.roadtrip.core.map.MapScreenReducer
import com.roadtrip.core.map.MapScreenState
import com.roadtrip.core.map.MarkerKind
import com.roadtrip.core.map.MarkerStyle
import com.roadtrip.core.map.markerStyleFor
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * "You are here" map (docs/spec/02-map.md). osmdroid renders OSM tiles with its on-device
 * cache so previously viewed regions work offline (ANDMAP-007). Markers/breadcrumb/progress
 * come from the core MapScreenReducer (kid vs parent marker rules, ANDMAP-001); offline
 * renders the cached state with a freshness label (ANDMAP-004). Parents can long-press to
 * add a destination and edit/reorder/remove pending ones (ANDMAP-006); kids never see those
 * affordances (AND-003).
 */
@Composable
fun MapScreen(
    container: AppContainer,
    profile: Profile,
    centerLat: Double?,
    centerLon: Double?,
) {
    val tick by container.refreshTick.collectAsState()
    val online by container.onlineMonitor.online.collectAsState()
    val isParent = profile.role == Role.PARENT
    val zone = remember { ZoneId.systemDefault() }

    val state: MapScreenState? = remember(tick, online) {
        val cached = container.mapCache.read() ?: return@remember null
        val destinations = container.destinationsCache.read()?.value.orEmpty()
        if (online) {
            MapScreenReducer.fromServer(cached.value, destinations, profile.role, zone)
        } else {
            MapScreenReducer.fromCache(cached, destinations, profile.role, zone)
        }
    }

    // With no active trip, parents stage the planned trip's itinerary from this screen —
    // same panel, writes scoped by ?trip=<plannedId> (ANDTRIP-007, online-only).
    val stagingTrip: Trip? = remember(tick) {
        val trips = container.tripsCache.read()?.value.orEmpty()
        if (trips.none { it.status == TripStatus.ACTIVE }) {
            trips.firstOrNull { it.status == TripStatus.PLANNED }
        } else {
            null
        }
    }
    val stagedDestinations: List<Destination> = remember(tick, stagingTrip) {
        stagingTrip?.let { container.stagedDestinationsCache(it.id).read()?.value }.orEmpty()
    }
    val stagingEnabled = stagingTrip == null || online

    var longPressPoint by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var showCoordinateDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            OsmMap(
                state = state,
                centerLat = centerLat,
                centerLon = centerLon,
                // Staging the planned trip is online-only (ANDTRIP-007).
                allowLongPress = isParent && stagingEnabled,
                onLongPress = { lat, lon -> longPressPoint = lat to lon },
            )

            // Progress panel from GET /api/map fields only (ANDMAP-002).
            state?.let { mapState ->
                Card(modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "This leg: ${mapState.progress.legMilesText} · remaining ${mapState.progress.remainingText}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        mapState.progress.lastUpdateText?.let {
                            Text("Last update $it", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Offline freshness label (ANDMAP-004).
                mapState.freshnessLabel?.let { label ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                    ) {
                        Text(
                            "Offline — $label",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (state == null) {
                Text(
                    "No map data yet — waiting for the first sync.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
        }

        if (isParent) {
            DestinationPanel(
                container = container,
                destinations = if (stagingTrip != null) stagedDestinations else state?.destinationList.orEmpty(),
                stagingTrip = stagingTrip,
                stagingEnabled = stagingEnabled,
                onAddByCoordinates = { showCoordinateDialog = true },
            )
        }
    }

    longPressPoint?.let { (lat, lon) ->
        AddDestinationDialog(
            container = container,
            role = profile.role,
            lat = lat,
            lon = lon,
            editableCoordinates = false,
            onDismiss = { longPressPoint = null },
            onConfirm = { name, dLat, dLon ->
                container.addDestination(name, dLat, dLon, stagingTrip?.id)
                longPressPoint = null
            },
        )
    }

    if (showCoordinateDialog) {
        AddDestinationDialog(
            container = container,
            role = profile.role,
            lat = null,
            lon = null,
            editableCoordinates = true,
            onDismiss = { showCoordinateDialog = false },
            onConfirm = { name, dLat, dLon ->
                container.addDestination(name, dLat, dLon, stagingTrip?.id)
                showCoordinateDialog = false
            },
        )
    }
}

@Composable
private fun OsmMap(
    state: MapScreenState?,
    centerLat: Double?,
    centerLon: Double?,
    allowLongPress: Boolean,
    onLongPress: (Double, Double) -> Unit,
) {
    val longPress = rememberUpdatedState(onLongPress)
    val longPressEnabled = rememberUpdatedState(allowLongPress)
    var centered by remember { mutableStateOf(false) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            configureOsmdroid(context)
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(5.5)
                controller.setCenter(OsmGeoPoint(39.5, -98.35)) // continental US until data arrives
                overlays.add(
                    MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: OsmGeoPoint?): Boolean = false

                        override fun longPressHelper(p: OsmGeoPoint?): Boolean {
                            if (p == null || !longPressEnabled.value) return false
                            longPress.value(p.latitude, p.longitude)
                            return true
                        }
                    }),
                )
                onResume()
            }
        },
        update = { mapView ->
            if (state != null) {
                // Rebuild overlays from reducer output; the events overlay stays first.
                val eventsOverlay = mapView.overlays.firstOrNull { it is MapEventsOverlay }
                mapView.overlays.clear()
                eventsOverlay?.let { mapView.overlays.add(it) }

                if (state.polyline.size >= 2) {
                    val line = Polyline(mapView)
                    line.setPoints(state.polyline.map { OsmGeoPoint(it.lat, it.lon) })
                    line.outlinePaint.color = android.graphics.Color.argb(200, 33, 100, 200)
                    line.outlinePaint.strokeWidth = 8f
                    mapView.overlays.add(line)
                }

                for (marker in state.markers) {
                    val osmMarker = Marker(mapView)
                    osmMarker.position = OsmGeoPoint(marker.lat, marker.lon)
                    osmMarker.title = markerTitle(marker)
                    // Distinct, kind-based iconography, center-anchored on the point so the
                    // glyph sits over the location rather than a bottom-anchored teardrop pin
                    // (ANDMAP-010). markerStyleFor is the pure core mapping; this only picks
                    // the drawable — it does not change which markers a role sees (ANDMAP-001).
                    ContextCompat.getDrawable(mapView.context, markerDrawableRes(marker.kind))
                        ?.let { osmMarker.icon = it }
                    osmMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    mapView.overlays.add(osmMarker)
                }

                if (!centered) {
                    centered = true
                    val target = when {
                        centerLat != null && centerLon != null -> OsmGeoPoint(centerLat, centerLon)
                        else -> state.markers.firstOrNull { it.kind == MarkerKind.CURRENT }
                            ?.let { OsmGeoPoint(it.lat, it.lon) }
                    }
                    if (target != null) {
                        mapView.controller.setZoom(11.0)
                        mapView.controller.setCenter(target)
                    }
                }
                mapView.invalidate()
            }
        },
    )
}

private fun markerTitle(marker: MapMarker): String = when (marker.kind) {
    MarkerKind.START -> marker.label ?: "Start"
    MarkerKind.CURRENT -> "We are here"
    MarkerKind.ACTIVE_DESTINATION -> "Next: ${marker.label ?: "destination"}"
    MarkerKind.DESTINATION -> marker.label ?: "Destination"
}

/** Resolves each marker kind to its drawable via the pure core style mapping (ANDMAP-010). */
private fun markerDrawableRes(kind: MarkerKind): Int = when (markerStyleFor(kind)) {
    MarkerStyle.CAR -> R.drawable.ic_map_car
    MarkerStyle.RED_DOT -> R.drawable.ic_map_dot_start
    MarkerStyle.GREEN_DOT_ACTIVE -> R.drawable.ic_map_dot_destination_active
    MarkerStyle.GREEN_DOT -> R.drawable.ic_map_dot_destination
}

private fun configureOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    // Required by the OSM tile usage policy; also keys the on-device tile cache (ANDMAP-007).
    config.userAgentValue = context.packageName
}

/**
 * Parent-only destination admin (ANDMAP-006). With [stagingTrip] set (no active trip,
 * a planned one exists) the same panel edits the planned trip's staged itinerary via
 * `?trip=<plannedId>` — online-only like all destination admin (ANDTRIP-007).
 */
@Composable
private fun DestinationPanel(
    container: AppContainer,
    destinations: List<Destination>,
    stagingTrip: Trip?,
    stagingEnabled: Boolean,
    onAddByCoordinates: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (stagingTrip != null) "Planned trip stops" else "Destinations",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAddByCoordinates, enabled = stagingEnabled) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Address / coordinates")
            }
        }
        Text(
            when {
                stagingTrip != null && !stagingEnabled ->
                    "Offline — staging \"${stagingTrip.name}\" needs the trip server."
                stagingTrip != null ->
                    "Staging \"${stagingTrip.name}\" — this itinerary is adopted when the trip starts. " +
                        "Long-press the map to add a stop."
                else -> "Long-press the map to add a stop."
            },
            style = MaterialTheme.typography.labelSmall,
        )
        LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
            items(destinations, key = { it.id }) { destination ->
                DestinationRow(container, destination, destinations, stagingTrip?.id, stagingEnabled)
            }
        }
    }
}

@Composable
private fun DestinationRow(
    container: AppContainer,
    destination: Destination,
    all: List<Destination>,
    stagingTripId: String?,
    stagingEnabled: Boolean,
) {
    val pending = destination.status == DestinationStatus.PENDING
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "${destination.orderIndex + 1}. ${destination.name}" + when (destination.status) {
                DestinationStatus.ACTIVE -> "  (next)"
                DestinationStatus.ARRIVED -> "  (arrived)"
                DestinationStatus.PENDING -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (pending) {
            IconButton(
                onClick = { reorder(container, destination, all, -1, stagingTripId) },
                enabled = stagingEnabled,
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
            }
            IconButton(
                onClick = { reorder(container, destination, all, +1, stagingTripId) },
                enabled = stagingEnabled,
            ) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
            }
            IconButton(
                onClick = { container.removeDestination(destination.id, stagingTripId) },
                enabled = stagingEnabled,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove")
            }
        }
    }
}

private fun reorder(
    container: AppContainer,
    destination: Destination,
    all: List<Destination>,
    delta: Int,
    stagingTripId: String?,
) {
    val sorted = all.sortedBy { it.orderIndex }
    val index = sorted.indexOfFirst { it.id == destination.id }
    val targetIndex = index + delta
    if (index < 0 || targetIndex < 0 || targetIndex >= sorted.size) return
    if (sorted[targetIndex].status != DestinationStatus.PENDING) return
    container.reorderDestination(destination.id, sorted[targetIndex].orderIndex, stagingTripId)
}

@Composable
private fun AddDestinationDialog(
    container: AppContainer,
    role: Role,
    lat: Double?,
    lon: Double?,
    editableCoordinates: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, lat: Double, lon: Double) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var latText by remember {
        mutableStateOf(lat?.let { String.format(java.util.Locale.US, "%.5f", it) } ?: "")
    }
    var lonText by remember {
        mutableStateOf(lon?.let { String.format(java.util.Locale.US, "%.5f", it) } ?: "")
    }

    // Address search (ANDMAP-008/009): explicit action only; the long-press variant keeps
    // its pin coordinates, so search appears only alongside editable coordinate entry.
    val scope = rememberCoroutineScope()
    val addressSearch = remember { AddressSearch(container.api) }
    var query by remember { mutableStateOf("") }
    var searchState by remember { mutableStateOf<AddressSearchState>(AddressSearchState.Idle) }

    val parsedLat = latText.toDoubleOrNull()
    val parsedLon = lonText.toDoubleOrNull()
    val valid = name.isNotBlank() &&
        parsedLat != null && parsedLat in -90.0..90.0 &&
        parsedLon != null && parsedLon in -180.0..180.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add destination") },
        text = {
            Column {
                if (editableCoordinates) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search address") },
                    )
                    TextButton(
                        enabled = query.isNotBlank() && searchState != AddressSearchState.Searching,
                        onClick = {
                            searchState = AddressSearchState.Searching
                            scope.launch {
                                // AddressSearch classifies the outcome (ANDMAP-009/011); we no
                                // longer blanket-catch every throwable into "unavailable". Genuine
                                // failures come back as Error and are logged, not swallowed.
                                val result = withContext(Dispatchers.IO) {
                                    addressSearch.search(query, role)
                                }
                                if (result is AddressSearchState.Error) {
                                    Log.e("MapScreen", "Address search failed", result.cause)
                                }
                                searchState = result
                            }
                        },
                    ) {
                        Text("Search")
                    }
                    when (val s = searchState) {
                        is AddressSearchState.Results -> s.matches.forEach { match ->
                            TextButton(
                                onClick = {
                                    val prefill = AddressSearch.pick(match)
                                    name = prefill.name
                                    latText = String.format(java.util.Locale.US, "%.5f", prefill.lat)
                                    lonText = String.format(java.util.Locale.US, "%.5f", prefill.lon)
                                    searchState = AddressSearchState.Idle
                                },
                            ) {
                                Text(
                                    match.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        AddressSearchState.Searching -> Text(
                            "Searching…",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        AddressSearchState.NoMatches -> Text(
                            "No matches — try a different search.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        AddressSearchState.Offline -> Text(
                            "Address search needs internet — enter coordinates below or long-press the map.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        AddressSearchState.ServiceUnavailable -> Text(
                            "Address search is temporarily unavailable — try again, or enter coordinates / long-press the map.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        is AddressSearchState.Error -> Text(
                            "Address search hit an unexpected error — enter coordinates below or long-press the map.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        AddressSearchState.Idle -> {}
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                )
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("Latitude") },
                    enabled = editableCoordinates,
                )
                OutlinedTextField(
                    value = lonText,
                    onValueChange = { lonText = it },
                    label = { Text("Longitude") },
                    enabled = editableCoordinates,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onConfirm(name.trim(), parsedLat!!, parsedLon!!) },
                enabled = valid,
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
