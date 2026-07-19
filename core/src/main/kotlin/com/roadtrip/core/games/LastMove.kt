package com.roadtrip.core.games

import com.roadtrip.core.api.GameType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The SINGLE most-recent move to highlight on a board (ANDGAME-024). Exactly one move is ever
 * highlighted and it resets on each new move (no trail): callers resolve it from the move stream
 * and the current cursor, so it is naturally reset-on-move and replay/spectate-aware.
 *
 * Per game type:
 *  - mark games (tic-tac-toe / ultimate): the last-placed cell ([MarkCell] / [UltimateCell]).
 *  - piece games (chess / checkers): the one move's origin + destination ([PieceMove]); checkers
 *    also carries the captured/jumped squares.
 *  - hangman: the most recent guessed letter ([HangmanGuess]).
 */
sealed class LastMoveHighlight {
    /** Tic-tac-toe: index 0..8 of the last-placed cell. */
    data class MarkCell(val cellIndex: Int) : LastMoveHighlight()

    /** Ultimate: the sub-board (0..8) and cell (0..8) of the last-placed mark. */
    data class UltimateCell(val board: Int, val cell: Int) : LastMoveHighlight()

    /**
     * Chess/checkers: the one last move. [origin] greys darker than other squares,
     * [destination] gets the dim accent tint; [captured] squares (checkers jumps) are marked.
     */
    data class PieceMove(
        val origin: String,
        val destination: String,
        val captured: List<String> = emptyList(),
    ) : LastMoveHighlight() {
        /**
         * The shade role a given square earns from this move, or null when it is not part of it.
         * Destination wins over origin (they never coincide); captured squares are their own role.
         */
        fun shadeFor(square: String): LastMoveShade? = when (square) {
            destination -> LastMoveShade.PIECE_DESTINATION
            origin -> LastMoveShade.PIECE_ORIGIN
            in captured -> LastMoveShade.PIECE_CAPTURE
            else -> null
        }
    }

    /** Hangman: the most recent guessed letter (uppercased). */
    data class HangmanGuess(val letter: Char) : LastMoveHighlight()
}

/**
 * Presentation-layer shade roles for the last-move highlight (ANDGAME-024), mirroring the
 * [PieceInk] pattern: the thin Compose layer maps each role to a concrete FILL color. Every role
 * is a fill/shade, never a border, so it stays visually distinct from the ANDGAME-020/012 `3.dp`
 * `colorScheme.primary` selected/on-turn border and the ANDGAME-004 dictated-sub-board highlight.
 */
enum class LastMoveShade {
    /** Mark games: the background fill of the last-placed cell. */
    MARK_FILL,

    /** Piece games: the origin/previous square, greyed darker than other squares. */
    PIECE_ORIGIN,

    /** Piece games: the destination square, a dim accent tint. */
    PIECE_DESTINATION,

    /** Checkers: a captured/jumped square. */
    PIECE_CAPTURE,
}

/**
 * Pure resolver for the last-move highlight (ANDGAME-024). Deterministic from the move payloads
 * and the cursor alone; keeps the "which one move, and where" decision JVM-testable.
 */
object LastMove {
    /**
     * The highlight for the move that produced the position at [index] (i.e. `moves[index - 1]`),
     * or null when [index] <= 0 (initial position — nothing has moved yet) or the stream is empty.
     * Following the cursor gives reset-on-move and replay/spectate behavior for free.
     */
    fun lastMove(gameType: GameType, moves: List<JsonObject>, index: Int): LastMoveHighlight? {
        val at = index - 1
        if (at < 0 || at >= moves.size) return null
        val move = moves[at]
        return when (gameType) {
            GameType.CHESS -> pieceMove(move)
            GameType.CHECKERS -> pieceMove(move)
            GameType.TICTACTOE -> move.int("cell")?.let { LastMoveHighlight.MarkCell(it) }
            GameType.ULTIMATE -> {
                val board = move.int("board") ?: return null
                val cell = move.int("cell") ?: return null
                LastMoveHighlight.UltimateCell(board, cell)
            }
            GameType.HANGMAN -> move.letter()?.let { LastMoveHighlight.HangmanGuess(it) }
        }
    }

    /**
     * Hangman resolves its board from the server `view`, not a local fold (ANDGAME-016), so the
     * screen can hand the most recent guessed [letter] straight in. Null letter → no highlight.
     */
    fun lastHangmanGuess(letter: Char?): LastMoveHighlight.HangmanGuess? =
        letter?.uppercaseChar()?.let { LastMoveHighlight.HangmanGuess(it) }

    private fun pieceMove(move: JsonObject): LastMoveHighlight.PieceMove? {
        val from = move.str("from") ?: return null
        val to = move.str("to") ?: return null
        val captured = (move["captured"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()
        return LastMoveHighlight.PieceMove(from, to, captured)
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.letter(): Char? = str("letter")?.firstOrNull()?.uppercaseChar()
}
