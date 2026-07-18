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

    suspend fun syncBatch(request: SyncBatchRequest): SyncBatchResult
    suspend fun getEvents(
        after: Long,
        limit: Int? = null,
        types: List<String>? = null,
        waitSeconds: Int? = null,
    ): EventsPage

    suspend fun getJournal(before: Long? = null, limit: Int? = null): JournalPage
    suspend fun postJournal(text: String): JournalEntry

    suspend fun getMap(maxPoints: Int? = null): MapState
    suspend fun getChecklist(): Checklist
    suspend fun getLegs(): List<Leg>
    suspend fun getLeg(destinationId: String): Leg
    suspend fun getTripSummary(): TripSummary

    suspend fun getGames(status: GameStatus? = null, profileId: String? = null): List<Game>
    suspend fun createGame(request: CreateGameRequest): Game
    suspend fun getGame(id: String): Game
    suspend fun joinGame(id: String): Game
    suspend fun submitMove(id: String, move: JsonElement): Game
    suspend fun resign(id: String): Game
    suspend fun getGameEvents(id: String, after: Long, waitSeconds: Int? = null): EventsPage

    suspend fun getNotifications(after: Long, waitSeconds: Int? = null): NotificationsPage
}
