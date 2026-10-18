package com.roadtrip.core.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class MarkerSelectionTest {
    private val car = MapMarker(MarkerKind.CURRENT, 40.0, -100.0)
    private val start = MapMarker(MarkerKind.START, 41.0, -101.0, "Home")
    private val next = MapMarker(MarkerKind.ACTIVE_DESTINATION, 42.0, -102.0, "Lake")
    private val later = MapMarker(MarkerKind.DESTINATION, 43.0, -103.0, "Park")

    @Test
    fun `tapping an unselected marker selects it ANDMAP-015`() {
        assertEquals(next.selectionKey(), MarkerSelection.onMarkerTap(null, next.selectionKey()))
    }

    @Test
    fun `tapping the selected marker again deselects it ANDMAP-015`() {
        assertNull(MarkerSelection.onMarkerTap(next.selectionKey(), next.selectionKey()))
    }

    @Test
    fun `tapping a different marker moves the selection ANDMAP-015`() {
        assertEquals(
            later.selectionKey(),
            MarkerSelection.onMarkerTap(next.selectionKey(), later.selectionKey()),
        )
    }

    @Test
    fun `tapping the empty map background deselects ANDMAP-015`() {
        assertNull(MarkerSelection.onBackgroundTap(car.selectionKey()))
        assertNull(MarkerSelection.onBackgroundTap(null))
    }

    @Test
    fun `selection survives a refresh while its marker is present ANDMAP-015`() {
        val selected = start.selectionKey()
        assertEquals(selected, MarkerSelection.reconcile(selected, listOf(car, start, next)))
    }

    @Test
    fun `the moving current-position marker stays selected across pings ANDMAP-015`() {
        val moved = car.copy(lat = 40.5, lon = -100.5)
        assertEquals(car.selectionKey(), moved.selectionKey())
        assertEquals(car.selectionKey(), MarkerSelection.reconcile(car.selectionKey(), listOf(moved)))
    }

    @Test
    fun `selection clears when the selected marker disappears ANDMAP-015`() {
        assertNull(MarkerSelection.reconcile(later.selectionKey(), listOf(car, start, next)))
        // The active destination becoming a different kind (arrival) is a different marker.
        val arrived = next.copy(kind = MarkerKind.DESTINATION)
        assertNull(MarkerSelection.reconcile(next.selectionKey(), listOf(car, arrived)))
        assertNull(MarkerSelection.reconcile(null, listOf(car)))
    }

    @Test
    fun `distinct destinations have distinct selection keys ANDMAP-015`() {
        val sameName = later.copy(lat = 44.0)
        assertNotEquals(later.selectionKey(), sameName.selectionKey())
        assertNotEquals(start.selectionKey(), later.selectionKey())
    }
}
