package com.roadtrip.app.data

import com.roadtrip.core.api.BingoCard
import com.roadtrip.core.api.ChecklistStateEntry
import com.roadtrip.core.api.Checklist
import com.roadtrip.core.api.EventDto
import com.roadtrip.core.api.Game
import com.roadtrip.core.api.MapState
import com.roadtrip.core.api.Profile
import com.roadtrip.core.api.TrackPoint
import com.roadtrip.core.bingo.BingoReducer
import com.roadtrip.core.common.Clock
import com.roadtrip.core.games.LobbyReducer
import com.roadtrip.core.storage.CacheStore
import com.roadtrip.core.storage.CursorStore
import com.roadtrip.core.sync.EventApplier
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * App-side event appliers folding pulled events into cached read models between full
 * refreshes. Each applier keeps its own applied-seq cursor so re-applying a page after a
 * crash is harmless (ANDSYNC-005 idempotency contract of [EventApplier]).
 */
abstract class SeqGuardedApplier(
    private val cursors: CursorStore,
    private val cursorKey: String,
) : EventApplier {
    final override fun apply(events: List<EventDto>) {
        val last = cursors.get(cursorKey)
        val fresh = events.filter { it.seq > last }
        if (fresh.isEmpty()) return
        applyFresh(fresh)
        cursors.set(cursorKey, fresh.maxOf { it.seq })
    }

    protected abstract fun applyFresh(events: List<EventDto>)
}

/** Extends the cached map breadcrumb from live location.ping events (ANDMAP-003). */
class MapCacheApplier(
    private val cache: CacheStore<MapState>,
    cursors: CursorStore,
    private val clock: Clock,
) : SeqGuardedApplier(cursors, "applied_map_seq") {
    override fun applyFresh(events: List<EventDto>) {
        val pings = events.filter { it.type == "location.ping" }
        if (pings.isEmpty()) return
        val cached = cache.read() ?: return
        var map = cached.value
        for (ping in pings) {
            val lat = ping.payload["lat"]?.jsonPrimitive?.doubleOrNull ?: continue
            val lon = ping.payload["lon"]?.jsonPrimitive?.doubleOrNull ?: continue
            val point = TrackPoint(lat, lon, ping.clientTs)
            map = map.copy(current = point, breadcrumb = map.breadcrumb + point)
        }
        cache.write(map, clock.now())
    }
}

/** Lights up newly-crossed states in the cached checklist (ANDSET-003). */
class ChecklistCacheApplier(
    private val cache: CacheStore<Checklist>,
    cursors: CursorStore,
    private val clock: Clock,
) : SeqGuardedApplier(cursors, "applied_checklist_seq") {
    override fun applyFresh(events: List<EventDto>) {
        val crossings = events.filter { it.type == "location.crossing.state" }
        if (crossings.isEmpty()) return
        val cached = cache.read() ?: return
        var checklist = cached.value
        for (event in crossings) {
            val code = event.payload["state_code"]?.jsonPrimitive?.contentOrNull ?: continue
            if (checklist.states.any { it.stateCode == code }) continue
            val name = event.payload["state"]?.jsonPrimitive?.contentOrNull ?: code
            checklist = checklist.copy(
                states = checklist.states + ChecklistStateEntry(name, code, event.clientTs),
            )
        }
        cache.write(checklist, clock.now())
    }
}

/**
 * Folds live plate.* events into the cached bingo card via the core reducer, so cells
 * fill/clear without a reload (ANDBNG-004). Never touches notifications or the journal
 * (ANDBNG-005).
 */
class BingoCacheApplier(
    private val cache: CacheStore<BingoCard>,
    cursors: CursorStore,
    private val clock: Clock,
    private val profileLookup: (String) -> Profile? = { null },
) : SeqGuardedApplier(cursors, "applied_bingo_seq") {
    override fun applyFresh(events: List<EventDto>) {
        val plateEvents = events.filter { it.type.startsWith("plate.") }
        if (plateEvents.isEmpty()) return
        var card = cache.read()?.value ?: return
        val profilesById = plateEvents
            .mapNotNull { it.actorId }
            .distinct()
            .mapNotNull(profileLookup)
            .associateBy { it.id }
        for (event in plateEvents) {
            card = BingoReducer.applyEvent(card, event, profilesById)
        }
        cache.write(card, clock.now())
    }
}

/** Folds game.* events into the cached games list via the core lobby reducer (ANDGAME-001). */
class GamesCacheApplier(
    private val cache: CacheStore<List<Game>>,
    cursors: CursorStore,
    private val clock: Clock,
) : SeqGuardedApplier(cursors, "applied_games_seq") {
    override fun applyFresh(events: List<EventDto>) {
        val gameEvents = events.filter { it.type.startsWith("game.") }
        if (gameEvents.isEmpty()) return
        var games = cache.read()?.value ?: return
        for (event in gameEvents) {
            games = LobbyReducer.applyEvent(games, event)
        }
        cache.write(games, clock.now())
    }
}
