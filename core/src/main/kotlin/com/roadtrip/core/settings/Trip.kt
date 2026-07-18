package com.roadtrip.core.settings

import com.roadtrip.core.api.Leg
import com.roadtrip.core.api.Profile
import com.roadtrip.core.api.TripSummary

data class LegRow(
    val legIndex: Int,
    val destinationName: String?,
    val arrived: Boolean,
    val miles: Double?,
    val wallHours: Double?,
    val movingHours: Double?,
    val stopCount: Int?,
    val states: List<String>,
    val gamesPlayed: Int?,
)

data class TripSummaryRow(
    val miles: Double,
    val wallHours: Double,
    val movingHours: Double,
    val statesCount: Int,
    val stopCount: Int,
    val gamesPlayed: Int,
    val winsByName: Map<String, Int>,
    val journalPostsByName: Map<String, Int>,
)

data class TripScreenState(
    val legs: List<LegRow>,
    val summary: TripSummaryRow,
)

/** Trip screen: per-leg summaries plus the whole-trip summary (ANDSET-004). */
object TripReducer {
    fun reduce(
        legs: List<Leg>,
        summary: TripSummary,
        profilesById: Map<String, Profile> = emptyMap(),
    ): TripScreenState {
        fun nameOf(profileId: String): String = profilesById[profileId]?.name ?: profileId

        return TripScreenState(
            legs = legs.sortedBy { it.legIndex }.map { leg ->
                LegRow(
                    legIndex = leg.legIndex,
                    destinationName = leg.destinationName,
                    arrived = leg.arrivedAt != null,
                    miles = leg.summary?.miles,
                    wallHours = leg.summary?.wallMinutes?.let { it / 60.0 },
                    movingHours = leg.summary?.movingMinutes?.let { it / 60.0 },
                    stopCount = leg.summary?.stopCount,
                    states = leg.summary?.states.orEmpty(),
                    gamesPlayed = leg.summary?.gamesPlayed,
                )
            },
            summary = TripSummaryRow(
                miles = summary.miles,
                wallHours = summary.wallMinutes / 60.0,
                movingHours = summary.movingMinutes / 60.0,
                statesCount = summary.statesCount,
                stopCount = summary.stopCount,
                gamesPlayed = summary.gamesPlayed,
                winsByName = summary.winsByProfile.mapKeys { nameOf(it.key) },
                journalPostsByName = summary.journalPostsByProfile.mapKeys { nameOf(it.key) },
            ),
        )
    }
}
