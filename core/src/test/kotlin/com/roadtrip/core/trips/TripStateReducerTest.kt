package com.roadtrip.core.trips

import com.roadtrip.core.api.ApiException
import com.roadtrip.core.api.TripStatus
import com.roadtrip.core.common.Role
import com.roadtrip.core.testing.FakeRoadtripApi
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TripStateReducerTest {
    private val active =
        TestData.trip("trip-3", "Fall Colors", TripStatus.ACTIVE, startedAt = TestData.ts(5000))
    private val endedOld =
        TestData.trip("trip-1", "Spring Break", TripStatus.ENDED, startedAt = TestData.ts(0), endedAt = TestData.ts(1000))
    private val endedRecent =
        TestData.trip("trip-2", "Summer Loop", TripStatus.ENDED, startedAt = TestData.ts(2000), endedAt = TestData.ts(3000))

    @Test
    fun `parents get the start action between trips and the end action during one ANDTRIP-001`() {
        val between = TripStateReducer.reduce(listOf(endedOld, endedRecent), Role.PARENT, online = true)
        assertTrue(between.startAction.visible)
        assertTrue(between.startAction.enabled)
        assertFalse(between.endAction.visible)

        val during = TripStateReducer.reduce(listOf(endedOld, active), Role.PARENT, online = true)
        assertFalse(during.startAction.visible)
        assertTrue(during.endAction.visible)
        assertTrue(during.endAction.enabled)
    }

    @Test
    fun `kids never see start or end actions anywhere but keep history browsing ANDTRIP-001`() {
        for (trips in listOf(emptyList(), listOf(endedRecent), listOf(endedOld, active))) {
            val state = TripStateReducer.reduce(trips, Role.KID, online = true)
            assertFalse(state.startAction.visible, "kids must not see start for $trips")
            assertFalse(state.endAction.visible, "kids must not see end for $trips")
        }
        // History still resolves for kids — they browse, they just cannot start/end.
        val kidView = TripStateReducer.reduce(listOf(endedOld, endedRecent), Role.KID, online = true)
        assertEquals("trip-2", kidView.viewedTrip?.id)
    }

    @Test
    fun `no active trip opens the most recently ended trip with a persistent banner ANDTRIP-002`() {
        // Deliberately unsorted list: the reducer must pick the most recently ENDED trip.
        val state = TripStateReducer.reduce(listOf(endedRecent, endedOld), Role.KID, online = false)

        val phase = assertIs<TripPhase.BetweenTrips>(state.phase)
        assertEquals("trip-2", phase.lastTrip.id)
        assertEquals("trip-2", state.viewedTrip?.id)
        assertEquals(TripStateReducer.NO_ACTIVE_TRIP_BANNER, state.bannerText)
    }

    @Test
    fun `the between-trips strip is one banner line with a parent-only New trip link ANDTRIP-002`() {
        val parent = TripStateReducer.reduce(listOf(endedOld, endedRecent), Role.PARENT, online = true)
        val strip = assertNotNull(TripStateReducer.noActiveTripStrip(parent))
        // Single line: the exact banner, ellipsized (no forced wraps), and no other content
        // than the banner + link surfaced from the reducer seam (no History/Plan/Start).
        assertEquals(TripStateReducer.NO_ACTIVE_TRIP_STRIP_BANNER, strip.banner)
        assertFalse(strip.banner.contains("\n"))
        // The "New trip → Settings" link follows the parent-only start gating.
        assertTrue(strip.newTripLinkVisible)
        assertEquals(parent.startAction.visible, strip.newTripLinkVisible)

        // Kids get the informational banner without the link (trip creation is parent-only).
        val kid = TripStateReducer.reduce(listOf(endedOld, endedRecent), Role.KID, online = true)
        val kidStrip = assertNotNull(TripStateReducer.noActiveTripStrip(kid))
        assertEquals(TripStateReducer.NO_ACTIVE_TRIP_STRIP_BANNER, kidStrip.banner)
        assertFalse(kidStrip.newTripLinkVisible, "kids see the banner without the New trip link")
        assertEquals(kid.startAction.visible, kidStrip.newTripLinkVisible)
    }

    @Test
    fun `the strip shows on first launch and offline for parents but not during a trip ANDTRIP-002`() {
        // First-ever launch: parents still get the strip + link.
        val firstLaunch = TripStateReducer.reduce(emptyList(), Role.PARENT, online = true)
        assertTrue(assertNotNull(TripStateReducer.noActiveTripStrip(firstLaunch)).newTripLinkVisible)

        // Offline parents keep the link (the disabled/offline explanation lives in Settings).
        val offline = TripStateReducer.reduce(emptyList(), Role.PARENT, online = false)
        assertTrue(assertNotNull(TripStateReducer.noActiveTripStrip(offline)).newTripLinkVisible)

        // While a trip is active the strip renders nothing (name lives in the app bar).
        val duringTrip = TripStateReducer.reduce(listOf(endedOld, active), Role.PARENT, online = true)
        assertNull(TripStateReducer.noActiveTripStrip(duringTrip))
    }

    @Test
    fun `an active trip shows its name with no banner ANDTRIP-002`() {
        val state = TripStateReducer.reduce(listOf(endedOld, active), Role.KID, online = true)

        assertIs<TripPhase.Active>(state.phase)
        assertEquals("Fall Colors", state.viewedTrip?.name)
        assertNull(state.bannerText)
    }

    @Test
    fun `first-ever launch is a welcome state with the parent start action ANDTRIP-002`() {
        val parent = TripStateReducer.reduce(emptyList(), Role.PARENT, online = true)

        assertIs<TripPhase.FirstLaunch>(parent.phase)
        assertNull(parent.viewedTrip)
        assertEquals(TripStateReducer.NO_ACTIVE_TRIP_BANNER, parent.bannerText)
        assertTrue(parent.startAction.visible)
        assertTrue(parent.startAction.enabled)
    }

    @Test
    fun `the tracker may only run while a trip is active ANDTRIP-002`() {
        assertTrue(TripStateReducer.reduce(listOf(active), Role.PARENT, online = true).trackerAllowed)
        assertFalse(TripStateReducer.reduce(listOf(endedRecent), Role.PARENT, online = true).trackerAllowed)
        assertFalse(TripStateReducer.reduce(emptyList(), Role.PARENT, online = true).trackerAllowed)
    }

    @Test
    fun `start and end are online-only confirm-dialog actions ANDTRIP-004`() {
        val offlineStart = TripStateReducer.reduce(emptyList(), Role.PARENT, online = false).startAction
        assertTrue(offlineStart.visible)
        assertFalse(offlineStart.enabled)
        assertEquals(TripStateReducer.OFFLINE_REASON, offlineStart.disabledReason)

        val offlineEnd = TripStateReducer.reduce(listOf(active), Role.PARENT, online = false).endAction
        assertTrue(offlineEnd.visible)
        assertFalse(offlineEnd.enabled)
        assertEquals(TripStateReducer.OFFLINE_REASON, offlineEnd.disabledReason)

        val onlineStart = TripStateReducer.reduce(emptyList(), Role.PARENT, online = true).startAction
        assertTrue(onlineStart.enabled)
        assertNull(onlineStart.disabledReason)
        // Both actions always go through a confirm dialog.
        assertTrue(onlineStart.requiresConfirmation)
        assertTrue(offlineEnd.requiresConfirmation)
    }

    @Test
    fun `the server arbitrates the single active trip - a second start conflicts ANDTRIP-004`() = runTest {
        val api = FakeRoadtripApi()
        api.createTrip("First")

        val ex = assertFailsWith<ApiException> { api.createTrip("Second") }
        assertEquals(409, ex.status)
        assertEquals("conflict", ex.code)

        // Ending the active trip frees the slot again.
        api.endTrip(api.trips.first { it.status == TripStatus.ACTIVE }.id)
        assertEquals(TripStatus.ACTIVE, api.createTrip(null).status)
    }

    @Test
    fun `the active trip name is app-bar context only while a trip runs ANDTRIP-009`() {
        // Active: the app bar overline carries the running trip's name.
        val active = TripStateReducer.reduce(listOf(endedOld, this.active), Role.PARENT, online = true)
        assertNull(active.bannerText)
        assertEquals("Fall Colors", TripStateReducer.activeTripBarLabel(active))
    }

    @Test
    fun `between trips and first launch surface no app-bar trip name ANDTRIP-009`() {
        // Between trips: the "Browsing …" context stays in the TripStrip banner, not the app bar.
        val between = TripStateReducer.reduce(listOf(endedOld, endedRecent), Role.KID, online = true)
        assertEquals(TripStateReducer.NO_ACTIVE_TRIP_BANNER, between.bannerText)
        assertNull(TripStateReducer.activeTripBarLabel(between))

        // First-ever launch: nothing to show in the app bar either.
        val firstLaunch = TripStateReducer.reduce(emptyList(), Role.PARENT, online = true)
        assertNull(TripStateReducer.activeTripBarLabel(firstLaunch))
    }
}
