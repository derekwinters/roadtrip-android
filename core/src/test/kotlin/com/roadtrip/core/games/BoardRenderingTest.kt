package com.roadtrip.core.games

import com.roadtrip.core.api.GameType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Pure board-rendering helpers (ANDGAME-012/010) and win-line exposure (ANDGAME-014).
 * Compose views stay thin; everything asserted here is plain-JVM logic.
 */
class BoardRenderingTest {

    private fun cell(index: Int): JsonObject = buildJsonObject { put("cell", index) }
    private fun ultimate(board: Int, cellIndex: Int): JsonObject = buildJsonObject {
        put("board", board)
        put("cell", cellIndex)
    }

    // ---- ANDGAME-013: piece glyphs + ink -------------------------------------------------

    @Test
    fun `chess uses solid glyphs for both colors distinguished by ink ANDGAME-013`() {
        // Both white and black map to the SAME solid glyph; only the ink differs.
        assertEquals(BoardPieces.chessGlyph("wK"), BoardPieces.chessGlyph("bK"))
        assertEquals("♚", BoardPieces.chessGlyph("wK"))
        assertEquals("♟", BoardPieces.chessGlyph("bP"))
        assertNull(BoardPieces.chessGlyph(null))

        // White reads as an outlined white fill; black reads as black.
        assertEquals(PieceInk.WHITE_OUTLINED, BoardPieces.chessInk("wQ"))
        assertEquals(PieceInk.BLACK, BoardPieces.chessInk("bQ"))
        assertNull(BoardPieces.chessInk(null))
    }

    @Test
    fun `checkers renders red vs black never white ANDGAME-013`() {
        assertEquals(PieceInk.BLACK, BoardPieces.checkersInk("w"))
        assertEquals(PieceInk.BLACK, BoardPieces.checkersInk("W"))
        assertEquals(PieceInk.RED, BoardPieces.checkersInk("r"))
        assertEquals(PieceInk.RED, BoardPieces.checkersInk("R"))
        // No checkers piece is ever tinted the old white ink.
        assertTrue(listOf("w", "W", "r", "R").none { BoardPieces.checkersInk(it) == PieceInk.WHITE_OUTLINED })
        // Men are discs, kings crowned.
        assertEquals("●", BoardPieces.checkersGlyph("w"))
        assertEquals("♛", BoardPieces.checkersGlyph("R"))
        assertNull(BoardPieces.checkersGlyph(null))
    }

    // ---- ANDGAME-012: board sizing ------------------------------------------------------

    @Test
    fun `board is bounded to the smaller dimension so it fits without scrolling ANDGAME-012`() {
        // Wide tablet slot: height is the limiting dimension -> square capped to height.
        assertEquals(600f, BoardMetrics.squareSide(availableWidth = 1000f, availableHeight = 600f))
        // Tall phone slot: width limits.
        assertEquals(400f, BoardMetrics.squareSide(availableWidth = 400f, availableHeight = 900f))
        // Never negative.
        assertEquals(0f, BoardMetrics.squareSide(availableWidth = -5f, availableHeight = 100f))
    }

    @Test
    fun `piece glyph scales to a fraction of the square not a fixed size ANDGAME-012`() {
        assertEquals(0.7f, BoardMetrics.PIECE_GLYPH_FRACTION)
        assertEquals(70f, BoardMetrics.pieceGlyphSize(100f))
        assertEquals(56f, BoardMetrics.pieceGlyphSize(80f))
    }

    // ---- ANDGAME-014: winning line exposure ---------------------------------------------

    @Test
    fun `ttt exposes the winning line indices not just the symbol ANDGAME-014`() {
        val engine = ReplayEngine(GameType.TICTACTOE)
        val moves = listOf(cell(4), cell(0), cell(2), cell(1), cell(6)) // X on 2-4-6 diagonal
        val finished = assertIs<BoardState.TttBoard>(engine.fold(moves))
        assertEquals('X', finished.winner)
        assertEquals(listOf(2, 4, 6), finished.winningLine)

        val open = assertIs<BoardState.TttBoard>(engine.stateAt(moves, 3))
        assertNull(open.winningLine)
    }

    @Test
    fun `tttWinningLine is a pure reusable helper ANDGAME-014`() {
        val row = listOf('X', 'X', 'X', null, 'O', null, 'O', null, null)
        assertEquals(listOf(0, 1, 2), tttWinningLine(row))
        assertEquals('X', tttWinner(row))
        assertNull(tttWinningLine(List(9) { null }))
    }

    @Test
    fun `ultimate exposes macro and captured-sub-board winning lines ANDGAME-014`() {
        val engine = ReplayEngine(GameType.ULTIMATE)
        // X wins sub-boards 0, 1, 2 (the top macro row) on their top rows.
        val moves = listOf(
            ultimate(0, 0), ultimate(0, 3), // X b0c0 / O b0c3
            ultimate(0, 1), ultimate(0, 4), // X b0c1
            ultimate(0, 2), // X wins board 0 (0,1,2) -> dictates board 2, but continue building
            ultimate(2, 3), // O
            ultimate(1, 0), ultimate(1, 3),
            ultimate(1, 1), ultimate(1, 4),
            ultimate(1, 2), // X wins board 1
            ultimate(2, 5), // O
            ultimate(2, 0), ultimate(2, 6),
            ultimate(2, 1), ultimate(2, 7),
            ultimate(2, 2), // X wins board 2 -> macro row 0,1,2 complete
        )
        val state = assertIs<BoardState.UltimateBoard>(engine.fold(moves))
        assertEquals('X', state.macroWinner)
        assertEquals(listOf(0, 1, 2), state.macroWinningLine)
        // Deciding cells inside a captured sub-board are exposed too.
        assertEquals(listOf(0, 1, 2), state.boardWinningLines[0])
        assertNull(state.boardWinningLines[3]) // untouched board has no line
    }
}
