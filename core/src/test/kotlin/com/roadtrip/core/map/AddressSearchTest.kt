package com.roadtrip.core.map

import com.roadtrip.core.api.ApiException
import com.roadtrip.core.api.GeocodeMatch
import com.roadtrip.core.api.HttpRoadtripApi
import com.roadtrip.core.common.Role
import com.roadtrip.core.testing.FakeRoadtripApi
import com.roadtrip.core.testing.GeocodeFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class AddressSearchTest {
    private val api = FakeRoadtripApi()
    private val search = AddressSearch(api)

    private val moab = GeocodeMatch("Moab, Grand County, Utah, United States", 38.5733, -109.5498)
    private val arches = GeocodeMatch("Arches National Park, Utah, United States", 38.7331, -109.5925)

    /**
     * Drives [AddressSearch] through the real [HttpRoadtripApi] JSON-decoding boundary against a
     * [MockWebServer] returning [body]. A [FakeRoadtripApi] can't cover this: it skips decoding,
     * so the envelope-mismatch bug (#85) only surfaces here, end to end.
     */
    private suspend fun searchAgainstServer(body: String): AddressSearchState {
        val server = MockWebServer()
        server.start()
        return try {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(body).addHeader("Content-Type", "application/json"),
            )
            val http = HttpRoadtripApi(server.url("/").toString(), { "profile-parent" })
            AddressSearch(http).search("Moab", Role.PARENT)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a well-formed bare-array response resolves to Results, never Error ANDMAP-011`() = runTest {
        val state = assertIs<AddressSearchState.Results>(searchAgainstServer(GeocodeFixtures.SINGLE_MATCH_JSON))
        assertEquals(listOf(GeocodeFixtures.SINGLE_MATCH), state.matches)
    }

    @Test
    fun `an empty bare-array response resolves to NoMatches, not Error ANDMAP-011`() = runTest {
        assertEquals(AddressSearchState.NoMatches, searchAgainstServer(GeocodeFixtures.EMPTY_JSON))
    }

    @Test
    fun `an explicit search lists the geocode matches ANDMAP-008`() = runTest {
        api.geocodeResults = listOf(moab, arches)

        val state = search.search("Moab", Role.PARENT)

        assertEquals(AddressSearchState.Results(listOf(moab, arches)), state)
        assertEquals(listOf("Moab"), api.geocodeQueries)
    }

    @Test
    fun `search only fires on the explicit action and exposes the in-flight state ANDMAP-008`() = runTest {
        api.geocodeHandler = { query ->
            // Mid-flight the reducer reports Searching.
            assertEquals(AddressSearchState.Searching, search.state)
            assertEquals("Moab", query)
            listOf(moab)
        }

        // Construction/typing alone never calls the server — idle until the action.
        assertEquals(AddressSearchState.Idle, search.state)
        assertTrue(api.geocodeQueries.isEmpty())

        search.search("Moab", Role.PARENT)
        assertEquals(AddressSearchState.Results(listOf(moab)), search.state)
    }

    @Test
    fun `never lists more than five matches ANDMAP-008`() = runTest {
        api.geocodeResults = (1..7).map { GeocodeMatch("Springfield $it", it.toDouble(), -it.toDouble()) }

        val state = assertIs<AddressSearchState.Results>(search.search("Springfield", Role.PARENT))

        assertEquals(5, state.matches.size)
    }

    @Test
    fun `picking a match prefills the editable name and the coordinates ANDMAP-008`() {
        val prefill = AddressSearch.pick(moab)

        assertEquals("Moab, Grand County, Utah, United States", prefill.name)
        assertEquals(38.5733, prefill.lat)
        assertEquals(-109.5498, prefill.lon)
    }

    @Test
    fun `no matches resolve to the empty state ANDMAP-008`() = runTest {
        api.geocodeResults = emptyList()

        assertEquals(AddressSearchState.NoMatches, search.search("Nowhereville ZZ", Role.PARENT))
    }

    @Test
    fun `a blank query clears back to idle without calling the server ANDMAP-008`() = runTest {
        api.geocodeResults = listOf(moab)
        search.search("Moab", Role.PARENT)

        assertEquals(AddressSearchState.Idle, search.search("   ", Role.PARENT))
        assertEquals(listOf("Moab"), api.geocodeQueries) // no second call
    }

    @Test
    fun `a transport failure resolves to the offline needs-internet state ANDMAP-009`() = runTest {
        api.offline = true

        assertEquals(AddressSearchState.Offline, search.search("Moab", Role.PARENT))
    }

    @Test
    fun `a 503 geocode_unavailable answer resolves to the offline state ANDMAP-009`() = runTest {
        // Backend GSR-004: upstream geocoder unreachable — effectively offline.
        api.geocodeHandler = { throw ApiException(503, "geocode_unavailable", "upstream geocoder unreachable") }

        assertEquals(AddressSearchState.Offline, search.search("Moab", Role.PARENT))
    }

    @Test
    fun `a 503 geocode_upstream_error answer resolves to the service-unavailable state ANDMAP-011`() = runTest {
        // Backend GSR-006: geocoder reached but errored (e.g. 429) — online, so not needs-internet.
        api.geocodeHandler = { throw ApiException(503, "geocode_upstream_error", "nominatim returned 429") }

        assertEquals(AddressSearchState.ServiceUnavailable, search.search("Moab", Role.PARENT))
    }

    @Test
    fun `another server error while online resolves to the service-unavailable state ANDMAP-011`() = runTest {
        api.geocodeHandler = { throw ApiException(500, "internal", "boom") }

        assertEquals(AddressSearchState.ServiceUnavailable, search.search("Moab", Role.PARENT))
    }

    @Test
    fun `an unexpected failure surfaces as a distinct logged error state ANDMAP-011`() = runTest {
        val boom = IllegalStateException("parse failure")
        api.geocodeHandler = { throw boom }

        val state = assertIs<AddressSearchState.Error>(search.search("Moab", Role.PARENT))
        assertEquals(boom, state.cause)
    }

    @Test
    fun `an unexpected 4xx ApiException surfaces as the error state ANDMAP-011`() = runTest {
        api.geocodeHandler = { throw ApiException(400, "bad_request", "malformed query") }

        assertIs<AddressSearchState.Error>(search.search("Moab", Role.PARENT))
    }

    @Test
    fun `kid role never reaches the geocode endpoint ANDMAP-008`() = runTest {
        api.geocodeResults = listOf(moab)

        val state = search.search("Moab", Role.KID)

        // Gated by the role attribute like the other parent affordances (AND-003).
        assertEquals(AddressSearchState.Idle, state)
        assertTrue(api.geocodeQueries.isEmpty())
    }
}
