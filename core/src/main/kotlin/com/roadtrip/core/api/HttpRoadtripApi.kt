package com.roadtrip.core.api

import com.roadtrip.core.common.Role
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * OkHttp implementation of [RoadtripApi]. Attaches `X-Profile-Id` from [profileIdProvider]
 * to every request (AND-002); non-2xx responses become [ApiException], transport failures
 * stay [IOException].
 */
class HttpRoadtripApi(
    baseUrl: String,
    private val profileIdProvider: () -> String?,
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = RoadtripJson,
) : RoadtripApi {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    // ---- system ----------------------------------------------------------------------

    override suspend fun health(): HealthResponse =
        get("api/health", HealthResponse.serializer())

    // ---- profiles --------------------------------------------------------------------

    override suspend fun getProfiles(): List<Profile> =
        get("api/profiles", ListSerializer(Profile.serializer()))

    override suspend fun createProfile(name: String, avatar: String?, role: Role): Profile =
        request(
            "POST", url("api/profiles"),
            body(CreateProfileBody.serializer(), CreateProfileBody(name, avatar, role)),
            Profile.serializer(),
        )

    override suspend fun updateProfile(id: String, patch: ProfilePatch): Profile =
        request("PATCH", url("api/profiles/$id"), body(ProfilePatch.serializer(), patch), Profile.serializer())

    // ---- config ----------------------------------------------------------------------

    override suspend fun getConfig(): Config = get("api/config", Config.serializer())

    override suspend fun putConfig(patch: ConfigPatch): Config =
        request("PUT", url("api/config"), body(ConfigPatch.serializer(), patch), Config.serializer())

    // ---- destinations ----------------------------------------------------------------
    // The optional ?trip=<plannedId> stages writes against the planned trip (ANDTRIP-007).

    override suspend fun getDestinations(trip: String?): List<Destination> {
        val url = url("api/destinations") { if (trip != null) addQueryParameter("trip", trip) }
        return request("GET", url, null, ListSerializer(Destination.serializer()))
    }

    override suspend fun createDestination(create: DestinationCreate, trip: String?): Destination =
        request(
            "POST", url("api/destinations") { if (trip != null) addQueryParameter("trip", trip) },
            body(DestinationCreate.serializer(), create),
            Destination.serializer(),
        )

    override suspend fun updateDestination(id: String, patch: DestinationPatch, trip: String?): Destination =
        request(
            "PATCH", url("api/destinations/$id") { if (trip != null) addQueryParameter("trip", trip) },
            body(DestinationPatch.serializer(), patch),
            Destination.serializer(),
        )

    override suspend fun deleteDestination(id: String, trip: String?) {
        requestNoContent("DELETE", url("api/destinations/$id") { if (trip != null) addQueryParameter("trip", trip) })
    }

    // ---- geocode -----------------------------------------------------------------------

    override suspend fun geocode(q: String): List<GeocodeMatch> {
        val url = url("api/geocode") { addQueryParameter("q", q) }
        // The backend answers with a bare top-level JSON array (GSR-002 / ANDMAP-008), not an
        // object envelope — decode it as a list. An empty body [] yields an empty list.
        return request("GET", url, null, ListSerializer(GeocodeMatch.serializer()))
    }

    // ---- sync ------------------------------------------------------------------------

    override suspend fun syncBatch(request: SyncBatchRequest, actorProfileId: String?): SyncBatchResult =
        request(
            "POST", url("api/sync/batch"),
            body(SyncBatchRequest.serializer(), request),
            SyncBatchResult.serializer(),
            // Per-batch attribution override: ping batches upload under the enabling
            // parent's profile id (ANDLOC-008).
            profileOverride = actorProfileId,
        )

    override suspend fun getEvents(
        after: Long,
        limit: Int?,
        types: List<String>?,
        waitSeconds: Int?,
    ): EventsPage {
        val url = url("api/events") {
            addQueryParameter("after", after.toString())
            if (limit != null) addQueryParameter("limit", limit.toString())
            if (types != null) addQueryParameter("types", types.joinToString(","))
            if (waitSeconds != null) addQueryParameter("wait", waitSeconds.toString())
        }
        return request("GET", url, null, EventsPage.serializer(), longPollSeconds = waitSeconds)
    }

    // ---- journal ---------------------------------------------------------------------

    override suspend fun getJournal(before: Long?, limit: Int?, trip: String?): JournalPage {
        val url = url("api/journal") {
            if (before != null) addQueryParameter("before", before.toString())
            if (limit != null) addQueryParameter("limit", limit.toString())
            if (trip != null) addQueryParameter("trip", trip)
        }
        return request("GET", url, null, JournalPage.serializer())
    }

    override suspend fun postJournal(text: String): JournalEntry =
        request(
            "POST", url("api/journal"),
            body(JournalPostRequest.serializer(), JournalPostRequest(text)),
            JournalEntry.serializer(),
        )

    // ---- location read models ----------------------------------------------------------

    override suspend fun getMap(maxPoints: Int?, trip: String?): MapState {
        val url = url("api/map") {
            if (maxPoints != null) addQueryParameter("max_points", maxPoints.toString())
            if (trip != null) addQueryParameter("trip", trip)
        }
        return request("GET", url, null, MapState.serializer())
    }

    override suspend fun getChecklist(trip: String?): Checklist {
        val url = url("api/checklist") {
            if (trip != null) addQueryParameter("trip", trip)
        }
        return request("GET", url, null, Checklist.serializer())
    }

    override suspend fun getLegs(trip: String?): List<Leg> {
        val url = url("api/legs") {
            if (trip != null) addQueryParameter("trip", trip)
        }
        return request("GET", url, null, ListSerializer(Leg.serializer()))
    }

    override suspend fun getLeg(destinationId: String): Leg =
        get("api/legs/$destinationId", Leg.serializer())

    override suspend fun getTripSummary(): TripSummary =
        get("api/trip/summary", TripSummary.serializer())

    // ---- trips ---------------------------------------------------------------------------

    override suspend fun getTrips(): List<Trip> =
        get("api/trips", ListSerializer(Trip.serializer()))

    override suspend fun createTrip(name: String?): Trip =
        request(
            "POST", url("api/trips"),
            body(TripCreateRequest.serializer(), TripCreateRequest(name)),
            Trip.serializer(),
        )

    override suspend fun endTrip(id: String): Trip =
        request("POST", url("api/trips/$id/end"), emptyBody(), Trip.serializer())

    override suspend fun createPlannedTrip(name: String?, plannedStartAt: String?): Trip =
        request(
            "POST", url("api/trips"),
            body(
                TripCreateRequest.serializer(),
                TripCreateRequest(name = name, status = "planned", plannedStartAt = plannedStartAt),
            ),
            Trip.serializer(),
        )

    override suspend fun startTrip(id: String): Trip =
        request("POST", url("api/trips/$id/start"), emptyBody(), Trip.serializer())

    override suspend fun patchTrip(id: String, name: String?, plannedStartAt: String?): Trip =
        request(
            "PATCH", url("api/trips/$id"),
            body(TripPatchRequest.serializer(), TripPatchRequest(name = name, plannedStartAt = plannedStartAt)),
            Trip.serializer(),
        )

    override suspend fun deleteTrip(id: String) {
        requestNoContent("DELETE", url("api/trips/$id"))
    }

    override suspend fun getTripSummary(tripId: String): TripSummary =
        get("api/trips/$tripId/summary", TripSummary.serializer())

    // ---- bingo ---------------------------------------------------------------------------

    override suspend fun getBingo(trip: String?): BingoCard {
        val url = url("api/bingo") { if (trip != null) addQueryParameter("trip", trip) }
        return request("GET", url, null, BingoCard.serializer())
    }

    // ---- games -------------------------------------------------------------------------

    override suspend fun getGames(status: GameStatus?, profileId: String?): List<Game> {
        val url = url("api/games") {
            if (status != null) addQueryParameter("status", json.encodeToString(GameStatus.serializer(), status).trim('"'))
            if (profileId != null) addQueryParameter("profile", profileId)
        }
        return request("GET", url, null, ListSerializer(Game.serializer()))
    }

    override suspend fun createGame(request: CreateGameRequest): Game =
        request("POST", url("api/games"), body(CreateGameRequest.serializer(), request), Game.serializer())

    override suspend fun getGame(id: String): Game = get("api/games/$id", Game.serializer())

    override suspend fun joinGame(id: String): Game =
        request("POST", url("api/games/$id/join"), emptyBody(), Game.serializer())

    override suspend fun submitMove(id: String, move: JsonElement): Game =
        request(
            "POST", url("api/games/$id/moves"),
            body(MoveRequest.serializer(), MoveRequest(move)),
            Game.serializer(),
        )

    override suspend fun resign(id: String): Game =
        request("POST", url("api/games/$id/resign"), emptyBody(), Game.serializer())

    override suspend fun getGameEvents(id: String, after: Long, waitSeconds: Int?): EventsPage {
        val url = url("api/games/$id/events") {
            addQueryParameter("after", after.toString())
            if (waitSeconds != null) addQueryParameter("wait", waitSeconds.toString())
        }
        return request("GET", url, null, EventsPage.serializer(), longPollSeconds = waitSeconds)
    }

    // ---- notifications -------------------------------------------------------------------

    override suspend fun getNotifications(after: Long, waitSeconds: Int?): NotificationsPage {
        val url = url("api/notifications") {
            addQueryParameter("after", after.toString())
            if (waitSeconds != null) addQueryParameter("wait", waitSeconds.toString())
        }
        return request("GET", url, null, NotificationsPage.serializer(), longPollSeconds = waitSeconds)
    }

    // ---- plumbing --------------------------------------------------------------------

    @kotlinx.serialization.Serializable
    private data class CreateProfileBody(val name: String, val avatar: String? = null, val role: Role)

    private fun url(path: String, build: (HttpUrl.Builder.() -> Unit)? = null): HttpUrl {
        val builder = base.newBuilder().addPathSegments(path)
        build?.invoke(builder)
        return builder.build()
    }

    private fun <T> body(serializer: KSerializer<T>, value: T): RequestBody =
        json.encodeToString(serializer, value).toRequestBody(JSON_MEDIA_TYPE)

    private fun emptyBody(): RequestBody = "{}".toRequestBody(JSON_MEDIA_TYPE)

    private suspend fun <R> get(path: String, responseSerializer: KSerializer<R>): R =
        request("GET", url(path), null, responseSerializer)

    /** For endpoints answering 204 with an empty body. */
    private suspend fun requestNoContent(method: String, url: HttpUrl) {
        val builder = Request.Builder().url(url).method(method, null)
        profileIdProvider()?.let { builder.header(PROFILE_HEADER, it) }
        client.newCall(builder.build()).await().use { response ->
            if (!response.isSuccessful) throw response.toApiException(response.body?.string().orEmpty())
        }
    }

    private suspend fun <R> request(
        method: String,
        url: HttpUrl,
        requestBody: RequestBody?,
        responseSerializer: KSerializer<R>,
        longPollSeconds: Int? = null,
        profileOverride: String? = null,
    ): R {
        val builder = Request.Builder().url(url).method(method, requestBody)
        (profileOverride ?: profileIdProvider())?.let { builder.header(PROFILE_HEADER, it) }

        // Long-poll requests need a read timeout comfortably above the server hold time.
        val caller = if (longPollSeconds != null) {
            client.newBuilder().readTimeout(longPollSeconds + 10L, TimeUnit.SECONDS).build()
        } else {
            client
        }

        val bodyText = caller.newCall(builder.build()).await().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw response.toApiException(text)
            text
        }
        return json.decodeFromString(responseSerializer, bodyText)
    }

    private fun Response.toApiException(bodyText: String): ApiException {
        val parsed = try {
            RoadtripJson.decodeFromString(ApiErrorBody.serializer(), bodyText)
        } catch (e: Exception) {
            null
        }
        return ApiException(
            status = code,
            code = parsed?.error?.code,
            message = parsed?.error?.message ?: "HTTP $code",
        )
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        })
        continuation.invokeOnCancellation { cancel() }
    }

    companion object {
        const val PROFILE_HEADER = "X-Profile-Id"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
