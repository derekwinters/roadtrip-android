package com.roadtrip.core.map

import com.roadtrip.core.api.DeepLink
import com.roadtrip.core.api.DeepLinkKind
import com.roadtrip.core.api.Destination
import com.roadtrip.core.api.DestinationStatus
import com.roadtrip.core.api.EventDto
import com.roadtrip.core.api.GeoPoint
import com.roadtrip.core.api.MapState
import com.roadtrip.core.common.Role
import com.roadtrip.core.common.Timestamps
import com.roadtrip.core.storage.Cached
import java.time.ZoneId
import java.util.Locale
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class MarkerKind { START, CURRENT, ACTIVE_DESTINATION, DESTINATION }

data class MapMarker(
    val kind: MarkerKind,
    val lat: Double,
    val lon: Double,
    val label: String? = null,
)

/** Progress panel formatted purely from GET /api/map fields — no client geodesy (ANDMAP-002). */
data class ProgressPanel(
    val legMilesText: String,
    val remainingText: String,
    val lastUpdateText: String?,
)

data class MapScreenState(
    val markers: List<MapMarker>,
    val destinationList: List<Destination>,
    val polyline: List<GeoPoint>,
    val progress: ProgressPanel,
    val offline: Boolean,
    val freshnessLabel: String?,
    val center: GeoPoint?,
)

/**
 * Map screen state reducer. Kid profiles see exactly start/current/active-destination
 * markers; parents additionally get the full destination list (ANDMAP-001).
 */
object MapScreenReducer {
    fun fromServer(
        map: MapState,
        destinations: List<Destination>,
        role: Role,
        zone: ZoneId = ZoneId.of("UTC"),
    ): MapScreenState = build(map, destinations, role, zone, offline = false, freshnessLabel = null)

    /** Offline render of the last cached state with an "as of HH:mm" label (ANDMAP-004). */
    fun fromCache(
        cached: Cached<MapState>,
        destinations: List<Destination>,
        role: Role,
        zone: ZoneId = ZoneId.of("UTC"),
    ): MapScreenState = build(
        cached.value, destinations, role, zone,
        offline = true,
        freshnessLabel = "as of ${Timestamps.hhmm(cached.storedAt, zone)}",
    )

    /** Extends the breadcrumb from a live location.ping event without a reload (ANDMAP-003). */
    fun appendPing(state: MapScreenState, ping: EventDto): MapScreenState {
        if (ping.type != "location.ping") return state
        val lat = ping.payload["lat"]?.jsonPrimitive?.doubleOrNull ?: return state
        val lon = ping.payload["lon"]?.jsonPrimitive?.doubleOrNull ?: return state

        val point = GeoPoint(lat, lon)
        val markers = state.markers.filterNot { it.kind == MarkerKind.CURRENT } +
            MapMarker(MarkerKind.CURRENT, lat, lon)
        return state.copy(
            polyline = state.polyline + point,
            markers = markers.sortedBy { it.kind.ordinal },
        )
    }

    /** Centers the map on a stop's pin from its deep link (ANDMAP-005). */
    fun centerOn(state: MapScreenState, link: DeepLink): MapScreenState {
        if (link.kind != DeepLinkKind.MAP_PIN) return state
        val lat = link.lat ?: return state
        val lon = link.lon ?: return state
        return state.copy(center = GeoPoint(lat, lon))
    }

    private fun build(
        map: MapState,
        destinations: List<Destination>,
        role: Role,
        zone: ZoneId,
        offline: Boolean,
        freshnessLabel: String?,
    ): MapScreenState {
        val markers = buildList {
            map.start?.let { add(MapMarker(MarkerKind.START, it.lat, it.lon, "Start")) }
            map.current?.let { add(MapMarker(MarkerKind.CURRENT, it.lat, it.lon)) }
            map.activeDestination?.let {
                add(MapMarker(MarkerKind.ACTIVE_DESTINATION, it.lat, it.lon, it.name))
            }
            if (role == Role.PARENT) {
                destinations
                    .filter { it.status != DestinationStatus.ACTIVE }
                    .sortedBy { it.orderIndex }
                    .forEach { add(MapMarker(MarkerKind.DESTINATION, it.lat, it.lon, it.name)) }
            }
        }
        return MapScreenState(
            markers = markers,
            // Kids never see the full future destination list (ANDMAP-001, AND-003).
            destinationList = if (role == Role.PARENT) destinations.sortedBy { it.orderIndex } else emptyList(),
            polyline = map.breadcrumb.map { GeoPoint(it.lat, it.lon) },
            progress = ProgressPanel(
                legMilesText = formatMiles(map.legMiles),
                remainingText = map.remainingMi?.let(::formatMiles) ?: "—",
                lastUpdateText = map.current?.ts?.let { Timestamps.hhmm(Timestamps.parse(it), zone) },
            ),
            offline = offline,
            freshnessLabel = freshnessLabel,
            center = null,
        )
    }

    private fun formatMiles(miles: Double): String = String.format(Locale.US, "%.1f mi", miles)
}
