package com.roadtrip.core.games

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Pure geometry and move shape for the Four in a Row board (ANDGAME-028, backend GAME-020). The
 * server arbitrates every move; nothing here re-checks the rules. Cells are indexed row-major
 * from the BOTTOM row (row 0), matching the wire `view.board[row][column]`.
 */
object FourInARow {
    const val COLUMNS: Int = 7
    const val ROWS: Int = 6

    /** Flat cell index of ([row], [column]); row 0 is the bottom row. */
    fun index(row: Int, column: Int): Int = row * COLUMNS + column

    /** A tapped column's move payload: `{"column": n}`. */
    fun move(column: Int): JsonObject = buildJsonObject { put("column", column) }

    /** True when [column] is on the board and still has an empty cell to drop into. */
    fun isColumnPlayable(board: BoardState.FourInARowBoard, column: Int): Boolean =
        column in 0 until COLUMNS && board.cells[index(ROWS - 1, column)] == null
}

/**
 * Directions a line of four can run, as (row step, column step): horizontal, vertical, rising
 * diagonal, falling diagonal. Lines read left to right (bottom to top for vertical).
 */
private val FOUR_DIRECTIONS = listOf(0 to 1, 1 to 0, 1 to 1, -1 to 1)

/**
 * The four cell indices of a completed line, or null while undecided (ANDGAME-028). Derived from
 * the board alone, mirroring [tttWinningLine] (ANDGAME-014) — the server sends no win line.
 */
fun fourInARowWinningLine(cells: List<Int?>): List<Int>? {
    for (row in 0 until FourInARow.ROWS) {
        for (column in 0 until FourInARow.COLUMNS) {
            val disc = cells[FourInARow.index(row, column)] ?: continue
            for ((dr, dc) in FOUR_DIRECTIONS) {
                val line = (0 until 4).map { step -> (row + dr * step) to (column + dc * step) }
                val onBoard = line.all { (r, c) -> r in 0 until FourInARow.ROWS && c in 0 until FourInARow.COLUMNS }
                if (onBoard && line.all { (r, c) -> cells[FourInARow.index(r, c)] == disc }) {
                    return line.map { (r, c) -> FourInARow.index(r, c) }
                }
            }
        }
    }
    return null
}
