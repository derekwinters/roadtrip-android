package com.roadtrip.core.journal

import com.roadtrip.core.api.DeepLink
import com.roadtrip.core.api.DeepLinkKind
import com.roadtrip.core.api.JournalEntry
import com.roadtrip.core.api.JournalKind
import com.roadtrip.core.common.Role
import com.roadtrip.core.storage.InMemoryOutboxStore
import com.roadtrip.core.sync.OutboxQueue
import com.roadtrip.core.testing.FakeClock
import com.roadtrip.core.testing.SequentialIds
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JournalFeedTest {
    private val store = InMemoryOutboxStore()
    private val clock = FakeClock()
    private val queue = OutboxQueue(store, clock, SequentialIds())
    private val composer = JournalComposer(queue)

    private fun entry(
        seq: Long,
        kind: JournalKind,
        ts: String,
        text: String,
        link: DeepLink? = null,
    ) = JournalEntry(seq = seq, kind = kind, ts = ts, text = text, link = link)

    @Test
    fun `renders all five entry kinds with distinct display data ANDJRNL-001`() {
        val post = JournalEntry(
            seq = 1, kind = JournalKind.POST, ts = TestData.ts(0),
            actor = TestData.kid, text = "look, mountains!",
        )
        val crossing = entry(
            2, JournalKind.STATE_CROSSING, TestData.ts(60), "Welcome to Colorado!",
            DeepLink(DeepLinkKind.CHECKLIST, stateCode = "CO"),
        )
        val stop = entry(
            3, JournalKind.STOP, TestData.ts(120), "Stopped 25 min at Scenic Overlook",
            DeepLink(DeepLinkKind.MAP_PIN, lat = 39.7, lon = -105.2),
        )
        val gameResult = entry(
            4, JournalKind.GAME_RESULT, TestData.ts(180), "Maya beat Derek at chess",
            DeepLink(DeepLinkKind.GAME_REPLAY, gameId = "g-42"),
        )
        val arrival = entry(
            5, JournalKind.LEG_ARRIVAL, TestData.ts(240), "Arrived at Rocky Mountain NP",
            DeepLink(DeepLinkKind.LEG_SUMMARY, destinationId = "d-7"),
        )

        val postDisplay = assertIs<JournalDisplay.ManualPost>(JournalFeedReducer.displayOf(post))
        assertEquals("Maya", postDisplay.authorName)
        assertEquals("fox", postDisplay.authorAvatar)
        assertEquals("look, mountains!", postDisplay.text)

        val crossingDisplay = assertIs<JournalDisplay.StateCrossing>(JournalFeedReducer.displayOf(crossing))
        assertEquals("CO", crossingDisplay.stateCode)

        val stopDisplay = assertIs<JournalDisplay.Stop>(JournalFeedReducer.displayOf(stop))
        assertEquals(39.7, stopDisplay.pin?.lat)

        val resultDisplay = assertIs<JournalDisplay.GameResult>(JournalFeedReducer.displayOf(gameResult))
        assertEquals("g-42", resultDisplay.gameId)

        val arrivalDisplay = assertIs<JournalDisplay.LegArrival>(JournalFeedReducer.displayOf(arrival))
        assertEquals("d-7", arrivalDisplay.destinationId)
    }

    @Test
    fun `renders trip started and ended entries distinctly with trip summary links ANDJRNL-001`() {
        val started = entry(
            6, JournalKind.TRIP_STARTED, TestData.ts(300), "Road trip \"Summer Loop\" started!",
            DeepLink(DeepLinkKind.TRIP_SUMMARY, tripId = "trip-1"),
        )
        val ended = entry(
            7, JournalKind.TRIP_ENDED, TestData.ts(360), "Road trip complete — 1,204 mi, 5 states",
            DeepLink(DeepLinkKind.TRIP_SUMMARY, tripId = "trip-1"),
        )

        val startedDisplay = assertIs<JournalDisplay.TripStarted>(JournalFeedReducer.displayOf(started))
        assertEquals("Road trip \"Summer Loop\" started!", startedDisplay.text)
        assertEquals("trip-1", startedDisplay.tripId)

        val endedDisplay = assertIs<JournalDisplay.TripEnded>(JournalFeedReducer.displayOf(ended))
        assertEquals("Road trip complete — 1,204 mi, 5 states", endedDisplay.text)
        assertEquals("trip-1", endedDisplay.tripId)
    }

    @Test
    fun `offline posts appear immediately from the outbox marked syncing ANDJRNL-002`() {
        clock.current = TestData.t(600)
        composer.post("posted with no signal")

        val feed = JournalFeedReducer.reduce(emptyList(), store.pending(), TestData.kid)

        val item = feed.single()
        assertTrue(item.syncing)
        assertEquals(TestData.t(600), item.clientTs)
        val display = assertIs<JournalDisplay.ManualPost>(item.display)
        assertEquals("posted with no signal", display.text)
        assertEquals("Maya", display.authorName)
    }

    @Test
    fun `enforces the 1-2000 char contract range in the composer ANDJRNL-002`() {
        assertFailsWith<IllegalArgumentException> { composer.post("") }
        assertFailsWith<IllegalArgumentException> { composer.post("y".repeat(2001)) }
        composer.post("y".repeat(2000))
        assertEquals(1, store.pending().size)
    }

    @Test
    fun `orders the feed by client_ts so backdated entries land at their written time ANDJRNL-003`() {
        val server = listOf(
            entry(10, JournalKind.POST, TestData.ts(0), "start of day"),
            // Synced late from another device but WRITTEN at +1200s (backdated, high seq).
            entry(99, JournalKind.POST, TestData.ts(1200), "offline device post"),
            entry(11, JournalKind.POST, TestData.ts(1800), "latest online post"),
        )
        clock.current = TestData.t(600)
        composer.post("local pending post")

        val feed = JournalFeedReducer.reduce(server, store.pending(), TestData.parent)

        assertEquals(
            listOf("latest online post", "offline device post", "local pending post", "start of day"),
            feed.map { (it.display as JournalDisplay.ManualPost).text },
        )
    }

    @Test
    fun `resolves author avatar and name from the current profiles cache by actor id ANDJRNL-007`() {
        // Entry cached with the author's OLD snapshot (avatar "fox", name "Maya").
        val post = JournalEntry(
            seq = 1, kind = JournalKind.POST, ts = TestData.ts(0),
            actor = TestData.kid, text = "look, mountains!",
        )
        // Current profiles cache: same id, but avatar/name have since changed.
        val current = TestData.kid.copy(avatar = "unicorn", name = "Maya B.")
        val profiles = mapOf(current.id to current)

        val display = assertIs<JournalDisplay.ManualPost>(
            JournalFeedReducer.displayOf(post, profiles),
        )
        assertEquals("unicorn", display.authorAvatar)
        assertEquals("Maya B.", display.authorName)
    }

    @Test
    fun `falls back to the embedded actor when the profile is not cached ANDJRNL-007`() {
        val post = JournalEntry(
            seq = 1, kind = JournalKind.POST, ts = TestData.ts(0),
            actor = TestData.kid, text = "look, mountains!",
        )
        // Cache knows a different profile only — the author's id is absent.
        val profiles = mapOf(TestData.parent.id to TestData.parent)

        val display = assertIs<JournalDisplay.ManualPost>(
            JournalFeedReducer.displayOf(post, profiles),
        )
        assertEquals("fox", display.authorAvatar)
        assertEquals("Maya", display.authorName)
    }

    @Test
    fun `reduce resolves server post attribution from the profiles cache ANDJRNL-007`() {
        val post = JournalEntry(
            seq = 1, kind = JournalKind.POST, ts = TestData.ts(0),
            actor = TestData.kid, text = "hi",
        )
        val current = TestData.kid.copy(avatar = "unicorn", name = "Maya B.")

        val feed = JournalFeedReducer.reduce(
            serverEntries = listOf(post),
            pendingOutbox = emptyList(),
            selfProfile = null,
            profiles = mapOf(current.id to current),
        )

        val display = assertIs<JournalDisplay.ManualPost>(feed.single().display)
        assertEquals("unicorn", display.authorAvatar)
        assertEquals("Maya B.", display.authorName)
    }

    @Test
    fun `kid and parent profiles get identical journal capabilities ANDJRNL-006`() {
        val kidCaps = composer.capabilities(Role.KID)
        val parentCaps = composer.capabilities(Role.PARENT)

        // No role gate anywhere in composer logic: same object, full capabilities.
        assertEquals(parentCaps, kidCaps)
        assertTrue(kidCaps.canPost)
        assertEquals(1, kidCaps.minChars)
        assertEquals(2000, kidCaps.maxChars)
        assertEquals(false, kidCaps.moderated)

        // And posting itself is role-blind: the same call path serves everyone.
        composer.post("from a kid")
        composer.post("from a parent")
        assertEquals(2, store.pending().size)
    }
}
