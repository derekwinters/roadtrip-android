package com.roadtrip.core.settings

import com.roadtrip.core.api.Checklist
import com.roadtrip.core.api.EventDto
import com.roadtrip.core.common.Timestamps
import com.roadtrip.core.sync.OutboxEntry
import java.time.Instant
import java.util.Locale
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class StateRow(val state: String, val stateCode: String, val firstEnteredAt: Instant?)
data class CityRow(val city: String, val stateCode: String, val firstAt: Instant?)
/**
 * A server-detected checklist stop (not a map destination or a leg). [stopId] is the
 * backend's stable id — the only way a stop is addressed (ANDSET-014/015).
 */
data class StopRow(
    val stopId: String,
    val lat: Double,
    val lon: Double,
    val startedAt: Instant?,
    val durationMin: Double?,
    val place: String?,
    val label: String? = null,
) {
    /** The row's name (ANDSET-013): custom label, else auto place, else lat/lon. */
    val displayName: String
        get() = label ?: place ?: String.format(Locale.US, "%.4f, %.4f", lat, lon)
}

data class ChecklistScreenState(
    val states: List<StateRow>,
    val cities: List<CityRow>,
    val stops: List<StopRow>,
) {
    /**
     * States with their cities nested by `stateCode` (ANDSET-009). A city whose state has no
     * row gets a synthesized, not-entered group named by its code (ANDSET-010); once the
     * crossing arrives via [ChecklistReducer.applyEvent] the real row takes its place.
     */
    val stateGroups: List<StateGroup>
        get() {
            val citiesByState = cities.groupBy { it.stateCode }
            val known = states.map { it.stateCode }.toSet()
            val real = states.map { StateGroup(it, citiesByState[it.stateCode].orEmpty()) }
            val synthesized = citiesByState
                .filterKeys { it !in known }
                .map { (code, rows) -> StateGroup(StateRow(code, code, firstEnteredAt = null), rows) }
            return real + synthesized
        }
}

data class StateGroup(val state: StateRow, val cities: List<CityRow>) {
    val cityCount: Int get() = cities.size
}

/**
 * View-local expansion of the checklist's state groups (ANDSET-011): collapsed by default,
 * with only the deep-linked state (ANDJRNL-004) expanded on arrival. Never persisted.
 */
object ChecklistExpansion {
    fun initial(groups: List<StateGroup>, highlightStateCode: String?): Set<String> =
        if (highlightStateCode != null && groups.any { it.state.stateCode == highlightStateCode }) {
            setOf(highlightStateCode)
        } else {
            emptySet()
        }

    fun toggle(expanded: Set<String>, stateCode: String): Set<String> =
        if (stateCode in expanded) expanded - stateCode else expanded + stateCode
}

/**
 * Checklist read model from GET /api/checklist (cached for offline); states light up with
 * their first-entered time as `location.crossing.state` events arrive on the feed
 * (ANDSET-003), with cities nested under their state (ANDSET-009).
 */
object ChecklistReducer {
    /**
     * [pendingOutbox] overlays not-yet-synced stop names on the cached read model so a
     * saved name shows at once, offline too (ANDSET-016): latest `client_ts` per `stop_id`
     * wins; names for stops the cache does not hold are ignored.
     */
    fun fromDto(checklist: Checklist, pendingOutbox: List<OutboxEntry> = emptyList()): ChecklistScreenState {
        val pendingLabels = pendingOutbox
            .filter { it.type == OutboxEntry.TYPE_STOP_LABELED }
            .sortedBy { it.clientTs }
            .mapNotNull { entry ->
                val stopId = entry.payload["stop_id"]?.jsonPrimitive?.contentOrNull
                val label = entry.payload["label"]?.jsonPrimitive?.contentOrNull
                if (stopId != null && label != null) stopId to label else null
            }
            .toMap()
        return ChecklistScreenState(
            states = checklist.states.map {
                StateRow(it.state, it.stateCode, it.firstEnteredAt?.let(Timestamps::parse))
            },
            cities = checklist.cities.map {
                CityRow(it.city, it.stateCode, it.firstAt?.let(Timestamps::parse))
            },
            stops = checklist.stops.map {
                StopRow(
                    stopId = it.stopId,
                    lat = it.lat,
                    lon = it.lon,
                    startedAt = it.startedAt?.let(Timestamps::parse),
                    durationMin = it.durationMin,
                    place = it.place,
                    label = pendingLabels[it.stopId] ?: it.label,
                )
            },
        )
    }

    /**
     * Folds a `location.stop.labeled` feed event into the cached read model so a synced
     * name survives until the next `GET /api/checklist` refresh (ANDSET-016). Events for
     * stops the cache does not hold, and every other event type, leave it unchanged.
     */
    fun applyToCache(checklist: Checklist, event: EventDto): Checklist {
        if (event.type != OutboxEntry.TYPE_STOP_LABELED) return checklist
        val stopId = event.payload["stop_id"]?.jsonPrimitive?.contentOrNull ?: return checklist
        val label = event.payload["label"]?.jsonPrimitive?.contentOrNull ?: return checklist
        if (checklist.stops.none { it.stopId == stopId }) return checklist
        return checklist.copy(
            stops = checklist.stops.map { if (it.stopId == stopId) it.copy(label = label) else it },
        )
    }

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
