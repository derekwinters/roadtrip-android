package com.roadtrip.core.map

import com.roadtrip.core.api.DeepLink
import com.roadtrip.core.api.DeepLinkKind
import com.roadtrip.core.api.Destination
import com.roadtrip.core.api.DestinationStatus
import com.roadtrip.core.api.GeoPoint
import com.roadtrip.core.api.MapState
import com.roadtrip.core.api.TrackPoint
import com.roadtrip.core.common.Role
import com.roadtrip.core.storage.Cached
import com.roadtrip.core.testing.TestData
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MapScreenReducerTest {
    private val utc = ZoneId.of("UTC")

    private val active = Destination("d-2", "Rocky Mountain NP", 40.3428, -105.6836, 1, DestinationStatus.ACTIVE)
    private val pending = Destination("d-3", "Arches NP", 38.7331, -109.5925, 2, DestinationStatus.PENDING)
    private val arrived = Destination("d-1", "Denver", 39.7392, -104.9903, 0, DestinationStatus.ARRIVED)
    private val destinations = listOf(pending, arrived, active)

    private val map = MapState(
        current = TrackPoint(39.9, -105.1, "2026-07-18T14:32:10Z"),
        start = GeoPoint(41.8781, -87.6298),
        activeDestination = active,
        remainingMi = 342.15,
        legMiles = 128.64,
        breadcrumb = listOf(TrackPoint(41.8781, -87.6298), TrackPoint(39.9, -105.1)),
    )

    @Test
    fun `kids see exactly start current and active destination markers ANDMAP-001`() {
        val state = MapScreenReducer.fromServer(map, destinations, Role.KID, utc)

        assertEquals(
            listOf(MarkerKind.START, MarkerKind.CURRENT, MarkerKind.ACTIVE_DESTINATION),
            state.markers.map { it.kind },
        )
        assertTrue(state.destinationList.isEmpty()) // no future destination list for kids
    }

    @Test
    fun `parents additionally get the full ordered destination list ANDMAP-001`() {
        val state = MapScreenReducer.fromServer(map, destinations, Role.PARENT, utc)

        assertEquals(listOf("d-1", "d-2", "d-3"), state.destinationList.map { it.id })
        // Parents also see non-active destinations as map markers.
        assertTrue(state.markers.any { it.kind == MarkerKind.DESTINATION && it.label == "Arches NP" })
    }

    @Test
    fun `progress panel formats server fields without client geodesy ANDMAP-002`() {
        val state = MapScreenReducer.fromServer(map, destinations, Role.KID, utc)

        assertEquals("128.6 mi", state.progress.legMilesText)
        assertEquals("342.2 mi", state.progress.remainingText)
        assertEquals("14:32", state.progress.lastUpdateText)

        // Missing server values render placeholders instead of computing anything locally.
        val bare = MapScreenReducer.fromServer(MapState(breadcrumb = emptyList()), emptyList(), Role.KID, utc)
        assertEquals("—", bare.progress.remainingText)
        assertNull(bare.progress.lastUpdateText)
    }

    @Test
    fun `live pings extend the breadcrumb polyline without a reload ANDMAP-003`() {
        val before = MapScreenReducer.fromServer(map, destinations, Role.PARENT, utc)

        val after = MapScreenReducer.appendPing(before, TestData.pingEvent(50, 39.95, -105.15))

        assertEquals(before.polyline + GeoPoint(39.95, -105.15), after.polyline)
        val current = after.markers.single { it.kind == MarkerKind.CURRENT }
        assertEquals(39.95, current.lat)
        assertEquals(-105.15, current.lon)
        // No reload: everything else is carried over untouched.
        assertSame(before.destinationList, after.destinationList)
        assertEquals(before.progress, after.progress)
    }

    @Test
    fun `offline renders the cached state with an as-of freshness label ANDMAP-004`() {
        val cached = Cached(map, storedAt = java.time.Instant.parse("2026-07-18T14:32:10Z"))

        val state = MapScreenReducer.fromCache(cached, destinations, Role.KID, utc)

        assertTrue(state.offline)
        assertEquals("as of 14:32", state.freshnessLabel)
        assertEquals(2, state.polyline.size) // last known trail still renders
    }

    @Test
    fun `stop deep links center the map on the stop pin ANDMAP-005`() {
        val state = MapScreenReducer.fromServer(map, destinations, Role.KID, utc)
        assertNull(state.center)

        val centered = MapScreenReducer.centerOn(
            state,
            DeepLink(DeepLinkKind.MAP_PIN, lat = 39.7, lon = -105.2),
        )
        assertEquals(GeoPoint(39.7, -105.2), centered.center)

        // Non-pin links leave the camera alone.
        val untouched = MapScreenReducer.centerOn(state, DeepLink(DeepLinkKind.CHECKLIST, stateCode = "CO"))
        assertNull(untouched.center)
    }
}
