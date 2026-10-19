package com.roadtrip.core.settings

import com.roadtrip.core.api.Checklist
import com.roadtrip.core.api.ChecklistStopEntry
import com.roadtrip.core.api.RoadtripJson
import com.roadtrip.core.common.Role
import com.roadtrip.core.storage.InMemoryCacheStore
import com.roadtrip.core.storage.InMemoryOutboxStore
import com.roadtrip.core.sync.OutboxEntry
import com.roadtrip.core.sync.OutboxQueue
import com.roadtrip.core.sync.SyncEngine
import com.roadtrip.core.testing.FakeClock
import com.roadtrip.core.testing.FakeRoadtripApi
import com.roadtrip.core.testing.SequentialIds
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Naming a checklist stop (docs/spec/07-settings.md, issue #160). */
class StopNamingTest {
    private val clock = FakeClock()
    private val store = InMemoryOutboxStore()
    private val queue = OutboxQueue(store, clock, SequentialIds())

    private val stopA = "6f1c2a3b-0000-4000-8000-00000000000a"
    private val stopB = "6f1c2a3b-0000-4000-8000-00000000000b"

    private fun row(label: String? = null, place: String? = null) =
        StopRow(stopA, 41.6, -93.6, TestData.t(0), 25.0, place, label)

    @Test
    fun `stop entries decode stop_id and the optional label ANDSET-003`() {
        val checklist = RoadtripJson.decodeFromString(
            Checklist.serializer(),
            """{"states":[],"cities":[],"stops":[
              {"stop_id":"$stopA","lat":41.6,"lon":-93.6,"place":"Des Moines","label":"Grandma's"},
              {"stop_id":"$stopB","lat":40.0,"lon":-95.0}
            ]}""",
        )

        val stops = ChecklistReducer.fromDto(checklist).stops
        assertEquals(listOf(stopA, stopB), stops.map { it.stopId })
        assertEquals("Grandma's", stops[0].label)
        assertEquals("Des Moines", stops[0].place)
        assertNull(stops[1].label)
    }

    @Test
    fun `display name is label then place then lat lon ANDSET-013`() {
        assertEquals("Grandma's", row(label = "Grandma's", place = "Des Moines").displayName)
        assertEquals("Des Moines", row(place = "Des Moines").displayName)
        assertEquals("41.6000, -93.6000", row().displayName)
    }

    @Test
    fun `long-press as a parent opens the dialog pre-filled with the current name ANDSET-014`() {
        assertEquals(StopNameDraft(stopA, "Grandma's"), StopNaming.open(row("Grandma's", "Des Moines"), Role.PARENT))
        assertEquals(StopNameDraft(stopA, "Des Moines"), StopNaming.open(row(place = "Des Moines"), Role.PARENT))
        assertEquals(StopNameDraft(stopA, ""), StopNaming.open(row(), Role.PARENT))
    }

    @Test
    fun `long-press as a kid or with no profile does nothing ANDSET-014`() {
        assertNull(StopNaming.open(row(place = "Des Moines"), Role.KID))
        assertNull(StopNaming.open(row(place = "Des Moines"), null))
    }

    @Test
    fun `save enqueues exactly one stop-label event with stop_id and trimmed label ANDSET-015`() {
        clock.advanceSeconds(90)

        val result = StopNaming.save(queue, stopA, "  Grandma's house  ")

        val saved = assertIs<StopNameSave.Saved>(result)
        val entry = store.pending().single()
        assertEquals(saved.entry, entry)
        assertEquals("location.stop.labeled", entry.type)
        assertEquals(OutboxEntry.TYPE_STOP_LABELED, entry.type)
        assertEquals(stopA, entry.payload["stop_id"]?.jsonPrimitive?.content)
        assertEquals("Grandma's house", entry.payload["label"]?.jsonPrimitive?.content)
        assertEquals(setOf("stop_id", "label"), entry.payload.keys)
        assertEquals(TestData.t(90), entry.clientTs)
        assertNull(entry.actorProfileId)
    }

    @Test
    fun `a blank name cannot be saved and nothing is enqueued ANDSET-015`() {
        assertFalse(StopNaming.canSave(""))
        assertFalse(StopNaming.canSave("   \t "))
        assertTrue(StopNaming.canSave(" x "))

        assertEquals(StopNameSave.Blank, StopNaming.save(queue, stopA, ""))
        assertEquals(StopNameSave.Blank, StopNaming.save(queue, stopA, "   "))
        assertTrue(store.pending().isEmpty())
    }

    @Test
    fun `pending outbox labels overlay the cached checklist latest client_ts winning ANDSET-016`() {
        val cached = Checklist(
            stops = listOf(
                ChecklistStopEntry(stopA, 41.6, -93.6, place = "Des Moines"),
                ChecklistStopEntry(stopB, 40.0, -95.0, label = "Server name"),
            ),
        )
        val later = queue.enqueueStopLabel(stopA, "Second")
        clock.advanceSeconds(-60)
        queue.enqueueStopLabel(stopA, "First")
        queue.enqueueStopLabel("unknown-stop", "Ghost")
        queue.enqueueJournalPost("not a label")

        val stops = ChecklistReducer.fromDto(cached, store.pending()).stops

        assertEquals("Second", stops[0].displayName)
        assertEquals("Second", stops[0].label)
        assertEquals("Des Moines", stops[0].place)
        assertEquals("Server name", stops[1].displayName)
        assertEquals(2, stops.size)
        assertEquals("location.stop.labeled", later.type)
    }

    @Test
    fun `stop-label feed events update the cached stop label ANDSET-016`() {
        val cached = Checklist(stops = listOf(ChecklistStopEntry(stopA, 41.6, -93.6, place = "Des Moines")))
        val event = TestData.event(
            40,
            "location.stop.labeled",
            buildJsonObject {
                put("stop_id", stopA)
                put("label", "Grandma's")
            },
            actorId = TestData.parent.id,
        )

        val updated = ChecklistReducer.applyToCache(cached, event)
        assertEquals("Grandma's", updated.stops.single().label)
        assertEquals("Des Moines", updated.stops.single().place)

        // Unknown stops and other event types leave the cache untouched.
        val unknown = TestData.event(
            41,
            "location.stop.labeled",
            buildJsonObject {
                put("stop_id", stopB)
                put("label", "Nowhere")
            },
        )
        assertEquals(updated, ChecklistReducer.applyToCache(updated, unknown))
        assertEquals(updated, ChecklistReducer.applyToCache(updated, TestData.pingEvent(42, 1.0, 2.0)))
    }

    @Test
    fun `offline round trip shows the name at once and it survives a sync ANDSET-016`() = runTest {
        val api = FakeRoadtripApi()
        val cache = InMemoryCacheStore<Checklist>()
        cache.write(Checklist(stops = listOf(ChecklistStopEntry(stopA, 41.6, -93.6, place = "Des Moines"))), clock.now())

        // Offline: the parent names the stop; the row shows it immediately from the outbox.
        api.offline = true
        assertIs<StopNameSave.Saved>(StopNaming.save(queue, stopA, "Grandma's"))
        assertEquals("Grandma's", ChecklistReducer.fromDto(cache.read()!!.value, store.pending()).stops.single().displayName)

        val engine = SyncEngine(api, store)
        assertTrue(engine.flush().networkFailure)
        assertEquals(1, store.pending().size)
        assertEquals("Grandma's", ChecklistReducer.fromDto(cache.read()!!.value, store.pending()).stops.single().displayName)

        // Reconnect: the event uploads and leaves the outbox...
        api.offline = false
        assertEquals(1, engine.flush().accepted)
        assertTrue(store.pending().isEmpty())
        val uploaded = api.serverEvents.values.single()
        assertEquals("location.stop.labeled", uploaded.type)
        assertEquals(stopA, uploaded.payload["stop_id"]?.jsonPrimitive?.content)

        // ...and the name survives via the feed event applied to the cache...
        val echoed = TestData.event(7, uploaded.type, uploaded.payload, TestData.parent.id)
        cache.write(ChecklistReducer.applyToCache(cache.read()!!.value, echoed), clock.now())
        assertEquals("Grandma's", ChecklistReducer.fromDto(cache.read()!!.value, store.pending()).stops.single().displayName)

        // ...and the server's refreshed read model, which now carries the label.
        api.checklist = Checklist(
            stops = listOf(ChecklistStopEntry(stopA, 41.6, -93.6, place = "Des Moines", label = "Grandma's")),
        )
        cache.write(api.getChecklist(), clock.now())
        assertEquals("Grandma's", ChecklistReducer.fromDto(cache.read()!!.value, store.pending()).stops.single().displayName)
    }
}
