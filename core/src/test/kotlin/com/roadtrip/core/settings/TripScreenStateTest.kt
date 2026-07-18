package com.roadtrip.core.settings

import com.roadtrip.core.api.Leg
import com.roadtrip.core.api.LegSummary
import com.roadtrip.core.api.TripSummary
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripScreenStateTest {
    private val profiles = mapOf(
        TestData.parent.id to TestData.parent,
        TestData.kid.id to TestData.kid,
    )

    @Test
    fun `shows per-leg summaries and the whole-trip summary ANDSET-004`() {
        val legs = listOf(
            Leg(
                legIndex = 0,
                destinationId = "d-1",
                destinationName = "Denver",
                startedAt = TestData.ts(0),
                arrivedAt = TestData.ts(30_000),
                summary = LegSummary(
                    wallMinutes = 510.0, movingMinutes = 450.0, miles = 512.5,
                    stopCount = 3, states = listOf("IL", "IA", "NE", "CO"), gamesPlayed = 4,
                ),
            ),
            Leg(legIndex = 1, destinationName = "Rocky Mountain NP", startedAt = TestData.ts(40_000)),
        )
        val summary = TripSummary(
            miles = 812.0, wallMinutes = 900.0, movingMinutes = 720.0,
            statesCount = 5, stopCount = 6, gamesPlayed = 9,
            winsByProfile = mapOf(TestData.kid.id to 5, TestData.parent.id to 4),
            journalPostsByProfile = mapOf(TestData.kid.id to 12),
        )

        val screen = TripReducer.reduce(legs, summary, profiles)

        val done = screen.legs[0]
        assertEquals("Denver", done.destinationName)
        assertTrue(done.arrived)
        assertEquals(512.5, done.miles)
        assertEquals(8.5, done.wallHours) // 510 min
        assertEquals(7.5, done.movingHours)
        assertEquals(3, done.stopCount)
        assertEquals(listOf("IL", "IA", "NE", "CO"), done.states)
        assertEquals(4, done.gamesPlayed)

        val current = screen.legs[1]
        assertFalse(current.arrived)
        assertNull(current.miles) // still under way, no summary yet

        assertEquals(812.0, screen.summary.miles)
        assertEquals(15.0, screen.summary.wallHours)
        assertEquals(12.0, screen.summary.movingHours)
        assertEquals(5, screen.summary.statesCount)
        assertEquals(6, screen.summary.stopCount)
        assertEquals(9, screen.summary.gamesPlayed)
    }

    @Test
    fun `resolves games won and journal counts per person by name ANDSET-004`() {
        val summary = TripSummary(
            miles = 0.0, wallMinutes = 0.0, movingMinutes = 0.0,
            statesCount = 0, stopCount = 0, gamesPlayed = 3,
            winsByProfile = mapOf(TestData.kid.id to 2, "p-unknown" to 1),
            journalPostsByProfile = mapOf(TestData.parent.id to 7),
        )

        val screen = TripReducer.reduce(emptyList(), summary, profiles)

        assertEquals(mapOf("Maya" to 2, "p-unknown" to 1), screen.summary.winsByName)
        assertEquals(mapOf("Derek" to 7), screen.summary.journalPostsByName)
    }
}
