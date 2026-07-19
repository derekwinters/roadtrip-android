package com.roadtrip.core.storage

import com.roadtrip.core.api.GameType
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

/**
 * Per-device tracker configuration: which parent enabled tracking here. Null means the
 * tracker is disabled. Every `location.ping` this device reports is attributed to the
 * recorded parent, regardless of the signed-in profile (ANDLOC-003/008).
 */
interface TrackerConfigStore {
    fun enabledBy(): String?
    fun setEnabledBy(profileId: String?)
}

/**
 * Per-`(profileId, gameType)` "confirm move before making it" preference (ANDGAME-022).
 * Client-side only, persisted locally across app restarts. [get] returns the profile's last-set
 * value for that game type, or null when the pair was never set (the caller resolves the ON
 * default via `ConfirmMovePreference.resolve`). [set] is last-write-wins and must not affect any
 * other `(profileId, gameType)` pair.
 */
interface ConfirmMoveStore {
    fun get(profileId: String, gameType: GameType): Boolean?
    fun set(profileId: String, gameType: GameType, confirm: Boolean)
}
