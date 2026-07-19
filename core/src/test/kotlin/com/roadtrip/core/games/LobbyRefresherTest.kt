package com.roadtrip.core.games

import com.roadtrip.core.api.Game
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LobbyRefresherTest {
    private val fetched = listOf(TestData.game(id = "g-fresh"))

    private class Recorder {
        var syncRequests = 0
        var fetches = 0
        var stored: List<Game>? = null
    }

    @Test
    fun `online reload requests a foreground sync and refetches games ANDGAME-010`() = runTest {
        val rec = Recorder()
        val refresher = LobbyRefresher(
            isOnline = { true },
            requestForegroundSync = { rec.syncRequests++ },
            fetchGames = { rec.fetches++; fetched },
            storeGames = { rec.stored = it },
        )

        val ran = refresher.reload()

        assertTrue(ran)
        assertEquals(1, rec.syncRequests)
        assertEquals(1, rec.fetches)
        assertEquals(listOf("g-fresh"), rec.stored?.map { it.id })
    }

    @Test
    fun `offline reload is skipped so the indicator does not spin ANDGAME-010`() = runTest {
        val rec = Recorder()
        val refresher = LobbyRefresher(
            isOnline = { false },
            requestForegroundSync = { rec.syncRequests++ },
            fetchGames = { rec.fetches++; fetched },
            storeGames = { rec.stored = it },
        )

        val ran = refresher.reload()

        assertFalse(ran)
        assertEquals(0, rec.syncRequests)
        assertEquals(0, rec.fetches)
        assertEquals(null, rec.stored)
    }

    @Test
    fun `a failed refetch still completes the reload and leaves the cache untouched ANDGAME-010`() = runTest {
        val rec = Recorder()
        val refresher = LobbyRefresher(
            isOnline = { true },
            requestForegroundSync = { rec.syncRequests++ },
            fetchGames = { throw RuntimeException("VPN dropped") },
            storeGames = { rec.stored = it },
        )

        val ran = refresher.reload()

        // Reload "ran" (indicator dismisses); sync was still requested; cache not overwritten.
        assertTrue(ran)
        assertEquals(1, rec.syncRequests)
        assertEquals(null, rec.stored)
    }
}
