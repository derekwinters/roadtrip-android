package com.roadtrip.core.games

import com.roadtrip.core.api.GameType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Pure last-move-highlight resolution (ANDGAME-024). The single most-recent move is resolved
 * from the move stream and the current cursor; the Compose views just map the resulting
 * [LastMoveHighlight]/[LastMoveShade] roles to concrete colors.
 */
class LastMoveTest {

    private fun cell(index: Int): JsonObject = buildJsonObject { put("cell", index) }
    private fun ultimate(board: Int, cellIndex: Int): JsonObject = buildJsonObject {
        put("board", board)
        put("cell", cellIndex)
    }
    private fun chessMove(from: String, to: String): JsonObject = buildJsonObject {
        put("from", from)
        put("to", to)
    }

    @Test
    fun `no highlight before the first move or on an empty stream ANDGAME-024`() {
        // index 0 == initial position: nothing has moved yet.
        assertNull(LastMove.lastMove(GameType.TICTACTOE, listOf(cell(4)), index = 0))
        assertNull(LastMove.lastMove(GameType.CHESS, listOf(chessMove("e2", "e4")), index = 0))
        // Empty stream: no move regardless of index.
        assertNull(LastMove.lastMove(GameType.TICTACTOE, emptyList(), index = 0))
        assertNull(LastMove.lastMove(GameType.HANGMAN, emptyList(), index = 3))
        assertNull(LastMove.lastHangmanGuess(null))
    }

    @Test
    fun `tictactoe highlights the last-placed cell ANDGAME-024`() {
        val moves = listOf(cell(4), cell(0), cell(2))
        val hl = assertIs<LastMoveHighlight.MarkCell>(
            LastMove.lastMove(GameType.TICTACTOE, moves, index = 3),
        )
        assertEquals(2, hl.cellIndex)
    }

    @Test
    fun `ultimate highlights the last board and cell ANDGAME-024`() {
        val moves = listOf(ultimate(4, 0), ultimate(0, 4), ultimate(4, 1))
        val hl = assertIs<LastMoveHighlight.UltimateCell>(
            LastMove.lastMove(GameType.ULTIMATE, moves, index = 3),
        )
        assertEquals(4, hl.board)
        assertEquals(1, hl.cell)
    }

    @Test
    fun `chess highlights greyed origin and dim-accent destination ANDGAME-024`() {
        val moves = listOf(chessMove("e2", "e4"), chessMove("e7", "e5"))
        val hl = assertIs<LastMoveHighlight.PieceMove>(
            LastMove.lastMove(GameType.CHESS, moves, index = 2),
        )
        assertEquals("e7", hl.origin)
        assertEquals("e5", hl.destination)
        assertEquals(emptyList(), hl.captured)
        // The origin greys darker, the destination gets the dim accent, others unshaded.
        assertEquals(LastMoveShade.PIECE_ORIGIN, hl.shadeFor("e7"))
        assertEquals(LastMoveShade.PIECE_DESTINATION, hl.shadeFor("e5"))
        assertNull(hl.shadeFor("d4"))
    }

    @Test
    fun `checkers highlights origin destination and captured squares ANDGAME-024`() {
        val moves = listOf(
            buildJsonObject { put("from", "c3"); put("to", "b4") },
            buildJsonObject {
                put("from", "a5")
                put("to", "c3")
                putJsonArray("captured") { add("b4") }
            },
        )
        val hl = assertIs<LastMoveHighlight.PieceMove>(
            LastMove.lastMove(GameType.CHECKERS, moves, index = 2),
        )
        assertEquals("a5", hl.origin)
        assertEquals("c3", hl.destination)
        assertEquals(listOf("b4"), hl.captured)
        assertEquals(LastMoveShade.PIECE_ORIGIN, hl.shadeFor("a5"))
        assertEquals(LastMoveShade.PIECE_DESTINATION, hl.shadeFor("c3"))
        assertEquals(LastMoveShade.PIECE_CAPTURE, hl.shadeFor("b4"))
    }

    @Test
    fun `hangman highlights the most recent guessed letter ANDGAME-024`() {
        val moves = listOf(
            buildJsonObject { put("letter", "R") },
            buildJsonObject { put("letter", "o") }, // lower-cased on the wire
        )
        val hl = assertIs<LastMoveHighlight.HangmanGuess>(
            LastMove.lastMove(GameType.HANGMAN, moves, index = 2),
        )
        assertEquals('O', hl.letter)
        // The explicit letter helper mirrors the stream resolution.
        assertEquals('O', LastMove.lastHangmanGuess('o')?.letter)
    }

    @Test
    fun `resolving at a replay cursor returns the move at index minus one not the final move ANDGAME-024`() {
        val moves = listOf(cell(4), cell(0), cell(2), cell(1), cell(6))
        // Scrubbed back to cursor 2: the last move is move #2 (cell 0), NOT the final cell 6.
        val atTwo = assertIs<LastMoveHighlight.MarkCell>(
            LastMove.lastMove(GameType.TICTACTOE, moves, index = 2),
        )
        assertEquals(0, atTwo.cellIndex)
        // The final cursor highlights the final move.
        val atEnd = assertIs<LastMoveHighlight.MarkCell>(
            LastMove.lastMove(GameType.TICTACTOE, moves, index = moves.size),
        )
        assertEquals(6, atEnd.cellIndex)
    }

    @Test
    fun `only the single latest move is highlighted advancing the cursor moves it ANDGAME-024`() {
        val moves = listOf(cell(4), cell(0), cell(2))
        val one = assertIs<LastMoveHighlight.MarkCell>(
            LastMove.lastMove(GameType.TICTACTOE, moves, index = 1),
        )
        assertEquals(4, one.cellIndex)
        val two = assertIs<LastMoveHighlight.MarkCell>(
            LastMove.lastMove(GameType.TICTACTOE, moves, index = 2),
        )
        assertEquals(0, two.cellIndex) // highlight jumped off cell 4 onto cell 0 (no trail)
    }

    @Test
    fun `ReplaySession exposes the current-cursor last move ANDGAME-024`() {
        val engine = ReplayEngine(GameType.TICTACTOE)
        val session = ReplaySession(engine, listOf(cell(4), cell(0), cell(2)), pinnedToLive = true)
        // Pinned to the live tail: the last-placed cell (2).
        assertEquals(2, assertIs<LastMoveHighlight.MarkCell>(session.lastMove(GameType.TICTACTOE)).cellIndex)
        // Scrub back one: the highlight follows the cursor to the prior move (cell 0).
        session.stepBack()
        assertEquals(0, assertIs<LastMoveHighlight.MarkCell>(session.lastMove(GameType.TICTACTOE)).cellIndex)
        // Back to the initial position: nothing highlighted.
        session.stepBack()
        session.stepBack()
        assertNull(session.lastMove(GameType.TICTACTOE))
    }

    @Test
    fun `shade roles distinguish mark fill piece origin destination and capture ANDGAME-024`() {
        // Four distinct roles so the Compose layer can map each to its own fill; none is a border.
        assertEquals(4, LastMoveShade.entries.size)
        val move = LastMoveHighlight.PieceMove("a1", "b2", captured = listOf("c3"))
        assertEquals(LastMoveShade.PIECE_ORIGIN, move.shadeFor("a1"))
        assertEquals(LastMoveShade.PIECE_DESTINATION, move.shadeFor("b2"))
        assertEquals(LastMoveShade.PIECE_CAPTURE, move.shadeFor("c3"))
        assertNull(move.shadeFor("h8"))
    }
}
