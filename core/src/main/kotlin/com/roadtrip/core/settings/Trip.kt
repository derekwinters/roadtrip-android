package com.roadtrip.core.settings

import com.roadtrip.core.api.Leg
import com.roadtrip.core.api.Profile
import com.roadtrip.core.api.TripSummary

data class LegRow(
    val legIndex: Int,
    val destinationName: String?,
    val arrived: Boolean,
    val miles: Double?,
    val wallMinutes: Double?,
    val movingMinutes: Double?,
    val stopCount: Int?,
    val states: List<String>,
    val gamesPlayed: Int?,
)

data class TripSummaryRow(
    val miles: Double,
    val wallMinutes: Double,
    val movingMinutes: Double,
    val statesCount: Int,
    val stopCount: Int,
    val gamesPlayed: Int,
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
        return TripScreenState(
            legs = legs.sortedBy { it.legIndex }.map { leg ->
                LegRow(
                    legIndex = leg.legIndex,
                    destinationName = leg.destinationName,
                    arrived = leg.arrivedAt != null,
                    miles = leg.summary?.miles,
                    wallMinutes = leg.summary?.wallMinutes,
                    movingMinutes = leg.summary?.movingMinutes,
                    stopCount = leg.summary?.stopCount,
                    states = leg.summary?.states.orEmpty(),
                    gamesPlayed = leg.summary?.gamesPlayed,
                )
            },
            summary = TripSummaryRow(
                miles = summary.miles,
                wallMinutes = summary.wallMinutes,
                movingMinutes = summary.movingMinutes,
                statesCount = summary.statesCount,
                stopCount = summary.stopCount,
                gamesPlayed = summary.gamesPlayed,
            ),
        )
    }
}
