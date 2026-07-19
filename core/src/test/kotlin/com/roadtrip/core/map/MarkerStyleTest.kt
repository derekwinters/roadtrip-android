package com.roadtrip.core.map

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkerStyleTest {
    @Test
    fun `each marker kind maps to its distinct style ANDMAP-010`() {
        assertEquals(MarkerStyle.CAR, markerStyleFor(MarkerKind.CURRENT))
        assertEquals(MarkerStyle.RED_DOT, markerStyleFor(MarkerKind.START))
        assertEquals(MarkerStyle.GREEN_DOT_ACTIVE, markerStyleFor(MarkerKind.ACTIVE_DESTINATION))
        assertEquals(MarkerStyle.GREEN_DOT, markerStyleFor(MarkerKind.DESTINATION))
    }

    @Test
    fun `every marker kind has a style and the active destination is emphasized ANDMAP-010`() {
        // Total mapping: no kind falls back to a default pin.
        val styles = MarkerKind.entries.map { markerStyleFor(it) }
        assertEquals(MarkerKind.entries.size, styles.size)

        // Start and destinations are visually distinct (red vs green).
        assertEquals(MarkerStyle.RED_DOT, markerStyleFor(MarkerKind.START))
        // The active/next destination stands out from later pending stops.
        assertEquals(MarkerStyle.GREEN_DOT_ACTIVE, markerStyleFor(MarkerKind.ACTIVE_DESTINATION))
        assertEquals(MarkerStyle.GREEN_DOT, markerStyleFor(MarkerKind.DESTINATION))
    }
}
