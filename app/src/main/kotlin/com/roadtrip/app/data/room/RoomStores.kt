package com.roadtrip.app.data.room

import com.roadtrip.core.api.RoadtripJson
import com.roadtrip.core.storage.CacheStore
import com.roadtrip.core.storage.Cached
import com.roadtrip.core.storage.CursorStore
import com.roadtrip.core.storage.OutboxStore
import com.roadtrip.core.storage.QuarantinedEvent
import com.roadtrip.core.sync.OutboxEntry
import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Room-backed implementations of the core storage ports (docs/spec/01-sync.md). */

class RoomOutboxStore(private val dao: OutboxDao) : OutboxStore {
    override fun add(entry: OutboxEntry) {
        dao.insert(entry.toRow())
    }

    override fun pending(): List<OutboxEntry> = dao.pending().map { it.toEntry() }

    override fun remove(eventIds: Collection<String>) {
        if (eventIds.isEmpty()) return
        dao.remove(eventIds.toList())
    }

    override fun quarantine(eventId: String, reason: String) {
        dao.quarantine(eventId, reason)
    }

    override fun quarantined(): List<QuarantinedEvent> =
        dao.quarantined().map { QuarantinedEvent(it.toEntry(), it.reason ?: "rejected") }

    private fun OutboxEntry.toRow() = OutboxEventRow(
        eventId = eventId,
        type = type,
        clientTs = clientTs.toString(),
        payload = payload.toString(),
        quarantined = false,
        reason = null,
    )

    private fun OutboxEventRow.toEntry() = OutboxEntry(
        eventId = eventId,
        type = type,
        clientTs = Instant.parse(clientTs),
        payload = RoadtripJson.parseToJsonElement(payload) as JsonObject,
    )
}

class RoomCursorStore(private val dao: CursorDao) : CursorStore {
    override fun get(key: String): Long = dao.get(key) ?: 0L

    override fun set(key: String, value: Long) {
        dao.set(CursorRow(key, value))
    }
}

/**
 * Generic key -> JSON cache row implementing CacheStore<T>; one instance per read model
 * (journal, map, checklist, games, ...), all sharing the `cache` table.
 */
class RoomCacheStore<T>(
    private val dao: CacheDao,
    private val key: String,
    private val serializer: KSerializer<T>,
    private val json: Json = RoadtripJson,
) : CacheStore<T> {
    override fun read(): Cached<T>? {
        val row = dao.get(key) ?: return null
        val value = try {
            json.decodeFromString(serializer, row.json)
        } catch (e: Exception) {
            return null // stale/incompatible cache rows are treated as absent
        }
        return Cached(value, Instant.parse(row.storedAt))
    }

    override fun write(value: T, at: Instant) {
        dao.put(CacheRow(key, json.encodeToString(serializer, value), at.toString()))
    }
}
