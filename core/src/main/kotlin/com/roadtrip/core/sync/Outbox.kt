package com.roadtrip.core.sync

import com.roadtrip.core.common.Clock
import com.roadtrip.core.common.IdGenerator
import com.roadtrip.core.common.UuidIdGenerator
import com.roadtrip.core.storage.OutboxStore
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A locally-created event awaiting upload. The UUID `eventId` is generated exactly once at
 * enqueue time and never regenerated across retries (ANDSYNC-001) — it is the server-side
 * idempotency key (EVT-001).
 */
data class OutboxEntry(
    val eventId: String,
    val type: String,
    val clientTs: Instant,
    val payload: JsonObject,
    /**
     * Attribution override: when set, sync batches containing this entry upload under
     * this profile id instead of the signed-in profile — used to attribute location
     * pings to the parent who enabled the tracker (ANDLOC-008). Null = signed-in profile.
     */
    val actorProfileId: String? = null,
) {
    companion object {
        const val TYPE_JOURNAL_POST = "journal.post"
        const val TYPE_LOCATION_PING = "location.ping"
    }
}

/** Writes client-originated events into the outbox regardless of connectivity. */
class OutboxQueue(
    private val store: OutboxStore,
    private val clock: Clock,
    private val ids: IdGenerator = UuidIdGenerator,
) {
    /** covers: ANDSYNC-001, ANDJRNL-002 */
    fun enqueueJournalPost(text: String): OutboxEntry {
        require(text.length in 1..2000) { "journal posts must be 1-2000 chars, got ${text.length}" }
        return enqueue(
            OutboxEntry.TYPE_JOURNAL_POST,
            clientTs = clock.now(),
            payload = buildJsonObject { put("text", text) },
        )
    }

    /**
     * covers: ANDLOC-001 — client_ts is the GPS sample time, not the enqueue time.
     * [actorProfileId] attributes the ping to the enabling parent (ANDLOC-008).
     */
    fun enqueueLocationPing(
        lat: Double,
        lon: Double,
        accuracyM: Double?,
        sampleTs: Instant,
        actorProfileId: String? = null,
    ): OutboxEntry = enqueue(
        OutboxEntry.TYPE_LOCATION_PING,
        clientTs = sampleTs,
        payload = buildJsonObject {
            put("lat", lat)
            put("lon", lon)
            if (accuracyM != null) put("accuracy_m", accuracyM)
        },
        actorProfileId = actorProfileId,
    )

    private fun enqueue(
        type: String,
        clientTs: Instant,
        payload: JsonObject,
        actorProfileId: String? = null,
    ): OutboxEntry {
        val entry = OutboxEntry(ids.newId(), type, clientTs, payload, actorProfileId)
        store.add(entry)
        return entry
    }
}
