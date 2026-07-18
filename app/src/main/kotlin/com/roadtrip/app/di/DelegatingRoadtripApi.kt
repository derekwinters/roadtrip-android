package com.roadtrip.app.di

import com.roadtrip.core.api.Checklist
import com.roadtrip.core.api.Config
import com.roadtrip.core.api.ConfigPatch
import com.roadtrip.core.api.CreateGameRequest
import com.roadtrip.core.api.Destination
import com.roadtrip.core.api.DestinationCreate
import com.roadtrip.core.api.DestinationPatch
import com.roadtrip.core.api.EventsPage
import com.roadtrip.core.api.Game
import com.roadtrip.core.api.GameStatus
import com.roadtrip.core.api.HealthResponse
import com.roadtrip.core.api.JournalEntry
import com.roadtrip.core.api.JournalPage
import com.roadtrip.core.api.Leg
import com.roadtrip.core.api.MapState
import com.roadtrip.core.api.NotificationsPage
import com.roadtrip.core.api.Profile
import com.roadtrip.core.api.ProfilePatch
import com.roadtrip.core.api.RoadtripApi
import com.roadtrip.core.api.SyncBatchRequest
import com.roadtrip.core.api.SyncBatchResult
import com.roadtrip.core.api.Trip
import com.roadtrip.core.api.TripSummary
import com.roadtrip.core.common.Role
import kotlinx.serialization.json.JsonElement

/**
 * Forwards every call to the current API instance so the server URL can change at runtime
 * (settings screen) without rebuilding the long-lived core components that hold an api ref.
 */
class DelegatingRoadtripApi(private val current: () -> RoadtripApi) : RoadtripApi {
    override suspend fun health(): HealthResponse = current().health()

    override suspend fun getProfiles(): List<Profile> = current().getProfiles()

    override suspend fun createProfile(name: String, avatar: String?, role: Role): Profile =
        current().createProfile(name, avatar, role)

    override suspend fun updateProfile(id: String, patch: ProfilePatch): Profile =
        current().updateProfile(id, patch)

    override suspend fun getConfig(): Config = current().getConfig()

    override suspend fun putConfig(patch: ConfigPatch): Config = current().putConfig(patch)

    override suspend fun getDestinations(): List<Destination> = current().getDestinations()

    override suspend fun createDestination(create: DestinationCreate): Destination =
        current().createDestination(create)

    override suspend fun updateDestination(id: String, patch: DestinationPatch): Destination =
        current().updateDestination(id, patch)

    override suspend fun deleteDestination(id: String) = current().deleteDestination(id)

    override suspend fun syncBatch(request: SyncBatchRequest, actorProfileId: String?): SyncBatchResult =
        current().syncBatch(request, actorProfileId)

    override suspend fun getEvents(
        after: Long,
        limit: Int?,
        types: List<String>?,
        waitSeconds: Int?,
    ): EventsPage = current().getEvents(after, limit, types, waitSeconds)

    override suspend fun getJournal(before: Long?, limit: Int?, trip: String?): JournalPage =
        current().getJournal(before, limit, trip)

    override suspend fun postJournal(text: String): JournalEntry = current().postJournal(text)

    override suspend fun getMap(maxPoints: Int?, trip: String?): MapState =
        current().getMap(maxPoints, trip)

    override suspend fun getChecklist(trip: String?): Checklist = current().getChecklist(trip)

    override suspend fun getLegs(trip: String?): List<Leg> = current().getLegs(trip)

    override suspend fun getLeg(destinationId: String): Leg = current().getLeg(destinationId)

    override suspend fun getTripSummary(): TripSummary = current().getTripSummary()

    override suspend fun getTrips(): List<Trip> = current().getTrips()

    override suspend fun createTrip(name: String?): Trip = current().createTrip(name)

    override suspend fun endTrip(id: String): Trip = current().endTrip(id)

    override suspend fun renameTrip(id: String, name: String): Trip = current().renameTrip(id, name)

    override suspend fun getTripSummary(tripId: String): TripSummary = current().getTripSummary(tripId)

    override suspend fun getGames(status: GameStatus?, profileId: String?): List<Game> =
        current().getGames(status, profileId)

    override suspend fun createGame(request: CreateGameRequest): Game = current().createGame(request)

    override suspend fun getGame(id: String): Game = current().getGame(id)

    override suspend fun joinGame(id: String): Game = current().joinGame(id)

    override suspend fun submitMove(id: String, move: JsonElement): Game =
        current().submitMove(id, move)

    override suspend fun resign(id: String): Game = current().resign(id)

    override suspend fun getGameEvents(id: String, after: Long, waitSeconds: Int?): EventsPage =
        current().getGameEvents(id, after, waitSeconds)

    override suspend fun getNotifications(after: Long, waitSeconds: Int?): NotificationsPage =
        current().getNotifications(after, waitSeconds)
}
