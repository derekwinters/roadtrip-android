package com.roadtrip.core.bingo

import com.roadtrip.core.api.BingoCard
import com.roadtrip.core.api.BingoCell
import com.roadtrip.core.api.BingoLogAction
import com.roadtrip.core.api.BingoLogEntry
import com.roadtrip.core.storage.InMemoryCacheStore
import com.roadtrip.core.storage.InMemoryOutboxStore
import com.roadtrip.core.sync.OutboxEntry
import com.roadtrip.core.sync.OutboxQueue
import com.roadtrip.core.testing.FakeClock
import com.roadtrip.core.testing.FakeRoadtripApi
import com.roadtrip.core.testing.SequentialIds
import com.roadtrip.core.testing.TestData
import com.roadtrip.core.trips.TripHistoryBrowser
import com.roadtrip.core.trips.TripScopedCacheStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive

class BingoReducerTest {
    private val profilesById = mapOf(
        TestData.parent.id to TestData.parent,
        TestData.kid.id to TestData.kid,
        TestData.otherKid.id to TestData.otherKid,
    )

    private val store = InMemoryOutboxStore()
    private val clock = FakeClock()
    private val queue = OutboxQueue(store, clock, SequentialIds())

    private fun cell(code: String, by: String, atSeconds: Long) =
        BingoCell(stateCode = code, spottedBy = by, spottedAt = TestData.ts(atSeconds))

    // ---- ANDBNG-001: the grid, filled cells, offline spots ------------------------------

    @Test
    fun `the grid covers the fifty states plus DC from the static list ANDBNG-001`() {
        assertEquals(51, UsStates.ALL.size)
        assertEquals(51, UsStates.ALL.map { it.code }.toSet().size)
        assertEquals("District of Columbia", UsStates.byCode.getValue("DC").name)
        assertEquals("Colorado", UsStates.byCode.getValue("CO").name)
        assertTrue(UsStates.byCode.containsKey("AK") && UsStates.byCode.containsKey("HI"))

        val state = BingoReducer.reduce(BingoCard(), emptyList(), TestData.kid, profilesById)
        assertEquals(51, state.cells.size)
        assertEquals(UsStates.ALL.map { it.code }, state.cells.map { it.stateCode })
        // Any profile can spot any empty cell — spotting is never parent-gated.
        assertTrue(state.cells.all { !it.spotted && it.canSpot })
    }

    @Test
    fun `filled cells show state spotter and time ANDBNG-001`() {
        val card = BingoCard(
            cells = listOf(cell("CO", TestData.kid.id, 60)),
            counts = mapOf(TestData.kid.id to 1),
        )

        val state = BingoReducer.reduce(card, emptyList(), TestData.otherKid, profilesById)
        val co = state.cells.single { it.stateCode == "CO" }

        assertTrue(co.spotted)
        assertEquals("Colorado", co.stateName)
        assertEquals(TestData.kid.id, co.spottedById)
        assertEquals("Maya", co.spottedByName)
        assertEquals(TestData.t(60), co.spottedAt)
        assertFalse(co.pending)
        assertFalse(co.canSpot)
    }

    @Test
    fun `spots queue offline with pending state and real tap timestamps ANDBNG-001`() {
        clock.current = TestData.t(300)
        val entry = queue.enqueuePlateSpotted("KS")

        assertEquals(OutboxEntry.TYPE_PLATE_SPOTTED, entry.type)
        assertEquals(TestData.t(300), entry.clientTs)
        assertEquals("KS", entry.payload["state_code"]!!.jsonPrimitive.content)
        // Attribution rides the normal sync path — no actor override for bingo.
        assertNull(entry.actorProfileId)

        // The pending spot renders immediately, attributed to the signed-in profile,
        // stamped with the tap time (not the sync time).
        clock.advanceSeconds(500)
        val state = BingoReducer.reduce(BingoCard(), store.pending(), TestData.kid, profilesById)
        val ks = state.cells.single { it.stateCode == "KS" }
        assertTrue(ks.spotted)
        assertTrue(ks.pending)
        assertEquals("Maya", ks.spottedByName)
        assertEquals(TestData.t(300), ks.spottedAt)

        // Only real states + DC exist on the card.
        assertFailsWith<IllegalArgumentException> { queue.enqueuePlateSpotted("XX") }
    }

    // ---- ANDBNG-002: detail sheet removal rules ------------------------------------------

    @Test
    fun `removal is offered to the original spotter or a parent only ANDBNG-002`() {
        val card = BingoCard(cells = listOf(cell("CO", TestData.kid.id, 60)))

        fun coFor(self: com.roadtrip.core.api.Profile) =
            BingoReducer.reduce(card, emptyList(), self, profilesById).cells.single { it.stateCode == "CO" }

        assertTrue(coFor(TestData.kid).canRemove) // the spotter
        assertTrue(coFor(TestData.parent).canRemove) // any parent
        // Everyone else still sees the details, just no remove action.
        val other = coFor(TestData.otherKid)
        assertFalse(other.canRemove)
        assertEquals("Maya", other.spottedByName)
        assertEquals(TestData.t(60), other.spottedAt)
    }

    @Test
    fun `removals queue offline and clear the cell with pending state ANDBNG-002`() {
        val card = BingoCard(
            cells = listOf(cell("CO", TestData.kid.id, 60)),
            counts = mapOf(TestData.kid.id to 1),
        )
        clock.current = TestData.t(900)
        val entry = queue.enqueuePlateUnspotted("CO")
        assertEquals(OutboxEntry.TYPE_PLATE_UNSPOTTED, entry.type)
        assertEquals(TestData.t(900), entry.clientTs)

        val state = BingoReducer.reduce(card, store.pending(), TestData.kid, profilesById)
        val co = state.cells.single { it.stateCode == "CO" }
        assertFalse(co.spotted)
        assertTrue(co.pending)
        // The leaderboard already reflects the pending removal.
        assertTrue(state.standings.none { it.profileId == TestData.kid.id && it.count > 0 })
    }

    // ---- ANDBNG-003: chronological log ---------------------------------------------------

    @Test
    fun `the log renders the backend history chronologically with names ANDBNG-003`() {
        val card = BingoCard(
            cells = listOf(cell("CO", TestData.kid.id, 100)),
            log = listOf(
                BingoLogEntry("UT", BingoLogAction.REMOVED, TestData.parent.id, TestData.ts(500)),
                BingoLogEntry("CO", BingoLogAction.SPOTTED, TestData.kid.id, TestData.ts(100)),
                BingoLogEntry("UT", BingoLogAction.SPOTTED, TestData.otherKid.id, TestData.ts(200)),
            ),
        )

        val log = BingoReducer.reduce(card, emptyList(), TestData.kid, profilesById).log

        assertEquals(listOf("CO", "UT", "UT"), log.map { it.stateCode })
        assertEquals(
            listOf(BingoLogAction.SPOTTED, BingoLogAction.SPOTTED, BingoLogAction.REMOVED),
            log.map { it.action },
        )
        assertEquals(listOf("Maya", "Theo", "Derek"), log.map { it.profileName })
        assertEquals(listOf(TestData.t(100), TestData.t(200), TestData.t(500)), log.map { it.ts })
        assertEquals("Utah", log.last().stateName)
    }

    // ---- ANDBNG-004: live feed fold, leaderboard, per-trip scope, read-only history ------

    @Test
    fun `live plate events fill and clear cells without a reload ANDBNG-004`() {
        var card = BingoCard()

        card = BingoReducer.applyEvent(card, TestData.plateSpottedEvent(10, "CO", TestData.kid.id), profilesById)
        assertEquals(TestData.kid.id, card.cells.single { it.stateCode == "CO" }.spottedBy)
        assertEquals(1, card.counts[TestData.kid.id])
        assertEquals(BingoLogAction.SPOTTED, card.log.single().action)

        // A duplicate spot of a filled cell changes nothing (first spotter wins).
        val duplicate = BingoReducer.applyEvent(card, TestData.plateSpottedEvent(11, "CO", TestData.otherKid.id), profilesById)
        assertEquals(card, duplicate)

        // Removals mirror the backend rule: a non-spotter kid is not honored...
        val denied = BingoReducer.applyEvent(card, TestData.plateUnspottedEvent(12, "CO", TestData.otherKid.id), profilesById)
        assertEquals(card, denied)

        // ...but the spotter or a parent clears the cell live.
        val cleared = BingoReducer.applyEvent(card, TestData.plateUnspottedEvent(13, "CO", TestData.parent.id), profilesById)
        assertTrue(cleared.cells.none { it.stateCode == "CO" })
        assertEquals(0, cleared.counts[TestData.kid.id] ?: 0)
        assertEquals(BingoLogAction.REMOVED, cleared.log.last().action)
    }

    @Test
    fun `the leaderboard shows standing counts per profile ANDBNG-004`() {
        val card = BingoCard(
            cells = listOf(cell("CO", TestData.kid.id, 10), cell("UT", TestData.kid.id, 20), cell("NM", TestData.parent.id, 30)),
            counts = mapOf(TestData.kid.id to 2, TestData.parent.id to 1),
        )

        val standings = BingoReducer.reduce(card, emptyList(), TestData.kid, profilesById).standings
        assertEquals(listOf("Maya" to 2, "Derek" to 1), standings.map { it.profileName to it.count })

        // A pending offline spot already counts for the signed-in profile.
        clock.current = TestData.t(40)
        queue.enqueuePlateSpotted("AZ")
        val withPending = BingoReducer.reduce(card, store.pending(), TestData.parent, profilesById).standings
        assertEquals(listOf("Derek" to 2, "Maya" to 2), withPending.map { it.profileName to it.count })
    }

    @Test
    fun `the bingo card cache is scoped per trip ANDBNG-004`() {
        val backing = mutableMapOf<String, InMemoryCacheStore<BingoCard>>()
        var currentTrip: String? = "trip-1"
        val cache = TripScopedCacheStore<BingoCard>("bingo", { currentTrip }) { key ->
            backing.getOrPut(key) { InMemoryCacheStore() }
        }

        cache.write(BingoCard(cells = listOf(cell("CO", TestData.kid.id, 10))), TestData.t(0))
        currentTrip = "trip-2"
        assertNull(cache.read()) // the new trip starts with an empty card
        cache.write(BingoCard(), TestData.t(100))
        assertEquals(setOf("bingo_trip-1", "bingo_trip-2"), backing.keys)

        currentTrip = "trip-1"
        assertEquals("CO", cache.read()?.value?.cells?.single()?.stateCode)
    }

    @Test
    fun `past trips open their card read-only from the history browser ANDBNG-004`() = runTest {
        val api = FakeRoadtripApi()
        api.bingoByTrip["trip-1"] = BingoCard(
            cells = listOf(cell("CO", TestData.kid.id, 10)),
            counts = mapOf(TestData.kid.id to 1),
        )

        val pastCard = TripHistoryBrowser(api).bingo("trip-1")
        val state = BingoReducer.reduce(pastCard, emptyList(), TestData.parent, profilesById, readOnly = true)

        assertTrue(state.readOnly)
        assertTrue(state.cells.single { it.stateCode == "CO" }.spotted)
        // Read-only cards offer no actions at all — not even to parents.
        assertTrue(state.cells.none { it.canSpot })
        assertTrue(state.cells.none { it.canRemove })
    }
}
