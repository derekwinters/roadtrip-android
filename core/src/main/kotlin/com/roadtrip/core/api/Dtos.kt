package com.roadtrip.core.api

import com.roadtrip.core.common.Role
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * DTOs mirroring roadtrip-backend/docs/spec/openapi.yaml exactly (snake_case wire names).
 * Unknown fields are ignored so the client tolerates additive server changes.
 */
val RoadtripJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}

@Serializable
data class HealthResponse(
    val status: String,
    val version: String? = null,
)

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val avatar: String,
    val role: Role,
)

@Serializable
data class ProfilePatch(
    val name: String? = null,
    val avatar: String? = null,
    val role: Role? = null,
)

@Serializable
data class Config(
    @SerialName("ping_interval_s") val pingIntervalS: Int,
    @SerialName("stop_radius_m") val stopRadiusM: Double,
    @SerialName("min_stop_duration_min") val minStopDurationMin: Double,
    @SerialName("arrival_radius_m") val arrivalRadiusM: Double,
    @SerialName("city_radius_km") val cityRadiusKm: Double,
    // Default matches CFG-006 so responses from servers predating the key still parse.
    @SerialName("open_profile_creation") val openProfileCreation: Boolean = true,
)

@Serializable
data class ConfigPatch(
    @SerialName("ping_interval_s") val pingIntervalS: Int? = null,
    @SerialName("stop_radius_m") val stopRadiusM: Double? = null,
    @SerialName("min_stop_duration_min") val minStopDurationMin: Double? = null,
    @SerialName("arrival_radius_m") val arrivalRadiusM: Double? = null,
    @SerialName("city_radius_km") val cityRadiusKm: Double? = null,
    @SerialName("open_profile_creation") val openProfileCreation: Boolean? = null,
)

@Serializable
enum class DestinationStatus {
    @SerialName("pending") PENDING,
    @SerialName("active") ACTIVE,
    @SerialName("arrived") ARRIVED,
}

@Serializable
data class Destination(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    @SerialName("order_index") val orderIndex: Int,
    val status: DestinationStatus,
    @SerialName("arrived_at") val arrivedAt: String? = null,
)

/** Body for POST /api/destinations (parent-only). */
@Serializable
data class DestinationCreate(
    val name: String,
    val lat: Double,
    val lon: Double,
    @SerialName("order_index") val orderIndex: Int? = null,
)

/** Body for PATCH /api/destinations/{id} (parent-only). */
@Serializable
data class DestinationPatch(
    val name: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("order_index") val orderIndex: Int? = null,
)

/** One match from the backend's GET /api/geocode proxy (ANDMAP-008). */
@Serializable
data class GeocodeMatch(
    @SerialName("display_name") val displayName: String,
    val lat: Double,
    val lon: Double,
)

/** Response envelope of GET /api/geocode: at most 5 results. */
@Serializable
data class GeocodeResponse(
    val results: List<GeocodeMatch> = emptyList(),
)

@Serializable
data class ClientEvent(
    @SerialName("event_id") val eventId: String,
    val type: String,
    @SerialName("client_ts") val clientTs: String,
    val payload: JsonObject,
)

@Serializable
data class SyncBatchRequest(
    @SerialName("device_id") val deviceId: String? = null,
    val events: List<ClientEvent>,
)

@Serializable
enum class SyncStatus {
    @SerialName("accepted") ACCEPTED,
    @SerialName("duplicate") DUPLICATE,
    @SerialName("rejected") REJECTED,
}

@Serializable
data class SyncEventResult(
    @SerialName("event_id") val eventId: String,
    val status: SyncStatus,
    val reason: String? = null,
    val seq: Long? = null,
)

@Serializable
data class SyncBatchResult(
    val results: List<SyncEventResult>,
)

@Serializable
data class EventDto(
    val seq: Long,
    @SerialName("event_id") val eventId: String,
    val type: String,
    @SerialName("actor_id") val actorId: String? = null,
    val payload: JsonObject,
    @SerialName("client_ts") val clientTs: String,
    @SerialName("server_ts") val serverTs: String,
)

@Serializable
data class EventsPage(
    val events: List<EventDto> = emptyList(),
    @SerialName("next_after") val nextAfter: Long,
)

@Serializable
enum class DeepLinkKind {
    @SerialName("game_replay") GAME_REPLAY,
    @SerialName("map_pin") MAP_PIN,
    @SerialName("checklist") CHECKLIST,
    @SerialName("leg_summary") LEG_SUMMARY,
    @SerialName("trip_summary") TRIP_SUMMARY,
}

@Serializable
data class DeepLink(
    val kind: DeepLinkKind,
    @SerialName("game_id") val gameId: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("state_code") val stateCode: String? = null,
    @SerialName("destination_id") val destinationId: String? = null,
    @SerialName("trip_id") val tripId: String? = null,
)

@Serializable
enum class JournalKind {
    @SerialName("post") POST,
    @SerialName("stop") STOP,
    @SerialName("state_crossing") STATE_CROSSING,
    @SerialName("leg_arrival") LEG_ARRIVAL,
    @SerialName("game_result") GAME_RESULT,
    @SerialName("trip_started") TRIP_STARTED,
    @SerialName("trip_ended") TRIP_ENDED,
}

@Serializable
data class JournalEntry(
    val seq: Long,
    val kind: JournalKind,
    val ts: String,
    val actor: Profile? = null,
    val text: String,
    val link: DeepLink? = null,
    val syncing: Boolean = false,
)

@Serializable
data class JournalPage(
    val entries: List<JournalEntry> = emptyList(),
    @SerialName("next_before") val nextBefore: Long? = null,
)

@Serializable
data class JournalPostRequest(
    val text: String,
)

@Serializable
data class GeoPoint(
    val lat: Double,
    val lon: Double,
)

@Serializable
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val ts: String? = null,
)

@Serializable
data class MapState(
    val current: TrackPoint? = null,
    val start: GeoPoint? = null,
    @SerialName("active_destination") val activeDestination: Destination? = null,
    @SerialName("remaining_mi") val remainingMi: Double? = null,
    @SerialName("leg_miles") val legMiles: Double = 0.0,
    val breadcrumb: List<TrackPoint> = emptyList(),
)

@Serializable
data class ChecklistStateEntry(
    val state: String,
    @SerialName("state_code") val stateCode: String,
    @SerialName("first_entered_at") val firstEnteredAt: String? = null,
)

@Serializable
data class ChecklistCityEntry(
    val city: String,
    @SerialName("state_code") val stateCode: String,
    @SerialName("first_at") val firstAt: String? = null,
)

@Serializable
data class ChecklistStopEntry(
    val lat: Double,
    val lon: Double,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("duration_min") val durationMin: Double? = null,
    val place: String? = null,
)

@Serializable
data class Checklist(
    val states: List<ChecklistStateEntry> = emptyList(),
    val cities: List<ChecklistCityEntry> = emptyList(),
    val stops: List<ChecklistStopEntry> = emptyList(),
)

@Serializable
data class LegSummary(
    @SerialName("wall_minutes") val wallMinutes: Double,
    @SerialName("moving_minutes") val movingMinutes: Double,
    val miles: Double,
    @SerialName("stop_count") val stopCount: Int,
    val states: List<String> = emptyList(),
    @SerialName("games_played") val gamesPlayed: Int,
)

@Serializable
data class Leg(
    @SerialName("leg_index") val legIndex: Int,
    @SerialName("destination_id") val destinationId: String? = null,
    @SerialName("destination_name") val destinationName: String? = null,
    @SerialName("started_at") val startedAt: String,
    @SerialName("arrived_at") val arrivedAt: String? = null,
    val summary: LegSummary? = null,
)

@Serializable
enum class TripStatus {
    @SerialName("planned") PLANNED,
    @SerialName("active") ACTIVE,
    @SerialName("ended") ENDED,
}

/**
 * A named road trip (backend 12-trips.md); at most one is active — and at most one is
 * planned (planner contract) — at a time. Planned trips carry an approximate free-text
 * `planned_start_at` and no `started_at` yet (ANDTRIP-006).
 */
@Serializable
data class Trip(
    val id: String,
    val name: String,
    val status: TripStatus,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("planned_start_at") val plannedStartAt: String? = null,
)

/**
 * Body for POST /api/trips (parent-only; server default name when omitted).
 * `status: "planned"` creates the single planned "next trip" instead of starting one
 * (409 `conflict` when a planned trip already exists, ANDTRIP-006).
 */
@Serializable
data class TripCreateRequest(
    val name: String? = null,
    val status: String? = null,
    @SerialName("planned_start_at") val plannedStartAt: String? = null,
)

/** Body for PATCH /api/trips/{id} (parent-only rename / planned re-date). */
@Serializable
data class TripPatchRequest(
    val name: String? = null,
    @SerialName("planned_start_at") val plannedStartAt: String? = null,
)

@Serializable
data class TripSummary(
    val miles: Double,
    @SerialName("wall_minutes") val wallMinutes: Double,
    @SerialName("moving_minutes") val movingMinutes: Double,
    @SerialName("states_count") val statesCount: Int,
    @SerialName("stop_count") val stopCount: Int,
    @SerialName("games_played") val gamesPlayed: Int,
    @SerialName("wins_by_profile") val winsByProfile: Map<String, Int> = emptyMap(),
    @SerialName("journal_posts_by_profile") val journalPostsByProfile: Map<String, Int> = emptyMap(),
)

// ---- license plate bingo (GET /api/bingo, docs/spec/10-bingo.md) -----------------------

/** One spotted state on the shared card (ANDBNG-001). */
@Serializable
data class BingoCell(
    @SerialName("state_code") val stateCode: String,
    @SerialName("spotted_by") val spottedBy: String,
    @SerialName("spotted_at") val spottedAt: String,
)

@Serializable
enum class BingoLogAction {
    @SerialName("spotted") SPOTTED,
    @SerialName("removed") REMOVED,
}

/** One line of the card's chronological history (ANDBNG-003). */
@Serializable
data class BingoLogEntry(
    @SerialName("state_code") val stateCode: String,
    val action: BingoLogAction,
    @SerialName("profile_id") val profileId: String,
    val ts: String,
)

/** Response of GET /api/bingo?trip= — cells, history log, per-profile standing counts. */
@Serializable
data class BingoCard(
    val cells: List<BingoCell> = emptyList(),
    val log: List<BingoLogEntry> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
)

@Serializable
enum class GameType {
    @SerialName("chess") CHESS,
    @SerialName("checkers") CHECKERS,
    @SerialName("tictactoe") TICTACTOE,
    @SerialName("ultimate") ULTIMATE,
    @SerialName("hangman") HANGMAN,
}

@Serializable
enum class GameMode {
    @SerialName("open") OPEN,
    @SerialName("challenge") CHALLENGE,
}

@Serializable
enum class GameStatus {
    @SerialName("open") OPEN,
    @SerialName("active") ACTIVE,
    @SerialName("finished") FINISHED,
    @SerialName("abandoned") ABANDONED,
}

@Serializable
enum class GameResultKind {
    @SerialName("win") WIN,
    @SerialName("draw") DRAW,
    @SerialName("abandoned") ABANDONED,
}

@Serializable
data class Game(
    val id: String,
    @SerialName("game_type") val gameType: GameType,
    val mode: GameMode,
    val status: GameStatus,
    @SerialName("created_by") val createdBy: String,
    @SerialName("invited_profile_id") val invitedProfileId: String? = null,
    @SerialName("opponent_id") val opponentId: String? = null,
    @SerialName("move_count") val moveCount: Int = 0,
    val result: GameResultKind? = null,
    @SerialName("winner_id") val winnerId: String? = null,
    val turn: String? = null,
    val view: JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null,
)

@Serializable
data class CreateGameRequest(
    @SerialName("game_type") val gameType: GameType,
    val mode: GameMode,
    @SerialName("invited_profile_id") val invitedProfileId: String? = null,
    val options: JsonObject? = null,
)

@Serializable
data class MoveRequest(
    val move: JsonElement,
)

@Serializable
enum class NotificationKind {
    @SerialName("challenge_received") CHALLENGE_RECEIVED,
    @SerialName("journal_activity") JOURNAL_ACTIVITY,
}

/**
 * Backend notification feed item (NOTIF-001..005). `actor_id` is not part of the required
 * contract but is tolerated when present so the client can guard against self-notifications
 * even if the backend misbehaves (ANDNOTIF-002).
 */
@Serializable
data class NotificationItem(
    val seq: Long,
    val kind: NotificationKind,
    val text: String,
    @SerialName("game_id") val gameId: String? = null,
    val link: DeepLink? = null,
    @SerialName("actor_id") val actorId: String? = null,
)

@Serializable
data class NotificationsPage(
    val items: List<NotificationItem> = emptyList(),
    @SerialName("next_after") val nextAfter: Long,
)

@Serializable
data class ApiErrorDetail(val code: String, val message: String)

@Serializable
data class ApiErrorBody(val error: ApiErrorDetail)
