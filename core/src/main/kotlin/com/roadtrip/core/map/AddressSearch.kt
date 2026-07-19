package com.roadtrip.core.map

import com.roadtrip.core.api.ApiException
import com.roadtrip.core.api.GeocodeMatch
import com.roadtrip.core.api.RoadtripApi
import com.roadtrip.core.common.Role
import com.roadtrip.core.profiles.FeatureVisibilityRules
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

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
     * Genuinely offline: a transport failure (`IOException`) or the backend's
     * `geocode_unavailable` (upstream geocoder unreachable, backend GSR-004). Address search
     * needs internet; pin and coordinate entry remain the working paths (ANDMAP-009).
     */
    object Offline : AddressSearchState()

    /**
     * The device is online but the geocoder can't answer right now: the backend reached the
     * upstream and it errored (`geocode_upstream_error`, backend GSR-006), or another server
     * error came back. Distinct from [Offline] so the user isn't told they have no internet
     * (ANDMAP-011). Pin and coordinate entry remain available.
     */
    object ServiceUnavailable : AddressSearchState()

    /**
     * An unexpected failure that isn't a known connectivity/geocoder condition. Surfaced (and
     * logged by the caller) instead of silently degrading into [Offline] (ANDMAP-011). Pin and
     * coordinate entry remain available.
     */
    data class Error(val cause: Throwable) : AddressSearchState()
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
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: IOException) {
            AddressSearchState.Offline // transport failure — needs internet (ANDMAP-009)
        } catch (e: ApiException) {
            when {
                // Backend GSR-004: upstream geocoder unreachable — effectively offline (ANDMAP-009).
                e.code == "geocode_unavailable" -> AddressSearchState.Offline
                // Backend GSR-006: geocoder reached but errored — online but temporarily down (ANDMAP-011).
                e.code == "geocode_upstream_error" -> AddressSearchState.ServiceUnavailable
                // Any other server error while online reads as temporarily-unavailable (ANDMAP-011).
                e.status in 500..599 -> AddressSearchState.ServiceUnavailable
                // Unexpected (e.g. a 4xx contract violation): surface it, don't mask it (ANDMAP-011).
                else -> AddressSearchState.Error(e)
            }
        } catch (e: Exception) {
            AddressSearchState.Error(e) // genuinely unexpected — logged by the caller (ANDMAP-011)
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
