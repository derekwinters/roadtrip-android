package com.roadtrip.core.testing

import com.roadtrip.core.api.ApiException
import com.roadtrip.core.api.Checklist
import com.roadtrip.core.api.ClientEvent
import com.roadtrip.core.api.Config
import com.roadtrip.core.api.ConfigPatch
import com.roadtrip.core.api.CreateGameRequest
import com.roadtrip.core.api.Destination
import com.roadtrip.core.api.DestinationCreate
import com.roadtrip.core.api.DestinationPatch
import com.roadtrip.core.api.DestinationStatus
import com.roadtrip.core.api.EventDto
import com.roadtrip.core.api.EventsPage
import com.roadtrip.core.api.Game
import com.roadtrip.core.api.GameStatus
import com.roadtrip.core.api.GeocodeMatch
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
import com.roadtrip.core.api.Trip
import com.roadtrip.core.api.TripStatus
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

    /** Per-batch actor override (X-Profile-Id) the client sent, parallel to [syncBatches]. */
    val syncBatchActors = mutableListOf<String?>()

    /** When > 0: the next batch is fully processed, then the response is "lost". */
    var loseResponses: Int = 0

    /** Per-event rejection rule; non-null return = rejected with that reason. */
    var rejectWhen: (ClientEvent) -> String? = { null }

    /** The /api/events feed. */
    val feed = mutableListOf<EventDto>()

    /** Trips read model; createTrip/endTrip mutate it with server-side arbitration. */
    val trips = mutableListOf<Trip>()
    private var nextTripNo = 1

    /** Per-trip scoped read models for the ?trip=<id> parameter (TRIP-007/008). */
    val journalByTrip = mutableMapOf<String, JournalPage>()
    val checklistByTrip = mutableMapOf<String, Checklist>()
    val legsByTrip = mutableMapOf<String, List<Leg>>()
    val summaryByTrip = mutableMapOf<String, TripSummary>()

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

    /** POST /api/profiles calls the client made, for bootstrap-flow assertions (AND-007). */
    data class CreateProfileRequest(val name: String, val avatar: String?, val role: Role)

    val createProfileRequests = mutableListOf<CreateProfileRequest>()

    override suspend fun createProfile(name: String, avatar: String?, role: Role): Profile {
        guard()
        createProfileRequests += CreateProfileRequest(name, avatar, role)
        val created = Profile("p-${profiles.size + 1}", name, avatar ?: "star", role)
        profiles = profiles + created
        return created
    }

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

    override suspend fun createDestination(create: DestinationCreate): Destination {
        guard()
        val destination = Destination(
            id = "dest-${destinations.size + 1}",
            name = create.name,
            lat = create.lat,
            lon = create.lon,
            orderIndex = create.orderIndex ?: destinations.size,
            status = if (destinations.none { it.status == DestinationStatus.ACTIVE }) {
                DestinationStatus.ACTIVE
            } else {
                DestinationStatus.PENDING
            },
        )
        destinations = destinations + destination
        return destination
    }

    override suspend fun updateDestination(id: String, patch: DestinationPatch): Destination {
        guard()
        val existing = destinations.first { it.id == id }
        val updated = existing.copy(
            name = patch.name ?: existing.name,
            lat = patch.lat ?: existing.lat,
            lon = patch.lon ?: existing.lon,
            orderIndex = patch.orderIndex ?: existing.orderIndex,
        )
        destinations = destinations.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun deleteDestination(id: String) {
        guard()
        destinations = destinations.filterNot { it.id == id }
    }

    // ---- geocode (backend GET /api/geocode proxy; ANDMAP-008/009) ----------------------

    /** Queries the server actually received, in order. */
    val geocodeQueries = mutableListOf<String>()

    /** Stubbed matches returned when no [geocodeHandler] is set. */
    var geocodeResults: List<GeocodeMatch> = emptyList()

    /** Failure/latency hook: set to throw (503 `geocode_unavailable`, ...) or to observe state mid-flight. */
    var geocodeHandler: (suspend (String) -> List<GeocodeMatch>)? = null

    override suspend fun geocode(q: String): List<GeocodeMatch> {
        guard()
        geocodeQueries += q
        val handler = geocodeHandler
        if (handler != null) return handler(q)
        return geocodeResults
    }

    override suspend fun syncBatch(request: SyncBatchRequest, actorProfileId: String?): SyncBatchResult {
        guard()
        syncBatches += request
        syncBatchActors += actorProfileId
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

    override suspend fun getJournal(before: Long?, limit: Int?, trip: String?): JournalPage {
        guard()
        if (trip != null) return journalByTrip[trip] ?: JournalPage(emptyList(), null)
        return journalPage
    }

    override suspend fun postJournal(text: String): JournalEntry {
        guard()
        return JournalEntry(seq = nextSeq++, kind = JournalKind.POST, ts = "2026-07-18T12:00:00Z", text = text)
    }

    override suspend fun getMap(maxPoints: Int?, trip: String?): MapState {
        guard()
        return mapState
    }

    override suspend fun getChecklist(trip: String?): Checklist {
        guard()
        if (trip != null) return checklistByTrip[trip] ?: Checklist()
        return checklist
    }

    override suspend fun getLegs(trip: String?): List<Leg> {
        guard()
        if (trip != null) return legsByTrip[trip] ?: emptyList()
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

    // ---- trips (server-side single-active-trip arbitration, TRIP-001/002) --------------

    override suspend fun getTrips(): List<Trip> {
        guard()
        return trips.toList()
    }

    override suspend fun createTrip(name: String?): Trip {
        guard()
        if (trips.any { it.status == TripStatus.ACTIVE }) {
            throw ApiException(409, "conflict", "a trip is already active")
        }
        val n = nextTripNo++
        val trip = Trip(
            id = "trip-$n",
            name = name ?: "Road Trip $n",
            status = TripStatus.ACTIVE,
            startedAt = TestData.ts(n * 1000L),
        )
        trips += trip
        return trip
    }

    override suspend fun endTrip(id: String): Trip {
        guard()
        val index = trips.indexOfFirst { it.id == id }
        val existing = trips.getOrNull(index) ?: throw ApiException(404, "not_found", "no such trip")
        if (existing.status != TripStatus.ACTIVE) {
            throw ApiException(409, "conflict", "trip is not active")
        }
        val ended = existing.copy(
            status = TripStatus.ENDED,
            endedAt = TestData.ts(nextTripNo++ * 1000L),
        )
        trips[index] = ended
        return ended
    }

    override suspend fun renameTrip(id: String, name: String): Trip {
        guard()
        val index = trips.indexOfFirst { it.id == id }
        val existing = trips.getOrNull(index) ?: throw ApiException(404, "not_found", "no such trip")
        val renamed = existing.copy(name = name)
        trips[index] = renamed
        return renamed
    }

    override suspend fun getTripSummary(tripId: String): TripSummary {
        guard()
        return summaryByTrip[tripId] ?: tripSummary
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
