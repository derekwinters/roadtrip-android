package com.roadtrip.core.testing

import com.roadtrip.core.api.GeocodeMatch

/**
 * Canonical `GET /api/geocode` wire fixtures. The backend emits a **bare top-level JSON array**
 * of `{display_name, lat, lon}` matches (backend GSR-002 / ANDMAP-008) — never an object
 * envelope such as `{results:[…]}`. These fixtures are shared by the HTTP-decoding tests and the
 * AddressSearch end-to-end tests so both exercise a body identical in shape to what the server
 * sends. The literals mirror the backend's own match shape, keeping cross-repo drift visible in
 * review.
 */
object GeocodeFixtures {
    /** Single-match body — the issue's canonical example. */
    const val SINGLE_MATCH_JSON =
        """[{"display_name":"Moab, Grand County, Utah","lat":38.5733,"lon":-109.5498}]"""

    val SINGLE_MATCH = GeocodeMatch("Moab, Grand County, Utah", 38.5733, -109.5498)

    /** No matches — still a JSON array, never an object. */
    const val EMPTY_JSON = "[]"

    /** Five matches: shape parity with the backend's capped (limit=5) response (GSR-002). */
    const val FIVE_MATCHES_JSON =
        """[{"display_name":"Moab, Grand County, Utah","lat":38.5733,"lon":-109.5498},""" +
            """{"display_name":"Arches National Park, Utah","lat":38.7331,"lon":-109.5925},""" +
            """{"display_name":"Canyonlands National Park, Utah","lat":38.2000,"lon":-109.9300},""" +
            """{"display_name":"Green River, Emery County, Utah","lat":38.9955,"lon":-110.1596},""" +
            """{"display_name":"Monticello, San Juan County, Utah","lat":37.8716,"lon":-109.3426}]"""

    val FIVE_MATCHES = listOf(
        GeocodeMatch("Moab, Grand County, Utah", 38.5733, -109.5498),
        GeocodeMatch("Arches National Park, Utah", 38.7331, -109.5925),
        GeocodeMatch("Canyonlands National Park, Utah", 38.2000, -109.9300),
        GeocodeMatch("Green River, Emery County, Utah", 38.9955, -110.1596),
        GeocodeMatch("Monticello, San Juan County, Utah", 37.8716, -109.3426),
    )
}
