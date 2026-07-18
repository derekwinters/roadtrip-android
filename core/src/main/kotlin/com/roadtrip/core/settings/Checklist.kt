package com.roadtrip.core.settings

import com.roadtrip.core.api.Checklist
import com.roadtrip.core.api.EventDto
import com.roadtrip.core.common.Timestamps
import java.time.Instant
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class StateRow(val state: String, val stateCode: String, val firstEnteredAt: Instant?)
data class CityRow(val city: String, val stateCode: String, val firstAt: Instant?)
data class StopRow(
    val lat: Double,
    val lon: Double,
    val startedAt: Instant?,
    val durationMin: Double?,
    val place: String?,
)

data class ChecklistScreenState(
    val states: List<StateRow>,
    val cities: List<CityRow>,
    val stops: List<StopRow>,
)

/**
 * Checklist read model from GET /api/checklist (cached for offline); states light up with
 * their first-entered time as `location.crossing.state` events arrive on the feed
 * (ANDSET-003).
 */
object ChecklistReducer {
    fun fromDto(checklist: Checklist): ChecklistScreenState = ChecklistScreenState(
        states = checklist.states.map {
            StateRow(it.state, it.stateCode, it.firstEnteredAt?.let(Timestamps::parse))
        },
        cities = checklist.cities.map {
            CityRow(it.city, it.stateCode, it.firstAt?.let(Timestamps::parse))
        },
        stops = checklist.stops.map {
            StopRow(it.lat, it.lon, it.startedAt?.let(Timestamps::parse), it.durationMin, it.place)
        },
    )

    fun applyEvent(state: ChecklistScreenState, event: EventDto): ChecklistScreenState {
        if (event.type != "location.crossing.state") return state
        val stateCode = event.payload["state_code"]?.jsonPrimitive?.contentOrNull ?: return state
        // Re-entering a state neither duplicates it nor moves its first-entered time.
        if (state.states.any { it.stateCode == stateCode }) return state

        val stateName = event.payload["state"]?.jsonPrimitive?.contentOrNull ?: stateCode
        return state.copy(
            states = state.states + StateRow(stateName, stateCode, Timestamps.parse(event.clientTs)),
        )
    }
}
