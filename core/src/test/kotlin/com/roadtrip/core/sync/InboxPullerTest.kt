package com.roadtrip.core.sync

import com.roadtrip.core.api.EventDto
import com.roadtrip.core.api.JournalEntry
import com.roadtrip.core.common.Timestamps
import com.roadtrip.core.storage.CursorStore
import com.roadtrip.core.storage.InMemoryCacheStore
import com.roadtrip.core.storage.InMemoryCursorStore
import com.roadtrip.core.testing.FakeClock
import com.roadtrip.core.testing.FakeRoadtripApi
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class InboxPullerTest {
    private val api = FakeRoadtripApi()
    private val cursors = InMemoryCursorStore()
    private val clock = FakeClock()
    private val journalCache = InMemoryCacheStore<List<JournalEntry>>()
    private val journalApplier = JournalCacheApplier(journalCache, clock)

    private fun cachedTexts(): List<String> = journalCache.read()?.value.orEmpty().map { it.text }

    @Test
    fun `advances the cursor only after the page is applied to caches ANDSYNC-005`() = runTest {
        api.feed += TestData.journalPostEvent(1, TestData.parent.id, "first")
        api.feed += TestData.journalPostEvent(2, TestData.kid.id, "second")

        var crash = true
        val crashingApplier = EventApplier { if (crash) error("simulated crash mid-apply") }
        val puller = InboxPuller(api, cursors, listOf(journalApplier, crashingApplier))

        // Crash between applying the journal cache and advancing the cursor.
        assertFailsWith<IllegalStateException> { puller.pullOnce() }
        assertEquals(0, cursors.get(CursorStore.EVENTS))
        assertEquals(2, journalCache.read()?.value?.size)

        // Recovery re-pulls the SAME page; re-applying must be idempotent (no duplicates).
        crash = false
        assertEquals(2, puller.pullOnce())
        assertEquals(2, cursors.get(CursorStore.EVENTS))
        assertEquals(2, journalCache.read()?.value?.size)

        // Next pull starts after the advanced cursor and finds nothing.
        assertEquals(0, puller.pullOnce())
    }

    @Test
    fun `interleaves mixed-device offline entries by client_ts ANDSYNC-006`() = runTest {
        val puller = InboxPuller(api, cursors, listOf(journalApplier))

        // Device A was online: its posts got low seqs at their true times.
        api.feed += TestData.journalPostEvent(1, TestData.parent.id, "left the house", clientTs = TestData.ts(0))
        api.feed += TestData.journalPostEvent(2, TestData.parent.id, "gas stop", clientTs = TestData.ts(1200))
        puller.pullOnce()

        // Device B was offline: its post syncs LATE (higher seq) but was written in between.
        api.feed += TestData.journalPostEvent(3, TestData.kid.id, "spotted a hawk", clientTs = TestData.ts(600))
        puller.pullOnce()

        // The cached feed renders newest-first by client_ts: the late post lands in the middle.
        assertEquals(listOf("gas stop", "spotted a hawk", "left the house"), cachedTexts())
        val cachedTs = journalCache.read()!!.value.map { Timestamps.parse(it.ts) }
        assertEquals(cachedTs.sortedDescending(), cachedTs)
    }

    @Test
    fun `maps derived events to their journal kinds when folding the cache ANDSYNC-006`() = runTest {
        val puller = InboxPuller(api, cursors, listOf(journalApplier))
        api.feed += TestData.stateCrossingEvent(1, "Colorado", "CO")
        api.feed += TestData.journalPostEvent(2, TestData.kid.id, "hi")
        api.feed += TestData.pingEvent(3, 39.0, -105.0) // pings are NOT journal entries

        puller.pullOnce()

        val kinds = journalCache.read()!!.value.map { it.kind.name }.sorted()
        assertEquals(listOf("POST", "STATE_CROSSING"), kinds)
    }
}
