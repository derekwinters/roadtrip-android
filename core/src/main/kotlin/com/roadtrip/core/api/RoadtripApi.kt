package com.roadtrip.core.api

import com.roadtrip.core.common.Role
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Non-2xx API responses. Network-level failures (offline, timeouts) surface as
 * [java.io.IOException] instead, so callers can distinguish "server said no"
 * from "server unreachable" (ANDSYNC-002 vs ANDSYNC-004).
 */
class ApiException(
    val status: Int,
    val code: String?,
    message: String,
) : Exception(message)

/**
 * Client for roadtrip-backend/docs/spec/openapi.yaml. Every call carries the selected
 * profile as `X-Profile-Id` (AND-002).
 */
interface RoadtripApi {
    suspend fun health(): HealthResponse

    suspend fun getProfiles(): List<Profile>
    suspend fun createProfile(name: String, avatar: String?, role: Role): Profile
    suspend fun updateProfile(id: String, patch: ProfilePatch): Profile

    suspend fun getConfig(): Config
    suspend fun putConfig(patch: ConfigPatch): Config

    // Destination reads/writes accept an optional trip scope (?trip=<id>) so parents can
    // stage the planned trip's itinerary ahead of time (planner contract, ANDTRIP-007);
    // null keeps the server default (the active trip's list).
    suspend fun getDestinations(trip: String? = null): List<Destination>
    suspend fun createDestination(create: DestinationCreate, trip: String? = null): Destination
    suspend fun updateDestination(id: String, patch: DestinationPatch, trip: String? = null): Destination
    suspend fun deleteDestination(id: String, trip: String? = null)

    /**
     * Address search through the backend's geocode proxy (parent-only): up to 5 matches
     * for the add-destination flow (ANDMAP-008). Answers 503 `geocode_unavailable` when
     * the server can't reach the upstream geocoder — callers map that (and transport
     * failures) to a needs-internet state (ANDMAP-009).
     */
    suspend fun geocode(q: String): List<GeocodeMatch>

    /**
     * Uploads a batch of locally-created events. [actorProfileId] overrides the signed-in
     * profile's `X-Profile-Id` for this batch only — used to attribute `location.ping`
     * batches to the enabling parent (ANDLOC-008).
     */
    suspend fun syncBatch(request: SyncBatchRequest, actorProfileId: String? = null): SyncBatchResult
    suspend fun getEvents(
        after: Long,
        limit: Int? = null,
        types: List<String>? = null,
        waitSeconds: Int? = null,
    ): EventsPage

    // The read models accept an optional trip scope (?trip=<id>, backend TRIP-007);
    // null keeps the server default (active trip, else most recently ended).
    suspend fun getJournal(before: Long? = null, limit: Int? = null, trip: String? = null): JournalPage
    suspend fun postJournal(text: String): JournalEntry

    suspend fun getMap(maxPoints: Int? = null, trip: String? = null): MapState
    suspend fun getChecklist(trip: String? = null): Checklist
    suspend fun getLegs(trip: String? = null): List<Leg>
    suspend fun getLeg(destinationId: String): Leg
    suspend fun getTripSummary(): TripSummary

    /**
     * POST /api/trip/leg/end — manually end the current leg (parent-only, online): marks the
     * active destination `arrived` at the request time and records its leg, WITHOUT advancing
     * to the next destination (backend LOC-013 / ANDMAP-014). Answers 409 `conflict` when no
     * destination is active. Returns the arrived destination.
     */
    suspend fun endLeg(): Destination

    // ---- trips (backend 12-trips.md + planner contract; ANDTRIP) ------------------------
    suspend fun getTrips(): List<Trip>

    /** 409 `conflict` when a trip is already active (TRIP-001). */
    suspend fun createTrip(name: String? = null): Trip
    suspend fun endTrip(id: String): Trip

    /** POST /api/trips {status:"planned"} — 409 `conflict` when one is already planned (ANDTRIP-006). */
    suspend fun createPlannedTrip(name: String? = null, plannedStartAt: String? = null): Trip

    /** POST /api/trips/{id}/start — activates the planned trip; 409 while another is active (ANDTRIP-008). */
    suspend fun startTrip(id: String): Trip

    /** PATCH /api/trips/{id} {name?, planned_start_at?} (parent-only, ANDTRIP-006). */
    suspend fun patchTrip(id: String, name: String? = null, plannedStartAt: String? = null): Trip

    /** DELETE /api/trips/{id} — planned trips only (ANDTRIP-006). */
    suspend fun deleteTrip(id: String)

    suspend fun renameTrip(id: String, name: String): Trip = patchTrip(id, name = name)

    /** Per-trip aggregate, GET /api/trips/{id}/summary (TRIP-008). */
    suspend fun getTripSummary(tripId: String): TripSummary

    /** The license-plate bingo card (GET /api/bingo?trip=, docs/spec/10-bingo.md). */
    suspend fun getBingo(trip: String? = null): BingoCard

    suspend fun getGames(status: GameStatus? = null, profileId: String? = null): List<Game>
    suspend fun createGame(request: CreateGameRequest): Game
    suspend fun getGame(id: String): Game
    suspend fun joinGame(id: String): Game
    suspend fun submitMove(id: String, move: JsonElement): Game
    suspend fun resign(id: String): Game
    suspend fun getGameEvents(id: String, after: Long, waitSeconds: Int? = null): EventsPage

    suspend fun getNotifications(after: Long, waitSeconds: Int? = null): NotificationsPage
}
