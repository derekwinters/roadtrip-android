package com.roadtrip.core.games

import com.roadtrip.core.api.GameStatus
import com.roadtrip.core.api.GameType
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerLegendTest {
    private val me = TestData.kid.id
    private val derek = TestData.parent.id
    private val theo = TestData.otherKid.id
    private val names = mapOf(
        TestData.parent.id to "Dad",
        TestData.kid.id to "Maya",
        TestData.otherKid.id to "Kid",
    )

    @Test
    fun `maps X to the creator and O to the opponent ANDGAME-020`() {
        val game = TestData.game(
            type = GameType.TICTACTOE,
            createdBy = derek,
            opponentId = theo,
            status = GameStatus.ACTIVE,
        )
        val legend = PlayerLegend.forGame(game, me, names)
        assertEquals(2, legend.size)
        assertEquals('X', legend[0].mark)
        assertEquals(derek, legend[0].playerId)
        assertEquals("Dad", legend[0].name)
        assertEquals('O', legend[1].mark)
        assertEquals(theo, legend[1].playerId)
        assertEquals("Kid", legend[1].name)
    }

    @Test
    fun `ultimate games get the same X-creator O-opponent legend ANDGAME-020`() {
        val game = TestData.game(
            type = GameType.ULTIMATE,
            createdBy = derek,
            opponentId = theo,
            status = GameStatus.ACTIVE,
        )
        val legend = PlayerLegend.forGame(game, me, names)
        assertEquals(listOf('X', 'O'), legend.map { it.mark })
        assertEquals(derek, legend[0].playerId)
        assertEquals(theo, legend[1].playerId)
    }

    @Test
    fun `isTurn follows Game turn and only in an active game ANDGAME-020`() {
        val base = TestData.game(
            type = GameType.TICTACTOE,
            createdBy = derek,
            opponentId = theo,
            status = GameStatus.ACTIVE,
        )
        val creatorTurn = PlayerLegend.forGame(base.copy(turn = derek), me, names)
        assertTrue(creatorTurn[0].isTurn)
        assertFalse(creatorTurn[1].isTurn)

        val opponentTurn = PlayerLegend.forGame(base.copy(turn = theo), me, names)
        assertFalse(opponentTurn[0].isTurn)
        assertTrue(opponentTurn[1].isTurn)

        // A finished game highlights no row even though `turn` still points at a player.
        val finished = PlayerLegend.forGame(
            base.copy(turn = derek, status = GameStatus.FINISHED), me, names,
        )
        assertFalse(finished[0].isTurn)
        assertFalse(finished[1].isTurn)
    }

    @Test
    fun `falls back to Someone for an uncached or absent opponent ANDGAME-020`() {
        // Opponent id present but not in the cache.
        val uncached = TestData.game(
            type = GameType.TICTACTOE,
            createdBy = derek,
            opponentId = "p-ghost",
            status = GameStatus.ACTIVE,
        )
        val legendA = PlayerLegend.forGame(uncached, me, names)
        assertEquals(GameLobbyLabeler.UNKNOWN_PLAYER, legendA[1].name)
        assertFalse(legendA[1].name.contains("p-ghost"))

        // No opponent yet (open game): O still renders as "Someone", never on turn.
        val open = TestData.game(
            type = GameType.TICTACTOE,
            createdBy = derek,
            opponentId = null,
            status = GameStatus.OPEN,
        )
        val legendB = PlayerLegend.forGame(open, me, names)
        assertEquals(GameLobbyLabeler.UNKNOWN_PLAYER, legendB[1].name)
        assertEquals(null, legendB[1].playerId)
        assertFalse(legendB[1].isTurn)
    }

    @Test
    fun `isYou marks the viewing profile's row ANDGAME-020`() {
        val game = TestData.game(
            type = GameType.TICTACTOE,
            createdBy = derek,
            opponentId = me,
            status = GameStatus.ACTIVE,
        )
        val legend = PlayerLegend.forGame(game, me, names)
        assertFalse(legend[0].isYou) // X = Dad, not me
        assertTrue(legend[1].isYou) // O = me
    }

    @Test
    fun `non-mark game types produce no legend ANDGAME-020`() {
        for (type in listOf(GameType.CHESS, GameType.CHECKERS, GameType.HANGMAN)) {
            val game = TestData.game(
                type = type,
                createdBy = derek,
                opponentId = theo,
                status = GameStatus.ACTIVE,
            )
            assertTrue(PlayerLegend.forGame(game, me, names).isEmpty(), "expected empty for $type")
        }
    }

    @Test
    fun `places the legend beside the board only at or above the tablet threshold ANDGAME-020`() {
        assertEquals(600, PlayerLegend.BESIDE_MIN_WIDTH_DP)
        assertFalse(PlayerLegend.placeBeside(599)) // phone / narrow — below the board
        assertTrue(PlayerLegend.placeBeside(600)) // tablet / wide — beside the board
        assertTrue(PlayerLegend.placeBeside(840))
    }
}
