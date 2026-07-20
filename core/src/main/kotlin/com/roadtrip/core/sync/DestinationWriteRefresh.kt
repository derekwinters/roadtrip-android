package com.roadtrip.core.sync

/** A read model re-pulled immediately after a parent destination write (ANDMAP-013). */
enum class DestinationWriteTarget {
    /** The planned trip's staged itinerary (`?trip=<plannedId>`, ANDTRIP-007). */
    STAGED_ITINERARY,

    /** The live (active-trip / between-trips) destination list. */
    LIVE_DESTINATIONS,

    /** The live map read model — active-destination + remaining distance recomputed server-side. */
    MAP,
}

/**
 * Pure decision for which read models a parent destination write must re-pull right away
 * (ANDMAP-013).
 *
 * A staged write (against a planned trip) refreshes only that trip's staged itinerary. A live
 * in-trip write refreshes the live destination list AND the map, so the new/removed/reordered
 * stop and the server-recomputed active destination / remaining distance appear at once instead
 * of drifting until the next foreground refresh (ANDSYNC-008) — the client-side cause of issue
 * #134, where local pings kept moving the "current" dot while the stale active-destination
 * marker stayed frozen and a mid-trip-added destination never appeared.
 */
object DestinationWriteRefresh {
    fun targets(staged: Boolean): Set<DestinationWriteTarget> =
        if (staged) {
            setOf(DestinationWriteTarget.STAGED_ITINERARY)
        } else {
            setOf(DestinationWriteTarget.LIVE_DESTINATIONS, DestinationWriteTarget.MAP)
        }
}
