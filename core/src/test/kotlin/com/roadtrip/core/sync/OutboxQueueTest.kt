package com.roadtrip.core.sync

import com.roadtrip.core.storage.InMemoryOutboxStore
import com.roadtrip.core.testing.FakeClock
import com.roadtrip.core.testing.FakeRoadtripApi
import com.roadtrip.core.testing.SequentialIds
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive

class OutboxQueueTest {
    private val store = InMemoryOutboxStore()
    private val clock = FakeClock()
    private val queue = OutboxQueue(store, clock, SequentialIds())

    @Test
    fun `queues offline events with client timestamps and stable ids ANDSYNC-001`() {
        clock.current = TestData.t(60)
        val entry = queue.enqueueJournalPost("hello from the back seat")

        assertEquals("evt-1", entry.eventId)
        assertEquals(TestData.t(60), entry.clientTs)
        assertEquals(OutboxEntry.TYPE_JOURNAL_POST, entry.type)
        assertEquals("hello from the back seat", entry.payload["text"]!!.jsonPrimitive.content)

        // The queued row is exactly the created entry — the UUID was generated once, at
        // enqueue time, and reading the queue never regenerates it.
        assertEquals(listOf(entry), store.pending())
        assertEquals(listOf(entry), store.pending())
    }

    @Test
    fun `keeps the same event id across failed flush retries ANDSYNC-001`() = runTest {
        val api = FakeRoadtripApi().apply { offline = true }
        val engine = SyncEngine(api, store)
        val entry = queue.enqueueJournalPost("written in a dead zone")

        engine.flush() // network failure — entry must stay queued, id untouched
        engine.flush()

        assertEquals(listOf(entry.eventId), store.pending().map { it.eventId })
    }

    @Test
    fun `location pings carry the sample position and timestamp ANDSYNC-001`() {
        val sampleTs = TestData.t(300)
        val entry = queue.enqueueLocationPing(39.74, -104.99, 12.5, sampleTs)

        assertEquals(OutboxEntry.TYPE_LOCATION_PING, entry.type)
        assertEquals(sampleTs, entry.clientTs)
        assertEquals(39.74, entry.payload["lat"]!!.jsonPrimitive.double)
        assertEquals(-104.99, entry.payload["lon"]!!.jsonPrimitive.double)
        assertEquals(12.5, entry.payload["accuracy_m"]!!.jsonPrimitive.double)
    }

    @Test
    fun `rejects journal posts outside the 1-2000 char contract range`() {
        assertFailsWith<IllegalArgumentException> { queue.enqueueJournalPost("") }
        assertFailsWith<IllegalArgumentException> { queue.enqueueJournalPost("x".repeat(2001)) }
        assertTrue(queue.enqueueJournalPost("x".repeat(2000)).payload.containsKey("text"))
    }
}
