package com.roadtrip.core.sync

import com.roadtrip.core.api.DeepLink
import com.roadtrip.core.api.DeepLinkKind
import com.roadtrip.core.api.EventDto
import com.roadtrip.core.api.JournalEntry
import com.roadtrip.core.api.JournalKind
import com.roadtrip.core.api.Profile
import com.roadtrip.core.api.RoadtripApi
import com.roadtrip.core.common.Clock
import com.roadtrip.core.common.Timestamps
import com.roadtrip.core.storage.CacheStore
import com.roadtrip.core.storage.CursorStore
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** A read-model cache that folds pulled events. Appliers MUST be idempotent per event `seq`. */
fun interface EventApplier {
    fun apply(events: List<EventDto>)
}

/**
 * Pulls GET /api/events?after=<cursor>, applies the page to all caches and only THEN
 * advances the cursor — a crash mid-apply re-pulls instead of skipping (ANDSYNC-005).
 */
class InboxPuller(
    private val api: RoadtripApi,
    private val cursors: CursorStore,
    private val appliers: List<EventApplier>,
    private val pageLimit: Int = 200,
    private val cursorKey: String = CursorStore.EVENTS,
) {
    /** Returns the number of events applied by this pull. */
    suspend fun pullOnce(): Int {
        val after = cursors.get(cursorKey)
        val page = api.getEvents(after = after, limit = pageLimit)
        if (page.events.isEmpty()) return 0

        // Apply first...
        appliers.forEach { it.apply(page.events) }
        // ...advance the cursor last. A crash above re-pulls the same page; appliers are
        // idempotent so the re-apply is harmless.
        cursors.set(cursorKey, page.nextAfter)
        return page.events.size
    }
}

/**
 * Folds journal-worthy events into the cached journal read model, ordered by client_ts
 * (the backend's ordering rule, JRNL-002) and de-duplicated by seq so re-applying a page
 * after a crash is harmless (ANDSYNC-005/006).
 */
class JournalCacheApplier(
    private val cache: CacheStore<List<JournalEntry>>,
    private val clock: Clock,
    private val profileLookup: (String) -> Profile? = { null },
) : EventApplier {
    override fun apply(events: List<EventDto>) {
        val current = cache.read()?.value.orEmpty()
        val known = current.mapTo(HashSet()) { it.seq }
        val incoming = events.mapNotNull { toJournalEntry(it) }.filter { it.seq !in known }
        if (incoming.isEmpty() && cache.read() != null) return

        val merged = (current + incoming).sortedByDescending { Timestamps.parse(it.ts) }
        cache.write(merged, clock.now())
    }

    private fun toJournalEntry(event: EventDto): JournalEntry? {
        val p = event.payload
        fun str(key: String): String? = p[key]?.jsonPrimitive?.contentOrNull
        fun num(key: String): Double? = p[key]?.jsonPrimitive?.doubleOrNull

        return when (event.type) {
            "journal.post" -> JournalEntry(
                seq = event.seq,
                kind = JournalKind.POST,
                ts = event.clientTs,
                actor = event.actorId?.let(profileLookup),
                text = str("text") ?: return null,
            )
            "location.stop.ended" -> JournalEntry(
                seq = event.seq,
                kind = JournalKind.STOP,
                ts = event.clientTs,
                text = buildString {
                    append("Stopped")
                    num("duration_min")?.let { append(" ${it.toInt()} min") }
                    str("place")?.let { append(" at $it") }
                },
                link = DeepLink(DeepLinkKind.MAP_PIN, lat = num("lat"), lon = num("lon")),
            )
            "location.crossing.state" -> JournalEntry(
                seq = event.seq,
                kind = JournalKind.STATE_CROSSING,
                ts = event.clientTs,
                text = "Welcome to ${str("state") ?: str("state_code") ?: "a new state"}!",
                link = DeepLink(DeepLinkKind.CHECKLIST, stateCode = str("state_code")),
            )
            "trip.leg.arrived" -> JournalEntry(
                seq = event.seq,
                kind = JournalKind.LEG_ARRIVAL,
                ts = event.clientTs,
                text = "Arrived at ${str("destination_name") ?: "the destination"}",
                link = DeepLink(DeepLinkKind.LEG_SUMMARY, destinationId = str("destination_id")),
            )
            "trip.started" -> JournalEntry(
                seq = event.seq,
                kind = JournalKind.TRIP_STARTED,
                ts = event.clientTs,
                text = "Road trip${str("name")?.let { " \"$it\"" }.orEmpty()} started!",
                link = DeepLink(DeepLinkKind.TRIP_SUMMARY, tripId = str("trip_id")),
            )
            "trip.ended" -> JournalEntry(
                seq = event.seq,
                kind = JournalKind.TRIP_ENDED,
                ts = event.clientTs,
                text = "Road trip${str("name")?.let { " \"$it\"" }.orEmpty()} complete!",
                link = DeepLink(DeepLinkKind.TRIP_SUMMARY, tripId = str("trip_id")),
            )
            "game.finished" -> JournalEntry(
                seq = event.seq,
                kind = JournalKind.GAME_RESULT,
                ts = event.clientTs,
                text = when (val winner = str("winner_profile_id")?.let(profileLookup)?.name) {
                    null -> "Game finished"
                    else -> "$winner won"
                },
                link = DeepLink(DeepLinkKind.GAME_REPLAY, gameId = str("game_id")),
            )
            else -> null
        }
    }
}
