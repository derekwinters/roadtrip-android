package com.roadtrip.core.testing

import com.roadtrip.core.api.Checklist
import com.roadtrip.core.api.ClientEvent
import com.roadtrip.core.api.Config
import com.roadtrip.core.api.ConfigPatch
import com.roadtrip.core.api.CreateGameRequest
import com.roadtrip.core.api.Destination
import com.roadtrip.core.api.EventDto
import com.roadtrip.core.api.EventsPage
import com.roadtrip.core.api.Game
import com.roadtrip.core.api.GameStatus
import com.roadtrip.core.api.HealthResponse
import com.roadtrip.core.api.JournalEntry
import com.roadtrip.core.api.JournalKind
import com.roadtrip.core.api.JournalPage
import com.roadtrip.core.api.Leg
import com.roadtrip.core.api.MapState
import com.roadtrip.core.api.NotificationsPage
import com.roadtrip.core.api.Profile
import com.roadtrip.core.api.ProfilePatch
import com.roadtrip.core.api.RoadtripApi
import com.roadtrip.core.api.SyncBatchRequest
import com.roadtrip.core.api.SyncBatchResult
import com.roadtrip.core.api.SyncEventResult
import com.roadtrip.core.api.SyncStatus
import com.roadtrip.core.api.TripSummary
import com.roadtrip.core.common.Role
import java.io.IOException
import kotlinx.serialization.json.JsonElement

/**
 * Fake server harness for logic tests (08-testing.md layer 4): an in-memory event-sourced
 * server with idempotent sync-batch handling, an offline switch, and a "processed but
 * response lost" failure mode for the ANDSYNC-003 scenario.
 */
class FakeRoadtripApi : RoadtripApi {
    var offline: Boolean = false

    /** Server-side event store keyed by event_id (idempotency, EVT-001). */
    val serverEvents = LinkedHashMap<String, ClientEvent>()
    private var nextSeq = 1L

    /** Batches the server actually received, in order. */
    val syncBatches = mutableListOf<SyncBatchRequest>()

    /** When > 0: the next batch is fully processed, then the response is "lost". */
    var loseResponses: Int = 0

    /** Per-event rejection rule; non-null return = rejected with that reason. */
    var rejectWhen: (ClientEvent) -> String? = { null }

    /** The /api/events feed. */
    val feed = mutableListOf<EventDto>()

    var profiles: List<Profile> = emptyList()
    var config: Config = Config(300, 100.0, 10.0, 800.0, 10.0)
    var putConfigHandler: (suspend (ConfigPatch) -> Config)? = null
    var destinations: List<Destination> = emptyList()
    var journalPage: JournalPage = JournalPage(emptyList(), null)
    var mapState: MapState = MapState(breadcrumb = emptyList())
    var checklist: Checklist = Checklist()
    var legs: List<Leg> = emptyList()
    var tripSummary: TripSummary = TripSummary(0.0, 0.0, 0.0, 0, 0, 0)
    var games: List<Game> = emptyList()
    var moveHandler: suspend (String, JsonElement) -> Game = { _, _ ->
        throw UnsupportedOperationException("moveHandler not stubbed")
    }
    var gameEventsHandler: (suspend (String, Long, Int?) -> EventsPage)? = null
    var notificationsPage: NotificationsPage = NotificationsPage(emptyList(), 0)

    var healthChecks = 0
        private set

    private fun guard() {
        if (offline) throw IOException("simulated offline")
    }

    override suspend fun health(): HealthResponse {
        healthChecks++
        guard()
        return HealthResponse("ok", "test")
    }

    override suspend fun getProfiles(): List<Profile> {
        guard()
        return profiles
    }

    override suspend fun createProfile(name: String, avatar: String?, role: Role): Profile =
        throw UnsupportedOperationException("not stubbed")

    override suspend fun updateProfile(id: String, patch: ProfilePatch): Profile =
        throw UnsupportedOperationException("not stubbed")

    override suspend fun getConfig(): Config {
        guard()
        return config
    }

    override suspend fun putConfig(patch: ConfigPatch): Config {
        guard()
        val handler = putConfigHandler
        if (handler != null) return handler(patch)
        config = Config(
            pingIntervalS = patch.pingIntervalS ?: config.pingIntervalS,
            stopRadiusM = patch.stopRadiusM ?: config.stopRadiusM,
            minStopDurationMin = patch.minStopDurationMin ?: config.minStopDurationMin,
            arrivalRadiusM = patch.arrivalRadiusM ?: config.arrivalRadiusM,
            cityRadiusKm = patch.cityRadiusKm ?: config.cityRadiusKm,
        )
        return config
    }

    override suspend fun getDestinations(): List<Destination> {
        guard()
        return destinations
    }

    override suspend fun syncBatch(request: SyncBatchRequest): SyncBatchResult {
        guard()
        syncBatches += request
        val results = request.events.map { event ->
            val rejection = rejectWhen(event)
            when {
                rejection != null -> SyncEventResult(event.eventId, SyncStatus.REJECTED, reason = rejection)
                serverEvents.containsKey(event.eventId) ->
                    SyncEventResult(event.eventId, SyncStatus.DUPLICATE)
                else -> {
                    serverEvents[event.eventId] = event
                    SyncEventResult(event.eventId, SyncStatus.ACCEPTED, seq = nextSeq++)
                }
            }
        }
        if (loseResponses > 0) {
            loseResponses--
            throw IOException("simulated lost response after processing")
        }
        return SyncBatchResult(results)
    }

    override suspend fun getEvents(
        after: Long,
        limit: Int?,
        types: List<String>?,
        waitSeconds: Int?,
    ): EventsPage {
        guard()
        val matching = feed
            .filter { it.seq > after && (types == null || it.type in types) }
            .sortedBy { it.seq }
            .take(limit ?: 200)
        return EventsPage(matching, matching.lastOrNull()?.seq ?: after)
    }

    override suspend fun getJournal(before: Long?, limit: Int?): JournalPage {
        guard()
        return journalPage
    }

    override suspend fun postJournal(text: String): JournalEntry {
        guard()
        return JournalEntry(seq = nextSeq++, kind = JournalKind.POST, ts = "2026-07-18T12:00:00Z", text = text)
    }

    override suspend fun getMap(maxPoints: Int?): MapState {
        guard()
        return mapState
    }

    override suspend fun getChecklist(): Checklist {
        guard()
        return checklist
    }

    override suspend fun getLegs(): List<Leg> {
        guard()
        return legs
    }

    override suspend fun getLeg(destinationId: String): Leg {
        guard()
        return legs.first { it.destinationId == destinationId }
    }

    override suspend fun getTripSummary(): TripSummary {
        guard()
        return tripSummary
    }

    override suspend fun getGames(status: GameStatus?, profileId: String?): List<Game> {
        guard()
        return games
    }

    override suspend fun createGame(request: CreateGameRequest): Game =
        throw UnsupportedOperationException("not stubbed")

    override suspend fun getGame(id: String): Game {
        guard()
        return games.first { it.id == id }
    }

    override suspend fun joinGame(id: String): Game =
        throw UnsupportedOperationException("not stubbed")

    override suspend fun submitMove(id: String, move: JsonElement): Game {
        guard()
        return moveHandler(id, move)
    }

    override suspend fun resign(id: String): Game =
        throw UnsupportedOperationException("not stubbed")

    override suspend fun getGameEvents(id: String, after: Long, waitSeconds: Int?): EventsPage {
        guard()
        val handler = gameEventsHandler
        if (handler != null) return handler(id, after, waitSeconds)
        val matching = feed.filter { it.seq > after }.sortedBy { it.seq }
        return EventsPage(matching, matching.lastOrNull()?.seq ?: after)
    }

    override suspend fun getNotifications(after: Long, waitSeconds: Int?): NotificationsPage {
        guard()
        return notificationsPage
    }
}
