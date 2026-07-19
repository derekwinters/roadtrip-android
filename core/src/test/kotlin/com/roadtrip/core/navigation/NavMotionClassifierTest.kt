package com.roadtrip.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavMotionClassifierTest {
    // covers: AND-011
    @Test
    fun `switching between two top-level tabs is a fade-through AND-011`() {
        // Route patterns as navigation-compose reports them, including argument templates.
        assertEquals(
            NavMotion.FADE_THROUGH,
            NavMotionClassifier.motionFor("journal", "map?lat={lat}&lon={lon}"),
        )
        assertEquals(
            NavMotion.FADE_THROUGH,
            NavMotionClassifier.motionFor("games", "trip?dest={dest}"),
        )
        assertEquals(
            NavMotion.FADE_THROUGH,
            NavMotionClassifier.motionFor("checklist?state={state}", "journal"),
        )
    }

    // covers: AND-011
    @Test
    fun `drilling into a detail route and popping back use shared-axis X AND-011`() {
        // Games -> Board/Replay/Bingo, Trip -> history, and Settings are all hierarchical.
        assertEquals(NavMotion.SHARED_AXIS_X, NavMotionClassifier.motionFor("games", "board/{gameId}"))
        assertEquals(NavMotion.SHARED_AXIS_X, NavMotionClassifier.motionFor("games", "replay/{gameId}"))
        assertEquals(NavMotion.SHARED_AXIS_X, NavMotionClassifier.motionFor("games", "bingo?trip={trip}"))
        assertEquals(NavMotion.SHARED_AXIS_X, NavMotionClassifier.motionFor("trip?dest={dest}", "trips?trip={trip}"))
        assertEquals(NavMotion.SHARED_AXIS_X, NavMotionClassifier.motionFor("journal", "settings"))
        // Popping back out is likewise shared-axis X.
        assertEquals(NavMotion.SHARED_AXIS_X, NavMotionClassifier.motionFor("board/{gameId}", "games"))
    }

    // covers: AND-011
    @Test
    fun `trips history is not confused with the trip tab AND-011`() {
        // "trips" (history, detail) must not read as the "trip" top-level tab.
        assertTrue(NavMotionClassifier.isTopLevel("trip?dest={dest}"))
        assertFalse(NavMotionClassifier.isTopLevel("trips?trip={trip}"))
    }

    // covers: AND-011
    @Test
    fun `null routes are treated as non-top-level AND-011`() {
        assertFalse(NavMotionClassifier.isTopLevel(null))
        assertEquals(NavMotion.SHARED_AXIS_X, NavMotionClassifier.motionFor(null, "journal"))
    }
}
