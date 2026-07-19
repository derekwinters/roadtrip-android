package com.roadtrip.core.trips

import com.roadtrip.core.api.ApiException
import com.roadtrip.core.api.Destination
import com.roadtrip.core.api.DestinationCreate
import com.roadtrip.core.api.DestinationPatch
import com.roadtrip.core.api.DestinationStatus
import com.roadtrip.core.api.TripStatus
import com.roadtrip.core.common.Role
import com.roadtrip.core.storage.InMemoryCacheStore
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

class TripPlannerTest {
    private val ended =
        TestData.trip("trip-1", "Spring Break", TripStatus.ENDED, startedAt = TestData.ts(0), endedAt = TestData.ts(1000))
    private val active =
        TestData.trip("trip-2", "Fall Colors", TripStatus.ACTIVE, startedAt = TestData.ts(5000))
    private val planned = TestData.plannedTrip("trip-9", "Desert Loop", "~ early August")

    // ---- ANDTRIP-006: one planned next trip, card on the no-active-trip screens ---------

    @Test
    fun `parents plan rename and delete exactly one next trip ANDTRIP-006`() = runTest {
        val api = FakeRoadtripApi()

        val plan = api.createPlannedTrip("Desert Loop", "~ early August")
        assertEquals(TripStatus.PLANNED, plan.status)
        assertEquals("~ early August", plan.plannedStartAt)
        assertNull(plan.startedAt)

        // The server arbitrates a single planned trip: a second plan conflicts.
        val ex = assertFailsWith<ApiException> { api.createPlannedTrip("Another", null) }
        assertEquals(409, ex.status)
        assertEquals("conflict", ex.code)

        // Rename and re-date via PATCH {name?, planned_start_at?}.
        val renamed = api.patchTrip(plan.id, name = "Desert Loop 2026")
        assertEquals("Desert Loop 2026", renamed.name)
        assertEquals("~ early August", renamed.plannedStartAt)
        val redated = api.patchTrip(plan.id, plannedStartAt = "~ Aug 8")
        assertEquals("~ Aug 8", redated.plannedStartAt)

        // Delete frees the slot; only planned trips are deletable.
        api.deleteTrip(plan.id)
        assertTrue(api.getTrips().isEmpty())
        val second = api.createPlannedTrip("Take two", null)
        assertEquals(TripStatus.PLANNED, second.status)

        api.startTrip(second.id)
        val activeNow = api.getTrips().single()
        assertEquals(TripStatus.ACTIVE, activeNow.status)
        assertFailsWith<ApiException> { api.deleteTrip(activeNow.id) }.also {
            assertEquals(409, it.status)
        }
    }

    @Test
    fun `the planned-trip card shows between trips and on first launch ANDTRIP-006`() {
        // Between trips: the ended trip stays the read-only viewed trip, the planned
        // card rides along.
        val between = TripStateReducer.reduce(listOf(ended, planned), Role.PARENT, online = true)
        assertIs<TripPhase.BetweenTrips>(between.phase)
        assertEquals("trip-1", between.viewedTrip?.id)
        assertEquals(TripStateReducer.NO_ACTIVE_TRIP_BANNER, between.bannerText)
        assertEquals("trip-9", between.plannedTrip?.id)

        // First-ever launch with only a plan: the Planned phase carries the card.
        val firstLaunch = TripStateReducer.reduce(listOf(planned), Role.KID, online = true)
        val phase = assertIs<TripPhase.Planned>(firstLaunch.phase)
        assertEquals("trip-9", phase.trip.id)
        assertNull(firstLaunch.viewedTrip)
        assertEquals(TripStateReducer.NO_ACTIVE_TRIP_BANNER, firstLaunch.bannerText)
        assertEquals("trip-9", firstLaunch.plannedTrip?.id)

        // While a trip runs the card stays out of the way; a planned trip never counts
        // as active for the tracker gate.
        val during = TripStateReducer.reduce(listOf(active, planned), Role.PARENT, online = true)
        assertIs<TripPhase.Active>(during.phase)
        assertNull(during.plannedTrip)
        assertFalse(TripStateReducer.reduce(listOf(planned), Role.PARENT, online = true).trackerAllowed)
    }

    @Test
    fun `the planner card exposes parent-only online-only actions ANDTRIP-006`() {
        val state = TripPlannerReducer.reduce(listOf(planned), emptyList(), Role.PARENT, online = true)
        val card = assertNotNull(state.card)
        assertEquals("Desert Loop", card.trip.name)
        assertEquals("~ early August", card.plannedStartText)
        assertTrue(card.startAction.visible)
        assertTrue(card.startAction.enabled)
        assertTrue(card.startAction.requiresConfirmation)
        assertTrue(card.renameAction.visible && card.renameAction.enabled)
        assertTrue(card.deleteAction.visible && card.deleteAction.enabled)
        // No planned trip yet: parents get the plan affordance instead of a card.
        val empty = TripPlannerReducer.reduce(emptyList(), emptyList(), Role.PARENT, online = true)
        assertNull(empty.card)
        assertTrue(empty.planAction.visible)
        assertTrue(empty.planAction.enabled)
        // Once one exists (or a trip runs), there is no second-plan affordance.
        assertFalse(state.planAction.visible)
        assertFalse(
            TripPlannerReducer.reduce(listOf(active), emptyList(), Role.PARENT, online = true)
                .planAction.visible,
        )
    }

    @Test
    fun `kids see the planned-trip card read-only ANDTRIP-006`() {
        val state = TripPlannerReducer.reduce(listOf(planned), emptyList(), Role.KID, online = true)
        val card = assertNotNull(state.card)
        assertEquals("Desert Loop", card.trip.name)
        assertEquals("~ early August", card.plannedStartText)
        assertFalse(card.startAction.visible)
        assertFalse(card.renameAction.visible)
        assertFalse(card.deleteAction.visible)
        assertFalse(card.canStageDestinations)
        assertFalse(state.planAction.visible)
    }

    @Test
    fun `planner actions are online-only with an explanation ANDTRIP-006`() {
        val offline = TripPlannerReducer.reduce(listOf(planned), emptyList(), Role.PARENT, online = false)
        val card = assertNotNull(offline.card)
        assertTrue(card.startAction.visible)
        assertFalse(card.startAction.enabled)
        assertEquals(TripStateReducer.OFFLINE_REASON, card.startAction.disabledReason)
        assertFalse(card.renameAction.enabled)
        assertFalse(card.deleteAction.enabled)
        assertEquals(TripPlannerReducer.OFFLINE_REASON, card.renameAction.disabledReason)

        val noPlan = TripPlannerReducer.reduce(emptyList(), emptyList(), Role.PARENT, online = false)
        assertTrue(noPlan.planAction.visible)
        assertFalse(noPlan.planAction.enabled)
        assertEquals(TripPlannerReducer.OFFLINE_REASON, noPlan.planAction.disabledReason)
    }

    @Test
    fun `planned trips stay out of the history browser list ANDTRIP-006`() = runTest {
        val api = FakeRoadtripApi()
        api.trips += listOf(ended, planned)

        val listed = TripHistoryBrowser(api).listTrips()

        assertEquals(listOf("trip-1"), listed.map { it.id })
    }

    // ---- ANDTRIP-007: staging destinations against the planned trip ---------------------

    @Test
    fun `destinations stage against the planned trip like during a trip ANDTRIP-007`() = runTest {
        val api = FakeRoadtripApi()
        val plan = api.createPlannedTrip("Desert Loop", null)

        api.createDestination(DestinationCreate("Arches NP", 38.73, -109.59), trip = plan.id)
        api.createDestination(DestinationCreate("Mesa Verde", 37.23, -108.46), trip = plan.id)

        // Staged list is scoped to the planned trip; the unscoped pool stays untouched.
        val staged = api.getDestinations(trip = plan.id)
        assertEquals(listOf("Arches NP", "Mesa Verde"), staged.map { it.name })
        assertTrue(api.getDestinations().isEmpty())

        // Staged stops can be edited and removed through the same scoped writes.
        api.updateDestination(staged[1].id, DestinationPatch(orderIndex = 0), trip = plan.id)
        api.deleteDestination(staged[0].id, trip = plan.id)
        assertEquals(listOf("Mesa Verde"), api.getDestinations(trip = plan.id).map { it.name })
    }

    @Test
    fun `the card previews the staged itinerary and gates staging ANDTRIP-007`() {
        val staged = listOf(
            Destination("d-2", "Mesa Verde", 37.23, -108.46, 1, DestinationStatus.PENDING),
            Destination("d-1", "Arches NP", 38.73, -109.59, 0, DestinationStatus.PENDING),
        )

        val parent = TripPlannerReducer.reduce(listOf(planned), staged, Role.PARENT, online = true)
        assertEquals(listOf("Arches NP", "Mesa Verde"), parent.card?.itinerary?.map { it.name })
        assertTrue(parent.card!!.canStageDestinations)

        // Staging is parent-only and online-only, like destination admin during a trip.
        val offline = TripPlannerReducer.reduce(listOf(planned), staged, Role.PARENT, online = false)
        assertFalse(offline.card!!.canStageDestinations)
        val kid = TripPlannerReducer.reduce(listOf(planned), staged, Role.KID, online = true)
        assertFalse(kid.card!!.canStageDestinations)
        // The preview itself is visible to everyone.
        assertEquals(2, kid.card!!.itinerary.size)
    }

    // ---- AND-012: the shell resolves staged destinations off the composition path -------

    @Test
    fun `planner reduce resolves the planned trip's staged itinerary via the provider AND-012`() {
        val staged = mapOf(
            "trip-9" to listOf(
                Destination("d-2", "Mesa Verde", 37.23, -108.46, 1, DestinationStatus.PENDING),
                Destination("d-1", "Arches NP", 38.73, -109.59, 0, DestinationStatus.PENDING),
            ),
        )
        val requested = mutableListOf<String>()
        val stagedFor: (String) -> List<Destination>? = { id -> requested += id; staged[id] }

        // With a plan present the provider is asked only for the planned trip's id and its
        // staged list feeds the card — the same result as passing the list directly.
        val state = TripPlannerReducer.reduce(listOf(ended, planned), stagedFor, Role.PARENT, online = true)
        assertEquals(listOf("trip-9"), requested)
        assertEquals(listOf("Arches NP", "Mesa Verde"), state.card?.itinerary?.map { it.name })

        // No planned trip: the provider is never consulted (no wasted cache read) and no card.
        requested.clear()
        val none = TripPlannerReducer.reduce(listOf(ended), stagedFor, Role.PARENT, online = true)
        assertTrue(requested.isEmpty())
        assertNull(none.card)

        // A not-yet-cached staged list (null) reads as an empty itinerary, never a crash.
        val uncached = TripPlannerReducer.reduce(listOf(planned), { null }, Role.PARENT, online = true)
        assertEquals(emptyList<Destination>(), uncached.card?.itinerary)
    }

    // ---- ANDTRIP-008: activation adopts the itinerary and switches caches ---------------

    @Test
    fun `activating adopts the staged itinerary and switches the scoped caches ANDTRIP-008`() = runTest {
        val api = FakeRoadtripApi()
        val endedOld = TestData.trip("trip-old", "Spring Break", TripStatus.ENDED, startedAt = TestData.ts(0), endedAt = TestData.ts(1000))
        api.trips += endedOld
        val plan = api.createPlannedTrip("Desert Loop", "~ early August")
        api.createDestination(DestinationCreate("Arches NP", 38.73, -109.59), trip = plan.id)
        api.createDestination(DestinationCreate("Mesa Verde", 37.23, -108.46), trip = plan.id)

        // The scoped caches point at the viewed trip (most recently ended, pre-start).
        val backing = mutableMapOf<String, InMemoryCacheStore<List<String>>>()
        var trips = api.getTrips()
        val journal = TripScopedCacheStore<List<String>>(
            "journal",
            { TripStateReducer.viewedTrip(trips)?.id },
        ) { key -> backing.getOrPut(key) { InMemoryCacheStore() } }
        journal.write(listOf("old trip post"), TestData.t(0))
        assertEquals(setOf("journal_trip-old"), backing.keys)

        // "Road trip starts now" on the card: the client just calls /start and re-syncs.
        val startedTrip = api.startTrip(plan.id)
        assertEquals(TripStatus.ACTIVE, startedTrip.status)
        trips = api.getTrips()

        // The new active trip is the viewed trip; its cache starts fresh while the old
        // trip's history stays intact under its own key.
        assertEquals(plan.id, TripStateReducer.viewedTrip(trips)?.id)
        assertNull(journal.read())
        journal.write(listOf("first desert post"), TestData.t(100))
        assertEquals(setOf("journal_trip-old", "journal_${plan.id}"), backing.keys)
        assertEquals(listOf("old trip post"), backing["journal_trip-old"]?.read()?.value)

        // The staged itinerary was adopted: it is now the active destination list.
        val adopted = api.getDestinations()
        assertEquals(listOf("Arches NP", "Mesa Verde"), adopted.map { it.name })
        assertEquals(DestinationStatus.ACTIVE, adopted.first().status)
    }

    @Test
    fun `activation conflicts while another trip is active ANDTRIP-008`() = runTest {
        val api = FakeRoadtripApi()
        val plan = api.createPlannedTrip("Desert Loop", null)
        api.trips += active

        val ex = assertFailsWith<ApiException> { api.startTrip(plan.id) }
        assertEquals(409, ex.status)
        assertEquals("conflict", ex.code)

        // The plan survives the conflict untouched.
        assertEquals(TripStatus.PLANNED, api.getTrips().first { it.id == plan.id }.status)
    }
}
