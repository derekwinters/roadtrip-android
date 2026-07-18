package com.roadtrip.core.storage

import com.roadtrip.core.api.Profile
import com.roadtrip.core.sync.OutboxEntry
import java.time.Instant

/**
 * Storage ports the app module implements with Room/DataStore. The core module ships
 * in-memory implementations used by tests (and usable as initial app storage).
 */

data class QuarantinedEvent(val entry: OutboxEntry, val reason: String)

interface OutboxStore {
    fun add(entry: OutboxEntry)

    /** Entries still awaiting sync (excludes quarantined ones). */
    fun pending(): List<OutboxEntry>

    fun remove(eventIds: Collection<String>)

    /** Moves an entry out of the retry loop, retaining the rejection reason (ANDSYNC-004). */
    fun quarantine(eventId: String, reason: String)

    fun quarantined(): List<QuarantinedEvent>
}

interface CursorStore {
    fun get(key: String): Long
    fun set(key: String, value: Long)

    companion object {
        const val EVENTS = "events"
        const val NOTIFICATIONS = "notifications"
    }
}

data class Cached<T>(val value: T, val storedAt: Instant)

interface CacheStore<T> {
    fun read(): Cached<T>?
    fun write(value: T, at: Instant)
}

interface SelectedProfileStore {
    fun get(): Profile?
    fun set(profile: Profile?)
}
