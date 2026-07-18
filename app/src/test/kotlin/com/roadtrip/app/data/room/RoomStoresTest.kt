package com.roadtrip.app.data.room

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.roadtrip.core.api.MapState
import com.roadtrip.core.api.TrackPoint
import com.roadtrip.core.storage.CursorStore
import com.roadtrip.core.sync.OutboxEntry
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke tests that the Room-backed stores satisfy the core storage port contracts.
 * covers: ANDSYNC-001 (outbox rows persist with stable event ids and client timestamps),
 * covers: ANDSYNC-004 (quarantined rows leave the retry loop but keep their reason),
 * covers: ANDSYNC-005 (cursor advances persist).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RoomStoresTest {

    private lateinit var db: RoadtripDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RoadtripDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entry(id: String, ts: String, text: String) = OutboxEntry(
        eventId = id,
        type = OutboxEntry.TYPE_JOURNAL_POST,
        clientTs = Instant.parse(ts),
        payload = buildJsonObject { put("text", text) },
    )

    @Test
    fun outboxRoundTripsEntriesUnchanged() {
        val store = RoomOutboxStore(db.outboxDao())
        val original = entry("event-1", "2026-07-18T12:00:00Z", "hello from the car")

        store.add(original)

        assertEquals(listOf(original), store.pending())
    }

    @Test
    fun outboxRemoveDeletesOnlyTheGivenIds() {
        val store = RoomOutboxStore(db.outboxDao())
        store.add(entry("event-1", "2026-07-18T12:00:00Z", "first"))
        store.add(entry("event-2", "2026-07-18T12:01:00Z", "second"))

        store.remove(listOf("event-1"))

        assertEquals(listOf("event-2"), store.pending().map { it.eventId })
    }

    @Test
    fun outboxPersistsTheEnablingParentAttributionOverride() {
        // covers: ANDLOC-008 — the actor override survives the Room round trip.
        val store = RoomOutboxStore(db.outboxDao())
        val ping = OutboxEntry(
            eventId = "ping-1",
            type = OutboxEntry.TYPE_LOCATION_PING,
            clientTs = Instant.parse("2026-07-18T12:00:00Z"),
            payload = buildJsonObject {
                put("lat", 39.7392)
                put("lon", -104.9903)
            },
            actorProfileId = "p-parent",
        )

        store.add(ping)

        assertEquals(ping, store.pending().single())
        assertEquals("p-parent", store.pending().single().actorProfileId)
    }

    @Test
    fun outboxQuarantineRetainsReasonAndLeavesRetryLoop() {
        val store = RoomOutboxStore(db.outboxDao())
        store.add(entry("event-1", "2026-07-18T12:00:00Z", "bad"))

        store.quarantine("event-1", "unknown_event_type")

        assertTrue(store.pending().isEmpty())
        val quarantined = store.quarantined()
        assertEquals(1, quarantined.size)
        assertEquals("event-1", quarantined[0].entry.eventId)
        assertEquals("unknown_event_type", quarantined[0].reason)
    }

    @Test
    fun cursorStoreDefaultsToZeroAndPersistsAdvances() {
        val store = RoomCursorStore(db.cursorDao())

        assertEquals(0L, store.get(CursorStore.EVENTS))
        store.set(CursorStore.EVENTS, 42L)
        assertEquals(42L, store.get(CursorStore.EVENTS))

        // Other keys stay independent.
        assertEquals(0L, store.get(CursorStore.NOTIFICATIONS))
        store.set(CursorStore.EVENTS, 43L)
        assertEquals(43L, store.get(CursorStore.EVENTS))
    }

    @Test
    fun cacheStoreRoundTripsTypedValuesWithStoredAt() {
        val store = RoomCacheStore(db.cacheDao(), "map", MapState.serializer())
        assertNull(store.read())

        val value = MapState(
            current = TrackPoint(44.5, -110.3, "2026-07-18T12:00:00Z"),
            breadcrumb = listOf(TrackPoint(44.0, -110.0)),
            legMiles = 12.5,
        )
        val at = Instant.parse("2026-07-18T12:34:00Z")
        store.write(value, at)

        val cached = store.read()
        assertNotNull(cached)
        assertEquals(value, cached.value)
        assertEquals(at, cached.storedAt)
    }
}
