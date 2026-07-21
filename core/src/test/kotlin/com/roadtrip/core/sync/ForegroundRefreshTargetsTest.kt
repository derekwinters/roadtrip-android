package com.roadtrip.core.sync

import com.roadtrip.core.notifications.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** covers: ANDMAP-012, ANDSYNC-008, ANDSYNC-009, ANDBNG-006 */
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

    @Test
    fun `bingo needs a full sync pass so queued marks flush and reconcile ANDSYNC-009 ANDBNG-006`() {
        // The bingo card is fed by locally-queued offline plate.* writes, so a pull-only re-pull
        // would never push a parked device's marks nor pull the other device's — it must run a
        // full serialized sync pass (flush + pull) on the foreground cadence.
        assertTrue(ForegroundRefreshTargets.requiresFullSync(Screen.BINGO))
        // Its cheap read-model set is empty: the full pass refreshes the bingo cache, not a target.
        assertTrue(ForegroundRefreshTargets.forScreen(Screen.BINGO).isEmpty())
    }

    @Test
    fun `pull-only screens keep the cheap re-pull ANDSYNC-009`() {
        // Server-owned or server-arbitrated screens (incl. the games lobby, whose moves are
        // server-arbitrated) must NOT be forced into a full pass every cadence.
        for (screen in listOf(Screen.JOURNAL, Screen.MAP, Screen.GAMES, Screen.CHECKLIST, Screen.TRIP)) {
            assertFalse(ForegroundRefreshTargets.requiresFullSync(screen), "$screen must stay pull-only")
        }
    }
}
