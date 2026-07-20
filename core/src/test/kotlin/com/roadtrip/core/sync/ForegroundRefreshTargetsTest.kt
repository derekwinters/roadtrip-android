package com.roadtrip.core.sync

import com.roadtrip.core.notifications.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** covers: ANDMAP-012, ANDSYNC-008 */
class ForegroundRefreshTargetsTest {

    @Test
    fun `MAP refreshes both the map state and the destination list ANDMAP-012`() {
        // Issue #134: viewing the map, the destination list must stay live so mid-trip adds
        // and arrivals landing in the panel appear without leaving the screen.
        val targets = ForegroundRefreshTargets.forScreen(Screen.MAP)
        assertTrue(ForegroundReadModel.DESTINATIONS in targets, "MAP must re-pull destinations")
        assertTrue(ForegroundReadModel.MAP in targets, "MAP must re-pull the map state")
        assertEquals(setOf(ForegroundReadModel.MAP, ForegroundReadModel.DESTINATIONS), targets)
    }

    @Test
    fun `each screen maps to its own read models ANDSYNC-008`() {
        assertEquals(setOf(ForegroundReadModel.JOURNAL), ForegroundRefreshTargets.forScreen(Screen.JOURNAL))
        assertEquals(setOf(ForegroundReadModel.GAMES), ForegroundRefreshTargets.forScreen(Screen.GAMES))
        assertEquals(setOf(ForegroundReadModel.CHECKLIST), ForegroundRefreshTargets.forScreen(Screen.CHECKLIST))
        assertEquals(
            setOf(ForegroundReadModel.TRIPS, ForegroundReadModel.TRIP_SUMMARY, ForegroundReadModel.LEGS),
            ForegroundRefreshTargets.forScreen(Screen.TRIP),
        )
    }

    @Test
    fun `board and settings have no live read model to re-pull ANDSYNC-008`() {
        assertTrue(ForegroundRefreshTargets.forScreen(Screen.GAME_BOARD).isEmpty())
        assertTrue(ForegroundRefreshTargets.forScreen(Screen.SETTINGS).isEmpty())
    }
}
