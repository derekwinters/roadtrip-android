package com.roadtrip.core.location

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
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive

class TrackerControllerTest {
    private val store = InMemoryOutboxStore()
    private val clock = FakeClock()
    private val queue = OutboxQueue(store, clock, SequentialIds())
    private val controller = TrackerController(queue)

    @Test
    fun `each sample enqueues a location ping with the sample timestamp as client_ts ANDLOC-001`() {
        val sampleTs = TestData.t(300)
        clock.current = TestData.t(345) // enqueue happens later than the GPS fix

        controller.onSample(39.7392, -104.9903, 8.0, sampleTs)

        val entry = store.pending().single()
        assertEquals(OutboxEntry.TYPE_LOCATION_PING, entry.type)
        assertEquals(sampleTs, entry.clientTs) // sample time, NOT enqueue time
        assertEquals(39.7392, entry.payload["lat"]!!.jsonPrimitive.double)
        assertEquals(-104.9903, entry.payload["lon"]!!.jsonPrimitive.double)
        assertEquals(8.0, entry.payload["accuracy_m"]!!.jsonPrimitive.double)
    }

    @Test
    fun `offline samples queue and later flush without loss or duplication ANDLOC-004`() = runTest {
        val api = FakeRoadtripApi().apply { offline = true }
        val engine = SyncEngine(api, store)

        // Three samples in a dead zone.
        controller.onSample(39.0, -105.0, 5.0, TestData.t(0))
        controller.onSample(39.1, -105.1, 5.0, TestData.t(300))
        controller.onSample(39.2, -105.2, 5.0, TestData.t(600))

        assertTrue(engine.flush().networkFailure)
        assertEquals(3, store.pending().size) // nothing lost while offline

        // Reconnect, but the first response gets lost after the server processed it.
        api.offline = false
        api.loseResponses = 1
        engine.flush()
        engine.flush()

        assertEquals(3, api.serverEvents.size) // no duplicates server-side
        assertTrue(store.pending().isEmpty()) // nothing lost client-side
        assertEquals(
            listOf(TestData.ts(0), TestData.ts(300), TestData.ts(600)),
            api.serverEvents.values.map { it.clientTs },
        )
    }

    @Test
    fun `pings are attributed to the enabling parent regardless of the signed-in profile ANDLOC-008`() {
        // The device may be signed in as anyone (even a kid); the controller stamps the
        // parent who enabled the tracker onto every ping outbox entry.
        val attributed = TrackerController(queue, enabledBy = { TestData.parent.id })
        attributed.onSample(39.0, -105.0, 5.0, TestData.t(0))
        assertEquals(TestData.parent.id, store.pending().single().actorProfileId)

        // Without a recorded enabler (legacy install) there is no override.
        controller.onSample(39.1, -105.1, 5.0, TestData.t(300))
        assertEquals(null, store.pending().last().actorProfileId)
    }

    @Test
    fun `three consecutive sample failures raise a quiet warning ANDLOC-007`() {
        controller.onSampleFailure()
        controller.onSampleFailure()
        assertFalse(controller.gpsWarning) // single/double failures skip silently

        controller.onSampleFailure()
        assertTrue(controller.gpsWarning)
        assertEquals(3, controller.consecutiveFailures)
    }

    @Test
    fun `a successful sample resets the failure streak ANDLOC-007`() {
        controller.onSampleFailure()
        controller.onSampleFailure()
        controller.onSample(39.0, -105.0, 5.0, TestData.t(0))
        assertEquals(0, controller.consecutiveFailures)

        controller.onSampleFailure() // a fresh single failure stays silent
        assertFalse(controller.gpsWarning)
    }
}
