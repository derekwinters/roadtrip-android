package com.roadtrip.core.games

import com.roadtrip.core.api.EventDto
import com.roadtrip.core.api.GameType
import com.roadtrip.core.storage.InMemoryCacheStore
import com.roadtrip.core.testing.FakeClock
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class OfflineGateTest {

    @Test
    fun `game actions are disabled offline with an explanatory reason ANDGAME-008`() {
        val offline = GameOfflineGate.evaluate(online = false)
        assertFalse(offline.enabled)
        assertEquals(GameOfflineGate.OFFLINE_REASON, offline.reason)

        val online = GameOfflineGate.evaluate(online = true)
        assertTrue(online.enabled)
        assertNull(online.reason)
    }

    @Test
    fun `cached replays of finished games still work offline ANDGAME-008`() {
        // The move stream was cached while online...
        val cache = InMemoryCacheStore<List<EventDto>>()
        cache.write(
            listOf(
                TestData.gameMoveEvent(1, "g-done", 1, buildJsonObject { put("cell", 4) }, TestData.kid.id),
                TestData.gameMoveEvent(2, "g-done", 2, buildJsonObject { put("cell", 0) }, TestData.parent.id),
                TestData.gameMoveEvent(3, "g-done", 3, buildJsonObject { put("cell", 8) }, TestData.kid.id),
            ),
            FakeClock.T0,
        )

        // ...and replays entirely locally: no API involved anywhere below.
        val cachedMoves = cache.read()!!.value.map { it.payload["move"]!!.jsonObject }
        val session = ReplaySession(ReplayEngine(GameType.TICTACTOE), cachedMoves)
        session.seek(3)

        val board = assertIs<BoardState.TttBoard>(session.board())
        assertEquals('X', board.cells[4])
        assertEquals('O', board.cells[0])
        assertEquals('X', board.cells[8])
        assertNotNull(board)
    }
}
