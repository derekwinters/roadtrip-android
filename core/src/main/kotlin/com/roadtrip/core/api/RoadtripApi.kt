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

    suspend fun getDestinations(): List<Destination>
    suspend fun createDestination(create: DestinationCreate): Destination
    suspend fun updateDestination(id: String, patch: DestinationPatch): Destination
    suspend fun deleteDestination(id: String)

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

    // ---- trips (backend 12-trips.md; ANDTRIP) ----------------------------------------
    suspend fun getTrips(): List<Trip>

    /** 409 `conflict` when a trip is already active (TRIP-001). */
    suspend fun createTrip(name: String? = null): Trip
    suspend fun endTrip(id: String): Trip
    suspend fun renameTrip(id: String, name: String): Trip

    /** Per-trip aggregate, GET /api/trips/{id}/summary (TRIP-008). */
    suspend fun getTripSummary(tripId: String): TripSummary

    suspend fun getGames(status: GameStatus? = null, profileId: String? = null): List<Game>
    suspend fun createGame(request: CreateGameRequest): Game
    suspend fun getGame(id: String): Game
    suspend fun joinGame(id: String): Game
    suspend fun submitMove(id: String, move: JsonElement): Game
    suspend fun resign(id: String): Game
    suspend fun getGameEvents(id: String, after: Long, waitSeconds: Int? = null): EventsPage

    suspend fun getNotifications(after: Long, waitSeconds: Int? = null): NotificationsPage
}
