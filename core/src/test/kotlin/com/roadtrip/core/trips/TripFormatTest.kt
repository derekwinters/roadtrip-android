package com.roadtrip.core.trips

import kotlin.test.Test
import kotlin.test.assertEquals

class TripFormatTest {
    @Test
    fun `renders minutes as h-mm clock format ANDTRIP-010`() {
        assertEquals("1:12", TripFormat.clock(72.0))
        assertEquals("8:30", TripFormat.clock(510.0))
        assertEquals("0:45", TripFormat.clock(45.0))
        assertEquals("0:00", TripFormat.clock(0.0))
    }

    @Test
    fun `rounds to the nearest minute once and carries into the hour ANDTRIP-010`() {
        assertEquals("1:00", TripFormat.clock(59.6)) // never 0:60
        assertEquals("2:00", TripFormat.clock(119.6))
        assertEquals("0:59", TripFormat.clock(59.4))
    }

    @Test
    fun `whole-trip and history duration label is total and moving with no unit ANDTRIP-010`() {
        assertEquals("15:00 total · 12:00 moving", TripFormat.durationLine(900.0, 720.0))
        assertEquals("1:00 total · 0:45 moving", TripFormat.durationLine(59.6, 45.0))
    }

    @Test
    fun `per-leg durations use the same clock formatter ANDTRIP-010`() {
        assertEquals(listOf("8:30", "7:30 moving"), TripFormat.legDurations(510.0, 450.0))
        assertEquals(listOf("1:00"), TripFormat.legDurations(59.6, null))
        assertEquals(emptyList(), TripFormat.legDurations(null, null))
    }

    @Test
    fun `miles keep one decimal place ANDTRIP-010`() {
        assertEquals("512.5 mi", TripFormat.miles(512.5))
        assertEquals("812.0 mi", TripFormat.miles(812.0))
    }
}
