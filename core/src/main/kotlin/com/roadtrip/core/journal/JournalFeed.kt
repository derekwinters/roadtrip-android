package com.roadtrip.core.journal

import com.roadtrip.core.api.DeepLink
import com.roadtrip.core.api.JournalEntry
import com.roadtrip.core.api.JournalKind
import com.roadtrip.core.api.Profile
import com.roadtrip.core.common.Role
import com.roadtrip.core.common.Timestamps
import com.roadtrip.core.sync.OutboxEntry
import com.roadtrip.core.sync.OutboxQueue
import java.time.Instant
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Kind-specific display data — each journal entry kind renders distinctly (ANDJRNL-001). */
sealed class JournalDisplay {
    data class ManualPost(
        val authorName: String?,
        val authorAvatar: String?,
        val text: String,
    ) : JournalDisplay()

    data class StateCrossing(val text: String, val stateCode: String?) : JournalDisplay()

    data class Stop(val text: String, val pin: DeepLink?) : JournalDisplay()

    data class GameResult(val text: String, val gameId: String?) : JournalDisplay()

    data class LegArrival(val text: String, val destinationId: String?) : JournalDisplay()

    data class TripStarted(val text: String, val tripId: String?) : JournalDisplay()

    data class TripEnded(val text: String, val tripId: String?) : JournalDisplay()
}

data class JournalFeedItem(
    val key: String,
    val clientTs: Instant,
    val display: JournalDisplay,
    val syncing: Boolean,
    val link: DeepLink?,
)

/**
 * Merges the server journal read model with local pending outbox posts (shown immediately,
 * marked syncing — ANDJRNL-002), ordered by client_ts so backdated synced entries land at
 * their written time (ANDJRNL-003, ANDSYNC-006).
 */
object JournalFeedReducer {
    fun reduce(
        serverEntries: List<JournalEntry>,
        pendingOutbox: List<OutboxEntry>,
        selfProfile: Profile?,
        profiles: Map<String, Profile> = emptyMap(),
    ): List<JournalFeedItem> {
        val fromServer = serverEntries.map { entry ->
            JournalFeedItem(
                key = "server-${entry.seq}",
                clientTs = Timestamps.parse(entry.ts),
                display = displayOf(entry, profiles),
                syncing = entry.syncing,
                link = entry.link,
            )
        }
        // Resolve the poster's own attribution from the live cache too, so a pending post
        // reflects an avatar/name change the author just made (ANDJRNL-007).
        val self = selfProfile?.id?.let { profiles[it] } ?: selfProfile
        val fromOutbox = pendingOutbox
            .filter { it.type == OutboxEntry.TYPE_JOURNAL_POST }
            .map { entry ->
                JournalFeedItem(
                    key = "outbox-${entry.eventId}",
                    clientTs = entry.clientTs,
                    display = JournalDisplay.ManualPost(
                        authorName = self?.name,
                        authorAvatar = self?.avatar,
                        text = entry.payload["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    ),
                    syncing = true,
                    link = null,
                )
            }
        // Newest first by client_ts — matching the server's ordering rules (JRNL-002).
        return (fromServer + fromOutbox).sortedByDescending { it.clientTs }
    }

    /**
     * @param profiles current profiles cache (id -> profile). Actor-attributed kinds resolve
     * their avatar/name from here by actor id so a profile changing its avatar updates every
     * one of that author's rows; the embedded [JournalEntry.actor] snapshot is only a fallback
     * for profiles that aren't cached (ANDJRNL-007).
     */
    fun displayOf(entry: JournalEntry, profiles: Map<String, Profile> = emptyMap()): JournalDisplay = when (entry.kind) {
        JournalKind.POST -> {
            val author = entry.actor?.id?.let { profiles[it] } ?: entry.actor
            JournalDisplay.ManualPost(
                authorName = author?.name,
                authorAvatar = author?.avatar,
                text = entry.text,
            )
        }
        JournalKind.STATE_CROSSING -> JournalDisplay.StateCrossing(entry.text, entry.link?.stateCode)
        JournalKind.STOP -> JournalDisplay.Stop(entry.text, entry.link)
        JournalKind.GAME_RESULT -> JournalDisplay.GameResult(entry.text, entry.link?.gameId)
        JournalKind.LEG_ARRIVAL -> JournalDisplay.LegArrival(entry.text, entry.link?.destinationId)
        JournalKind.TRIP_STARTED -> JournalDisplay.TripStarted(entry.text, entry.link?.tripId)
        JournalKind.TRIP_ENDED -> JournalDisplay.TripEnded(entry.text, entry.link?.tripId)
    }
}

/**
 * Pure pagination seam for the journal "load older" action (ANDJRNL-005).
 *
 * The feed — client and server — is ordered by `client_ts`, and the server's `before` cursor
 * compares on the tuple `(client_ts, seq)` (backend JRNL-002). Backdated entries sync late with a
 * high `seq` but an old `client_ts` (ANDJRNL-003), so `seq` order diverges from `client_ts` order
 * and the smallest `seq` in the cache is generally NOT the oldest visible entry. Paging from
 * `min(seq)` therefore points the cursor above the true client_ts fold, the server returns rows
 * already cached, and load-older deadlocks. Paging from the client_ts-oldest cached entry keeps
 * the cursor advancing.
 */
object JournalPagination {
    /**
     * Returns the `before` cursor (the `seq` of the oldest cached entry by `(client_ts, seq)`,
     * ascending) for the next load-older page, or `null` when the cache is empty (no query).
     * Uses the same [Timestamps.parse] the feed uses so ordering matches the render exactly.
     */
    fun loadOlderCursor(cached: List<JournalEntry>): Long? =
        cached.minWithOrNull(
            compareBy({ Timestamps.parse(it.ts) }, { it.seq }),
        )?.seq
}

data class ComposerCapabilities(
    val canPost: Boolean,
    val minChars: Int,
    val maxChars: Int,
    val moderated: Boolean,
)

/**
 * Free-text composer. Capabilities are identical for every role — posting is never
 * moderated or restricted (ANDJRNL-006); posts queue offline (ANDJRNL-002).
 */
class JournalComposer(private val queue: OutboxQueue) {
    /**
     * Role-independent by construction: the role parameter exists only so calling code
     * cannot accidentally introduce a gate — every role gets the same capabilities.
     */
    fun capabilities(@Suppress("UNUSED_PARAMETER") role: Role): ComposerCapabilities = CAPABILITIES

    /** Validates 1–2000 chars and enqueues; works identically for every profile. */
    fun post(text: String): OutboxEntry = queue.enqueueJournalPost(text)

    companion object {
        const val MIN_CHARS = 1
        const val MAX_CHARS = 2000
        private val CAPABILITIES = ComposerCapabilities(
            canPost = true,
            minChars = MIN_CHARS,
            maxChars = MAX_CHARS,
            moderated = false,
        )
    }
}
