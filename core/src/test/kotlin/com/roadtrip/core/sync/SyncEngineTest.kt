package com.roadtrip.core.sync

import com.roadtrip.core.storage.InMemoryOutboxStore
import com.roadtrip.core.testing.FakeRoadtripApi
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SyncEngineTest {
    private val api = FakeRoadtripApi()
    private val store = InMemoryOutboxStore()

    private fun entry(id: String, atSeconds: Long, text: String = "post $id"): OutboxEntry =
        OutboxEntry(
            eventId = id,
            type = OutboxEntry.TYPE_JOURNAL_POST,
            clientTs = TestData.t(atSeconds),
            payload = buildJsonObject { put("text", text) },
        )

    @Test
    fun `flushes outbox entries in client_ts order and removes them on accepted ANDSYNC-002`() = runTest {
        // Enqueued out of chronological order on purpose.
        store.add(entry("e-c", 300))
        store.add(entry("e-a", 100))
        store.add(entry("e-b", 200))

        val result = SyncEngine(api, store).flush()

        assertEquals(listOf("e-a", "e-b", "e-c"), api.syncBatches.single().events.map { it.eventId })
        assertEquals(3, result.accepted)
        assertTrue(store.pending().isEmpty())
    }

    @Test
    fun `chunks batches at the 500-event contract maximum ANDSYNC-002`() = runTest {
        repeat(501) { store.add(entry("e-$it", it.toLong())) }

        SyncEngine(api, store).flush()

        assertEquals(listOf(500, 1), api.syncBatches.map { it.events.size })
        assertTrue(store.pending().isEmpty())
    }

    @Test
    fun `keeps entries queued for the next pass on network failure ANDSYNC-002`() = runTest {
        api.offline = true
        store.add(entry("e-1", 100))
        store.add(entry("e-2", 200))

        val result = SyncEngine(api, store).flush()

        assertTrue(result.networkFailure)
        assertEquals(2, result.remaining)
        assertEquals(listOf("e-1", "e-2"), store.pending().map { it.eventId })

        // Reconnect: the same entries flush cleanly.
        api.offline = false
        val retry = SyncEngine(api, store).flush()
        assertEquals(2, retry.accepted)
        assertTrue(store.pending().isEmpty())
    }

    @Test
    fun `retry after a lost response yields duplicates not double entries ANDSYNC-003`() = runTest {
        store.add(entry("e-1", 100))
        store.add(entry("e-2", 200))
        api.loseResponses = 1 // server processes the batch, response never arrives

        val first = SyncEngine(api, store).flush()
        assertTrue(first.networkFailure)
        assertEquals(2, store.pending().size)
        assertEquals(2, api.serverEvents.size) // server DID process the batch

        val second = SyncEngine(api, store).flush()
        assertEquals(0, second.accepted)
        assertEquals(2, second.duplicate)
        assertEquals(2, api.serverEvents.size) // still exactly one copy of each
        assertTrue(store.pending().isEmpty())
    }

    @Test
    fun `groups batches by actor identity so pings flush under the enabling parent ANDLOC-008`() = runTest {
        // Kid journal post, two parent-attributed pings, another kid post — in ts order.
        store.add(entry("e-1", 100))
        store.add(ping("ping-1", 200, actor = TestData.parent.id))
        store.add(ping("ping-2", 300, actor = TestData.parent.id))
        store.add(entry("e-2", 400))

        val result = SyncEngine(api, store).flush()

        assertEquals(4, result.accepted)
        // Entries with different actors never share a batch...
        assertEquals(listOf(1, 2, 1), api.syncBatches.map { it.events.size })
        assertEquals(listOf(null, TestData.parent.id, null), api.syncBatchActors)
        // ...while client_ts order is preserved across the actor-grouped batches.
        assertEquals(
            listOf("e-1", "ping-1", "ping-2", "e-2"),
            api.syncBatches.flatMap { batch -> batch.events.map { it.eventId } },
        )
        assertTrue(store.pending().isEmpty())
    }

    private fun ping(id: String, atSeconds: Long, actor: String?): OutboxEntry =
        OutboxEntry(
            eventId = id,
            type = OutboxEntry.TYPE_LOCATION_PING,
            clientTs = TestData.t(atSeconds),
            payload = buildJsonObject {
                put("lat", 39.0)
                put("lon", -105.0)
            },
            actorProfileId = actor,
        )

    @Test
    fun `quarantines rejected events with their reason and never retries them ANDSYNC-004`() = runTest {
        store.add(entry("e-good", 100))
        store.add(entry("e-bad", 200))
        api.rejectWhen = { if (it.eventId == "e-bad") "not_parent" else null }

        val result = SyncEngine(api, store).flush()

        assertEquals(1, result.accepted)
        assertEquals(1, result.rejected)
        assertTrue(store.pending().isEmpty())
        val quarantined = store.quarantined().single()
        assertEquals("e-bad", quarantined.entry.eventId)
        assertEquals("not_parent", quarantined.reason)

        // The next pass has nothing to send — quarantined events are out of the loop.
        SyncEngine(api, store).flush()
        assertEquals(1, api.syncBatches.size)
    }
}
