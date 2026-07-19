package com.roadtrip.core.storage

import com.roadtrip.core.api.GameType
import com.roadtrip.core.api.Profile
import com.roadtrip.core.games.ConfirmMovePreference
import com.roadtrip.core.sync.OutboxEntry
import java.time.Instant

class InMemoryOutboxStore : OutboxStore {
    private val entries = LinkedHashMap<String, OutboxEntry>()
    private val quarantined = mutableListOf<QuarantinedEvent>()

    override fun add(entry: OutboxEntry) {
        entries[entry.eventId] = entry
    }

    override fun pending(): List<OutboxEntry> = entries.values.toList()

    override fun remove(eventIds: Collection<String>) {
        eventIds.forEach { entries.remove(it) }
    }

    override fun quarantine(eventId: String, reason: String) {
        val entry = entries.remove(eventId) ?: return
        quarantined += QuarantinedEvent(entry, reason)
    }

    override fun quarantined(): List<QuarantinedEvent> = quarantined.toList()
}

class InMemoryCursorStore : CursorStore {
    private val cursors = mutableMapOf<String, Long>()

    override fun get(key: String): Long = cursors[key] ?: 0L

    override fun set(key: String, value: Long) {
        cursors[key] = value
    }
}

class InMemoryCacheStore<T> : CacheStore<T> {
    private var cached: Cached<T>? = null

    override fun read(): Cached<T>? = cached

    override fun write(value: T, at: Instant) {
        cached = Cached(value, at)
    }
}

class InMemorySelectedProfileStore : SelectedProfileStore {
    private var profile: Profile? = null

    override fun get(): Profile? = profile

    override fun set(profile: Profile?) {
        this.profile = profile
    }
}

class InMemoryTrackerConfigStore : TrackerConfigStore {
    private var enabledBy: String? = null

    override fun enabledBy(): String? = enabledBy

    override fun setEnabledBy(profileId: String?) {
        enabledBy = profileId
    }
}

class InMemoryConfirmMoveStore : ConfirmMoveStore {
    // Keyed by the same builder the app-module store uses, so scoping is exercised end-to-end.
    private val values = mutableMapOf<String, Boolean>()

    override fun get(profileId: String, gameType: GameType): Boolean? =
        values[ConfirmMovePreference.key(profileId, gameType)]

    override fun set(profileId: String, gameType: GameType, confirm: Boolean) {
        values[ConfirmMovePreference.key(profileId, gameType)] = confirm
    }
}
