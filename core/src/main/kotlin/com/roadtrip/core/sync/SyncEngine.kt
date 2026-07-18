package com.roadtrip.core.sync

import com.roadtrip.core.api.ClientEvent
import com.roadtrip.core.api.RoadtripApi
import com.roadtrip.core.api.SyncBatchRequest
import com.roadtrip.core.api.SyncStatus
import com.roadtrip.core.common.Timestamps
import com.roadtrip.core.storage.OutboxStore
import java.io.IOException

data class FlushResult(
    val accepted: Int,
    val duplicate: Int,
    val rejected: Int,
    val remaining: Int,
    val networkFailure: Boolean,
) {
    val clean: Boolean get() = !networkFailure && remaining == 0
}

/**
 * Uploads the outbox via POST /api/sync/batch in client_ts order, chunked at ≤500 events
 * (contract maxItems). Entries are removed only on `accepted`/`duplicate`; network failure
 * keeps them queued; `rejected` entries are quarantined with their reason
 * (ANDSYNC-002/003/004).
 */
class SyncEngine(
    private val api: RoadtripApi,
    private val store: OutboxStore,
    private val deviceId: String? = null,
    private val chunkSize: Int = MAX_BATCH,
) {
    init {
        require(chunkSize in 1..MAX_BATCH) { "chunkSize must be 1..$MAX_BATCH" }
    }

    suspend fun flush(): FlushResult {
        var accepted = 0
        var duplicate = 0
        var rejected = 0

        val ordered = store.pending().sortedWith(compareBy({ it.clientTs }, { it.eventId }))
        for (chunk in ordered.chunked(chunkSize)) {
            val request = SyncBatchRequest(
                deviceId = deviceId,
                events = chunk.map { it.toClientEvent() },
            )
            val result = try {
                api.syncBatch(request)
            } catch (e: IOException) {
                // Unreachable server OR a lost response after processing: either way the
                // entries stay queued; event_id idempotency makes the retry safe.
                return FlushResult(accepted, duplicate, rejected, store.pending().size, networkFailure = true)
            }

            val done = mutableListOf<String>()
            for (status in result.results) {
                when (status.status) {
                    SyncStatus.ACCEPTED -> {
                        accepted++
                        done += status.eventId
                    }
                    SyncStatus.DUPLICATE -> {
                        duplicate++
                        done += status.eventId
                    }
                    SyncStatus.REJECTED -> {
                        rejected++
                        store.quarantine(status.eventId, status.reason ?: "rejected")
                    }
                }
            }
            store.remove(done)
        }
        return FlushResult(accepted, duplicate, rejected, store.pending().size, networkFailure = false)
    }

    private fun OutboxEntry.toClientEvent() = ClientEvent(
        eventId = eventId,
        type = type,
        clientTs = Timestamps.format(clientTs),
        payload = payload,
    )

    companion object {
        const val MAX_BATCH = 500
    }
}
