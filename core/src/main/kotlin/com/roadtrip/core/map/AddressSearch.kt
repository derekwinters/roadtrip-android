package com.roadtrip.core.map

import com.roadtrip.core.api.ApiException
import com.roadtrip.core.api.GeocodeMatch
import com.roadtrip.core.api.RoadtripApi
import com.roadtrip.core.common.Role
import com.roadtrip.core.profiles.FeatureVisibilityRules
import java.io.IOException

/** Address-search states of the add-destination flow (ANDMAP-008/009). */
sealed class AddressSearchState {
    /** Nothing searched yet, or the query was cleared/consumed. */
    object Idle : AddressSearchState()

    /** An explicit search is in flight. */
    object Searching : AddressSearchState()

    /** Server matches to list — never more than [AddressSearch.MAX_RESULTS]. */
    data class Results(val matches: List<GeocodeMatch>) : AddressSearchState()

    /** The server answered but nothing matched. */
    object NoMatches : AddressSearchState()

    /**
     * Offline or 503 `geocode_unavailable`: address search needs internet; pin and
     * coordinate entry remain the working paths (ANDMAP-009).
     */
    object Unavailable : AddressSearchState()
}

/** Form pre-fill produced by picking a match: editable name + coordinates (ANDMAP-008). */
data class DestinationPrefill(val name: String, val lat: Double, val lon: Double)

/**
 * Address search over the backend's GET /api/geocode proxy for the add-destination flow.
 * Searches fire only through the explicit [search] action — never per keystroke — and are
 * gated by the role attribute like the rest of destination editing (AND-003): kid profiles
 * never reach the endpoint (ANDMAP-008).
 */
class AddressSearch(private val api: RoadtripApi) {
    var state: AddressSearchState = AddressSearchState.Idle
        private set

    suspend fun search(query: String, role: Role): AddressSearchState {
        if (!FeatureVisibilityRules.forRole(role).destinationEditing) return state

        val q = query.trim()
        if (q.isEmpty()) {
            state = AddressSearchState.Idle
            return state
        }

        state = AddressSearchState.Searching
        state = try {
            val matches = api.geocode(q).take(MAX_RESULTS)
            if (matches.isEmpty()) AddressSearchState.NoMatches else AddressSearchState.Results(matches)
        } catch (e: IOException) {
            AddressSearchState.Unavailable // offline (ANDMAP-009)
        } catch (e: ApiException) {
            if (e.status == 503) AddressSearchState.Unavailable else throw e
        }
        return state
    }

    companion object {
        /** The backend returns at most 5 matches; the client never lists more (ANDMAP-008). */
        const val MAX_RESULTS = 5

        /** Picking a match fills coordinates and pre-fills the editable name (ANDMAP-008). */
        fun pick(match: GeocodeMatch): DestinationPrefill =
            DestinationPrefill(name = match.displayName, lat = match.lat, lon = match.lon)
    }
}
