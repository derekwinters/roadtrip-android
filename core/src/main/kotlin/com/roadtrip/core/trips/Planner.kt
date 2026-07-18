package com.roadtrip.core.trips

import com.roadtrip.core.api.Destination
import com.roadtrip.core.api.Trip
import com.roadtrip.core.api.TripStatus
import com.roadtrip.core.common.Role

/**
 * The planned-trip card (ANDTRIP-006/007): name, approximate start (verbatim free text),
 * staged itinerary preview, and the parent-only, online-only planner actions. Kids get
 * the same card with every action hidden — read-only by construction.
 */
data class PlannedTripCard(
    val trip: Trip,
    /** The approximate start exactly as entered ("~ early August"); never parsed. */
    val plannedStartText: String?,
    /** Staged destinations in itinerary order (ANDTRIP-007). */
    val itinerary: List<Destination>,
    /** "Road trip starts now" — activates the plan via /start (ANDTRIP-008). */
    val startAction: TripAction,
    val renameAction: TripAction,
    val deleteAction: TripAction,
    /** Map-screen staging is parent-only and online-only, like destination admin (ANDTRIP-007). */
    val canStageDestinations: Boolean,
)

data class PlannerState(
    /** Null when nothing is planned or a trip is running (the card stays out of the way). */
    val card: PlannedTripCard?,
    /** "Plan the next trip" — parents only, only while nothing is planned or active. */
    val planAction: TripAction,
)

/**
 * Reduces the trips list + staged destinations + role + connectivity into the planner
 * card state (ANDTRIP-006/007). The server arbitrates the single planned trip; this
 * only decides what to show and which actions apply.
 */
object TripPlannerReducer {
    const val OFFLINE_REASON = "Planning the next trip needs the trip server"

    fun plannedTrip(trips: List<Trip>): Trip? = trips.firstOrNull { it.status == TripStatus.PLANNED }

    fun reduce(
        trips: List<Trip>,
        stagedDestinations: List<Destination>,
        role: Role,
        online: Boolean,
    ): PlannerState {
        val planned = plannedTrip(trips)
        val activeRunning = trips.any { it.status == TripStatus.ACTIVE }
        val parent = role == Role.PARENT

        fun plannerAction(applies: Boolean, reason: String = OFFLINE_REASON): TripAction = when {
            !applies || !parent -> TripAction(visible = false, enabled = false)
            !online -> TripAction(visible = true, enabled = false, disabledReason = reason)
            else -> TripAction(visible = true, enabled = true)
        }

        // The card lives on the no-active-trip screens only (ANDTRIP-006).
        val card = planned?.takeIf { !activeRunning }?.let { trip ->
            PlannedTripCard(
                trip = trip,
                plannedStartText = trip.plannedStartAt,
                itinerary = stagedDestinations.sortedBy { it.orderIndex },
                // Activating is a trip start: same offline explanation as ANDTRIP-004.
                startAction = plannerAction(applies = true, reason = TripStateReducer.OFFLINE_REASON),
                renameAction = plannerAction(applies = true),
                deleteAction = plannerAction(applies = true),
                canStageDestinations = parent && online,
            )
        }

        return PlannerState(
            card = card,
            planAction = plannerAction(applies = planned == null && !activeRunning),
        )
    }
}
