package com.roadtrip.core.trips

import com.roadtrip.core.api.BingoCard
import com.roadtrip.core.api.Checklist
import com.roadtrip.core.api.JournalPage
import com.roadtrip.core.api.RoadtripApi
import com.roadtrip.core.api.Trip
import com.roadtrip.core.api.TripStatus
import com.roadtrip.core.api.TripSummary
import com.roadtrip.core.common.Role
import com.roadtrip.core.common.Timestamps
import com.roadtrip.core.storage.CacheStore
import com.roadtrip.core.storage.Cached
import java.time.Instant

/** Where the app is in the trip lifecycle (docs/spec/09-trips.md interface decisions). */
sealed class TripPhase {
    /** First-ever launch: no trips exist yet — welcome/empty state. */
    object FirstLaunch : TripPhase()

    /** First launch with only a planned "next trip": welcome plus the planned card (ANDTRIP-006). */
    data class Planned(val trip: Trip) : TripPhase()

    data class Active(val trip: Trip) : TripPhase()

    /** Between trips: the most recently ended trip is browsable read-only. */
    data class BetweenTrips(val lastTrip: Trip) : TripPhase()
}

/**
 * Lifecycle sort key: ended time, else started time; planned trips (no timestamps yet)
 * sort last. Shared by the reducer and the history list so planned trips never break
 * timestamp ordering (ANDTRIP-006).
 */
fun Trip.lifecycleInstant(): Instant =
    (endedAt ?: startedAt)?.let(Timestamps::parse) ?: Instant.EPOCH

/** A parent-only, online-only, dialog-confirmed trip action (ANDTRIP-001/004). */
data class TripAction(
    val visible: Boolean,
    val enabled: Boolean,
    val requiresConfirmation: Boolean = true,
    val disabledReason: String? = null,
)

data class TripHomeState(
    val phase: TripPhase,
    /** The trip whose data the app shows: active, else most recently ended, else null. */
    val viewedTrip: Trip?,
    /** Persistent banner text while no trip is active; null while one runs (ANDTRIP-002). */
    val bannerText: String?,
    val startAction: TripAction,
    val endAction: TripAction,
    /** The tracker never runs between trips (interface decision, 09-trips.md). */
    val trackerAllowed: Boolean,
    /**
     * The planned "next trip", surfaced wherever the no-active-trip banner shows — the
     * planned-trip card rides along between trips and on first launch (ANDTRIP-006).
     * Null while a trip is active (the card stays out of the way).
     */
    val plannedTrip: Trip? = null,
)

/**
 * Reduces the trips list + role + connectivity into the app's trip lifecycle state:
 * active trip, most-recently-ended fallback with the persistent banner, first-launch
 * welcome, and the parent-only online-only start/end affordances (ANDTRIP-001/002/004).
 */
object TripStateReducer {
    const val NO_ACTIVE_TRIP_BANNER = "No active road trip"
    const val OFFLINE_REASON = "Starting or ending a road trip needs the trip server"

    /** Active trip, else the most recently ended one (the server's default read scope). */
    fun viewedTrip(trips: List<Trip>): Trip? =
        trips.firstOrNull { it.status == TripStatus.ACTIVE } ?: mostRecentlyEnded(trips)

    fun reduce(trips: List<Trip>, role: Role, online: Boolean): TripHomeState {
        val active = trips.firstOrNull { it.status == TripStatus.ACTIVE }
        val lastEnded = mostRecentlyEnded(trips)
        val planned = trips.firstOrNull { it.status == TripStatus.PLANNED }
        val phase = when {
            active != null -> TripPhase.Active(active)
            lastEnded != null -> TripPhase.BetweenTrips(lastEnded)
            planned != null -> TripPhase.Planned(planned)
            else -> TripPhase.FirstLaunch
        }
        val parent = role == Role.PARENT

        // Start applies between trips, end while one runs; both are parent-only,
        // online-only, and always confirmed via a dialog (ANDTRIP-001/004).
        fun action(applies: Boolean): TripAction = when {
            !applies || !parent -> TripAction(visible = false, enabled = false)
            !online -> TripAction(visible = true, enabled = false, disabledReason = OFFLINE_REASON)
            else -> TripAction(visible = true, enabled = true)
        }

        return TripHomeState(
            phase = phase,
            viewedTrip = active ?: lastEnded,
            bannerText = if (active == null) NO_ACTIVE_TRIP_BANNER else null,
            startAction = action(applies = active == null),
            endAction = action(applies = active != null),
            // A planned trip is still "between trips" for the tracker (ANDTRIP-002).
            trackerAllowed = active != null,
            plannedTrip = if (active == null) planned else null,
        )
    }

    private fun mostRecentlyEnded(trips: List<Trip>): Trip? =
        trips.filter { it.status == TripStatus.ENDED }.maxByOrNull { it.lifecycleInstant() }
}

/** Trip-scoped cache keys, e.g. `journal_trip-1`, so histories never mix (ANDTRIP-002). */
object TripCacheKeys {
    fun scoped(base: String, tripId: String?): String =
        if (tripId.isNullOrEmpty()) base else "${base}_$tripId"
}

/**
 * A [CacheStore] view that resolves the trip-scoped key on every operation: ending or
 * switching trips transparently lands reads/writes on a different backing row, so the
 * previous trip's cached history stays intact and is never mixed into the next trip's
 * (ANDTRIP-002).
 */
class TripScopedCacheStore<T>(
    private val base: String,
    private val tripId: () -> String?,
    private val backing: (String) -> CacheStore<T>,
) : CacheStore<T> {
    override fun read(): Cached<T>? = current().read()

    override fun write(value: T, at: Instant) {
        current().write(value, at)
    }

    private fun current(): CacheStore<T> = backing(TripCacheKeys.scoped(base, tripId()))
}

/**
 * Read-only trip history browsing (ANDTRIP-003): lists trips (active first, then most
 * recently ended first) and opens each trip's journal/checklist/summary/bingo through
 * the `?trip=<id>` scoped read models — never through the live caches. Planned trips
 * have no history yet, so they never appear here (their home is the planner card,
 * ANDTRIP-006).
 */
class TripHistoryBrowser(private val api: RoadtripApi) {
    suspend fun listTrips(): List<Trip> =
        api.getTrips()
            .filter { it.status != TripStatus.PLANNED }
            .sortedWith(
                compareBy<Trip> { it.status != TripStatus.ACTIVE }
                    .thenByDescending { it.lifecycleInstant() },
            )

    suspend fun journal(tripId: String): JournalPage = api.getJournal(trip = tripId)

    suspend fun checklist(tripId: String): Checklist = api.getChecklist(trip = tripId)

    suspend fun summary(tripId: String): TripSummary = api.getTripSummary(tripId)

    /** A past trip's bingo card, rendered read-only (ANDBNG-004). */
    suspend fun bingo(tripId: String): BingoCard = api.getBingo(trip = tripId)
}
