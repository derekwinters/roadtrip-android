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
    fun `hangman produces no legend ANDGAME-020`() {
        val game = TestData.game(
            type = GameType.HANGMAN,
            createdBy = derek,
            opponentId = theo,
            status = GameStatus.ACTIVE,
        )
        assertTrue(PlayerLegend.forGame(game, me, names).isEmpty())
    }

    @Test
    fun `chess maps white to the creator and black to the opponent ANDGAME-020`() {
        val game = TestData.game(
            type = GameType.CHESS,
            createdBy = derek,
            opponentId = theo,
            status = GameStatus.ACTIVE,
        )
        val legend = PlayerLegend.forGame(game, me, names)
        assertEquals(2, legend.size)
        assertEquals(PieceInk.WHITE_OUTLINED, legend[0].ink)
        assertEquals(derek, legend[0].playerId)
        assertEquals("Dad", legend[0].name)
        assertEquals(PieceInk.BLACK, legend[1].ink)
        assertEquals(theo, legend[1].playerId)
        assertEquals("Kid", legend[1].name)
        // Colour rows carry no X/O mark.
        assertEquals(listOf<Char?>(null, null), legend.map { it.mark })
    }

    @Test
    fun `checkers maps red to the creator and black to the opponent ANDGAME-020`() {
        val game = TestData.game(
            type = GameType.CHECKERS,
            createdBy = derek,
            opponentId = theo,
            status = GameStatus.ACTIVE,
        )
        val legend = PlayerLegend.forGame(game, me, names)
        assertEquals(2, legend.size)
        assertEquals(PieceInk.RED, legend[0].ink)
        assertEquals(derek, legend[0].playerId)
        assertEquals(PieceInk.BLACK, legend[1].ink)
        assertEquals(theo, legend[1].playerId)
        assertEquals(listOf<Char?>(null, null), legend.map { it.mark })
    }

    @Test
    fun `colour swatches use the same ink as the board's pieces ANDGAME-020`() {
        val chess = PlayerLegend.forGame(
            TestData.game(type = GameType.CHESS, createdBy = derek, opponentId = theo, status = GameStatus.ACTIVE),
            me,
            names,
        )
        assertEquals(BoardPieces.chessInk("wK"), chess[0].ink)
        assertEquals(BoardPieces.chessInk("bK"), chess[1].ink)

        val checkers = PlayerLegend.forGame(
            TestData.game(type = GameType.CHECKERS, createdBy = derek, opponentId = theo, status = GameStatus.ACTIVE),
            me,
            names,
        )
        assertEquals(BoardPieces.checkersInk("r"), checkers[0].ink)
        assertEquals(BoardPieces.checkersInk("w"), checkers[1].ink)
    }

    @Test
    fun `four in a row maps R to the creator and Y to the opponent ANDGAME-020 ANDGAME-028`() {
        val game = TestData.game(
            type = GameType.FOURINAROW,
            createdBy = derek,
            opponentId = theo,
            status = GameStatus.ACTIVE,
        )
        val legend = PlayerLegend.forGame(game, me, names)
        assertEquals(2, legend.size)
        assertEquals('R', legend[0].mark)
        assertEquals(derek, legend[0].playerId)
        assertEquals("Dad", legend[0].name)
        assertEquals('Y', legend[1].mark)
        assertEquals(theo, legend[1].playerId)
        assertEquals("Kid", legend[1].name)
        // Mark rows carry no colour swatch.
        assertEquals(listOf<PieceInk?>(null, null), legend.map { it.ink })
    }

    @Test
    fun `mark rows carry no colour swatch ANDGAME-020`() {
        val game = TestData.game(
            type = GameType.TICTACTOE,
            createdBy = derek,
            opponentId = theo,
            status = GameStatus.ACTIVE,
        )
        assertEquals(listOf<PieceInk?>(null, null), PlayerLegend.forGame(game, me, names).map { it.ink })
    }

    @Test
    fun `isYou resolves on either side of a chess or checkers game ANDGAME-020`() {
        for (type in listOf(GameType.CHESS, GameType.CHECKERS, GameType.FOURINAROW)) {
            val meCreator = PlayerLegend.forGame(
                TestData.game(type = type, createdBy = me, opponentId = theo, status = GameStatus.ACTIVE),
                me,
                names,
            )
            assertTrue(meCreator[0].isYou, "creator row should be me for $type")
            assertFalse(meCreator[1].isYou, "opponent row should not be me for $type")

            val meOpponent = PlayerLegend.forGame(
                TestData.game(type = type, createdBy = derek, opponentId = me, status = GameStatus.ACTIVE),
                me,
                names,
            )
            assertFalse(meOpponent[0].isYou, "creator row should not be me for $type")
            assertTrue(meOpponent[1].isYou, "opponent row should be me for $type")
        }
    }

    @Test
    fun `isTurn on chess and checkers follows Game turn only while active ANDGAME-020`() {
        for (type in listOf(GameType.CHESS, GameType.CHECKERS, GameType.FOURINAROW)) {
            val base = TestData.game(type = type, createdBy = derek, opponentId = theo, status = GameStatus.ACTIVE)

            val creatorTurn = PlayerLegend.forGame(base.copy(turn = derek), me, names)
            assertEquals(listOf(true, false), creatorTurn.map { it.isTurn }, "creator's turn, $type")

            val opponentTurn = PlayerLegend.forGame(base.copy(turn = theo), me, names)
            assertEquals(listOf(false, true), opponentTurn.map { it.isTurn }, "opponent's turn, $type")

            val finished = PlayerLegend.forGame(
                base.copy(turn = derek, status = GameStatus.FINISHED), me, names,
            )
            assertEquals(listOf(false, false), finished.map { it.isTurn }, "finished, $type")
        }
    }

    @Test
    fun `chess and checkers with no opponent yet show Someone and no turn ANDGAME-020`() {
        for (type in listOf(GameType.CHESS, GameType.CHECKERS, GameType.FOURINAROW)) {
            val open = TestData.game(type = type, createdBy = derek, opponentId = null, status = GameStatus.OPEN)
            val legend = PlayerLegend.forGame(open.copy(turn = derek), me, names)
            assertEquals(2, legend.size, "two rows even when open, $type")
            assertEquals(null, legend[1].playerId)
            assertEquals(GameLobbyLabeler.UNKNOWN_PLAYER, legend[1].name)
            assertEquals(listOf(false, false), legend.map { it.isTurn }, "open game has no turn, $type")

            val uncached = TestData.game(
                type = type,
                createdBy = "p-ghost",
                opponentId = theo,
                status = GameStatus.ACTIVE,
            )
            val ghost = PlayerLegend.forGame(uncached, me, names)
            assertEquals(GameLobbyLabeler.UNKNOWN_PLAYER, ghost[0].name)
            assertFalse(ghost[0].name.contains("p-ghost"))
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
