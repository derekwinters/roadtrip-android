package com.roadtrip.core.games

import com.roadtrip.core.api.GameType
import com.roadtrip.core.api.RoadtripJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** covers: ANDGAME-028 — the Four in a Row board (backend GAME-020). */
class FourInARowTest {

    private fun drop(column: Int): JsonObject = buildJsonObject { put("column", column) }
    private fun drops(vararg columns: Int): List<JsonObject> = columns.map { drop(it) }

    private fun board(moves: List<JsonObject>): BoardState.FourInARowBoard =
        assertIs<BoardState.FourInARowBoard>(ReplayEngine(GameType.FOURINAROW).fold(moves))

    private fun at(row: Int, column: Int) = FourInARow.index(row, column)

    @Test
    fun `game type serialises as fourinarow on the wire ANDGAME-028`() {
        assertEquals("\"fourinarow\"", RoadtripJson.encodeToString(GameType.serializer(), GameType.FOURINAROW))
        assertEquals(GameType.FOURINAROW, RoadtripJson.decodeFromString(GameType.serializer(), "\"fourinarow\""))
    }

    @Test
    fun `grid is seven columns by six rows ANDGAME-028`() {
        assertEquals(7, FourInARow.COLUMNS)
        assertEquals(6, FourInARow.ROWS)
        val empty = board(emptyList())
        assertEquals(42, empty.cells.size)
        assertTrue(empty.cells.all { it == null })
        assertNull(empty.winningLine)
        assertNull(empty.winner)
    }

    @Test
    fun `fold drops each disc to the lowest empty row of its column ANDGAME-028`() {
        val b = board(drops(3, 3, 3, 0))
        assertEquals(0, b.cells[at(0, 3)])
        assertEquals(1, b.cells[at(1, 3)])
        assertEquals(0, b.cells[at(2, 3)])
        assertEquals(1, b.cells[at(0, 0)])
        assertNull(b.cells[at(3, 3)])
        assertEquals(4, b.cells.count { it != null })
    }

    @Test
    fun `fold alternates discs by move index creator first ANDGAME-028`() {
        val b = board(drops(0, 1, 2, 3))
        assertEquals(listOf(0, 1, 0, 1), (0..3).map { b.cells[at(0, it)] })
    }

    @Test
    fun `fold ignores the advisory row and places by gravity ANDGAME-028`() {
        val lying = buildJsonObject {
            put("column", 2)
            put("row", 5)
        }
        val b = board(listOf(lying))
        assertEquals(0, b.cells[at(0, 2)])
        assertNull(b.cells[at(5, 2)])
    }

    @Test
    fun `stateAt rebuilds the board at the replay cursor ANDGAME-028`() {
        val engine = ReplayEngine(GameType.FOURINAROW)
        val moves = drops(4, 4, 4)
        val partial = assertIs<BoardState.FourInARowBoard>(engine.stateAt(moves, 2))
        assertEquals(2, partial.cells.count { it != null })
        assertNull(partial.cells[at(2, 4)])
    }

    @Test
    fun `winning four is exposed for a horizontal line ANDGAME-028`() {
        // Red 0,1,2,3 on the bottom row; yellow stacks on top of them.
        val b = board(drops(0, 0, 1, 1, 2, 2, 3))
        assertEquals(listOf(at(0, 0), at(0, 1), at(0, 2), at(0, 3)), b.winningLine)
        assertEquals(0, b.winner)
    }

    @Test
    fun `winning four is exposed for a vertical line ANDGAME-028`() {
        val b = board(drops(5, 6, 5, 6, 5, 6, 5))
        assertEquals(listOf(at(0, 5), at(1, 5), at(2, 5), at(3, 5)), b.winningLine)
        assertEquals(0, b.winner)
    }

    @Test
    fun `winning four is exposed for a rising diagonal ANDGAME-028`() {
        // Red ends on (0,0) (1,1) (2,2) (3,3).
        val b = board(drops(0, 1, 1, 2, 2, 3, 2, 3, 3, 6, 3))
        assertEquals(listOf(at(0, 0), at(1, 1), at(2, 2), at(3, 3)), b.winningLine)
        assertEquals(0, b.winner)
    }

    @Test
    fun `winning four is exposed for a falling diagonal ANDGAME-028`() {
        // Red ends on (3,0) (2,1) (1,2) (0,3).
        val b = board(drops(3, 2, 2, 1, 1, 0, 1, 0, 0, 6, 0))
        assertEquals(listOf(at(3, 0), at(2, 1), at(1, 2), at(0, 3)), b.winningLine)
        assertEquals(0, b.winner)
    }

    @Test
    fun `winning line is null while the game is undecided ANDGAME-028`() {
        val b = board(drops(0, 1, 2, 4, 5, 6))
        assertNull(b.winningLine)
        assertNull(b.winner)
        assertNull(fourInARowWinningLine(List(42) { null }))
    }

    @Test
    fun `yellow can win too ANDGAME-028`() {
        val b = board(drops(0, 1, 0, 1, 0, 1, 6, 1))
        assertEquals(listOf(at(0, 1), at(1, 1), at(2, 1), at(3, 1)), b.winningLine)
        assertEquals(1, b.winner)
    }

    @Test
    fun `a full column is not playable ANDGAME-028`() {
        val b = board(drops(2, 2, 2, 2, 2, 2))
        assertFalse(FourInARow.isColumnPlayable(b, 2))
        assertTrue(FourInARow.isColumnPlayable(b, 3))
        assertFalse(FourInARow.isColumnPlayable(b, -1))
        assertFalse(FourInARow.isColumnPlayable(b, 7))
    }

    @Test
    fun `a column tap builds the column move payload ANDGAME-028`() {
        assertEquals(buildJsonObject { put("column", 4) }, FourInARow.move(4))
    }

    @Test
    fun `discs are red for the creator and yellow for the joiner ANDGAME-028`() {
        assertEquals(PieceInk.RED, BoardPieces.fourInARowInk(0))
        assertEquals(PieceInk.YELLOW, BoardPieces.fourInARowInk(1))
        assertNull(BoardPieces.fourInARowInk(null))
    }

    @Test
    fun `grid cells stay square and the whole grid fits both dimensions ANDGAME-028`() {
        // Wide tablet slot: height limits (600 / 6 = 100), width would allow 1000 / 7.
        assertEquals(100f, BoardMetrics.gridCellSide(1000f, 600f, columns = 7, rows = 6))
        // Tall phone slot: width limits (420 / 7 = 60).
        assertEquals(60f, BoardMetrics.gridCellSide(420f, 900f, columns = 7, rows = 6))
        // Fits both: grid size never exceeds the slot.
        val side = BoardMetrics.gridCellSide(700f, 590f, columns = 7, rows = 6)
        assertTrue(side * 7 <= 700f && side * 6 <= 590f)
        // Never negative, and a degenerate grid yields zero.
        assertEquals(0f, BoardMetrics.gridCellSide(-5f, 100f, columns = 7, rows = 6))
        assertEquals(0f, BoardMetrics.gridCellSide(100f, 100f, columns = 0, rows = 6))
    }

    @Test
    fun `disc is a fraction of its cell ANDGAME-028`() {
        assertEquals(0.8f, BoardMetrics.FOUR_IN_A_ROW_DISC_FRACTION)
        assertEquals(80f, BoardMetrics.fourInARowDiscDiameter(100f))
    }

    @Test
    fun `last move is the landing cell of the most recent drop ANDGAME-028`() {
        val moves = drops(3, 3, 1, 3)
        assertEquals(
            LastMoveHighlight.DiscCell(row = 2, column = 3),
            LastMove.lastMove(GameType.FOURINAROW, moves, 4),
        )
        // The cursor at 3 points at the drop into column 1, landing on the bottom row.
        assertEquals(
            LastMoveHighlight.DiscCell(row = 0, column = 1),
            LastMove.lastMove(GameType.FOURINAROW, moves, 3),
        )
        assertEquals(
            LastMoveHighlight.DiscCell(row = 1, column = 3),
            LastMove.lastMove(GameType.FOURINAROW, moves, 2),
        )
        assertNull(LastMove.lastMove(GameType.FOURINAROW, moves, 0))
        assertNull(LastMove.lastMove(GameType.FOURINAROW, moves, -1))
        assertEquals(LastMoveShade.MARK_FILL, LastMoveHighlight.DiscCell(0, 0).shade)
    }

    @Test
    fun `last move follows the replay session cursor ANDGAME-028`() {
        val session = ReplaySession(ReplayEngine(GameType.FOURINAROW), drops(0, 0), pinnedToLive = true)
        assertEquals(LastMoveHighlight.DiscCell(1, 0), session.lastMove(GameType.FOURINAROW))
        session.append(drop(6))
        assertEquals(LastMoveHighlight.DiscCell(0, 6), session.lastMove(GameType.FOURINAROW))
        session.seek(0)
        assertNull(session.lastMove(GameType.FOURINAROW))
    }
}
