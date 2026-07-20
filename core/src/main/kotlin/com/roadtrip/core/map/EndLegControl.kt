package com.roadtrip.core.map

import com.roadtrip.core.api.Destination
import com.roadtrip.core.api.DestinationStatus
import com.roadtrip.core.common.Role
import com.roadtrip.core.sync.DestinationWriteRefresh
import com.roadtrip.core.sync.DestinationWriteTarget
import com.roadtrip.core.trips.TripAction

/**
 * The manual "End leg" safety net (ANDMAP-014, backend LOC-013): a parent can mark the
 * active destination arrived now — without advancing to the next stop — for when automatic
 * arrival detection never fires. It mirrors the End-trip affordance (ANDTRIP-001/004):
 * parent-only, online-only, and always confirmed via a dialog.
 *
 * The decision of whether the action is even available lives here as pure Kotlin so it is
 * testable on the JVM and the Compose control stays thin: End leg is offered only while a
 * destination is `active` (the map's active-destination marker / destination panel). Between
 * trips or while staging a planned trip there is no active destination, so it stays hidden.
 */
object EndLegControl {
    const val OFFLINE_REASON = "Ending a leg needs the trip server"

    /**
     * Whether — and how — the End-leg control renders, given the viewer's role, connectivity,
     * and the destination list in view. Hidden unless a parent is looking at a list that has an
     * active destination; visible-but-disabled with a reason while offline; enabled otherwise.
     */
    fun action(role: Role, online: Boolean, destinations: List<Destination>): TripAction {
        val hasActive = destinations.any { it.status == DestinationStatus.ACTIVE }
        return when {
            role != Role.PARENT || !hasActive -> TripAction(visible = false, enabled = false)
            !online -> TripAction(visible = true, enabled = false, disabledReason = OFFLINE_REASON)
            else -> TripAction(visible = true, enabled = true)
        }
    }

    /**
     * Read models to re-pull immediately after a successful end-leg — the same write-through a
     * live (in-trip) destination write already performs (ANDMAP-013): the arrived destination
     * and the server-recomputed active destination / remaining distance appear at once rather
     * than drifting until the next foreground refresh. All geodesy stays on the backend.
     */
    fun refreshTargets(): Set<DestinationWriteTarget> = DestinationWriteRefresh.targets(staged = false)
}
