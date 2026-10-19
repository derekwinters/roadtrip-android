package com.roadtrip.core.games

import com.roadtrip.core.api.GameType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The move staged behind an explicit Confirm (ANDGAME-022) on a `{from, to}` piece board, shown so
 * the player sees what Confirm will do (ANDGAME-026). Both squares carry
 * [StagedMoveShade.STAGED_MOVE]; the board position itself is never altered (no piece preview).
 */
data class StagedMoveHighlight(val from: String, val to: String) {
    /** The staged shade for [square] when it is the staged origin or target, else null. */
    fun shadeFor(square: String): StagedMoveShade? =
        if (square == from || square == to) StagedMoveShade.STAGED_MOVE else null
}

/**
 * The staged-move FILL role (ANDGAME-026): its own role, never one of the ANDGAME-024
 * [LastMoveShade]s, so a staged move cannot be mistaken for the opponent's last move. A fill,
 * never a border, so it also stays distinct from the `3.dp` selected-square border.
 */
enum class StagedMoveShade : BoardSquareShade { STAGED_MOVE }

/**
 * Pure presentation seam for a staged move (ANDGAME-026): the highlight, the confirmation prompt,
 * the per-square shade and the selection to keep. Compose only renders what this returns.
 */
object StagedMove {
    /** The prompt for boards whose tapped target is itself the move (and for unreadable moves). */
    const val GENERIC_PROMPT: String = "Confirm your move?"

    /** Highlight for a staged chess/checkers `{from, to}` move; null for other games or nothing staged. */
    fun highlight(gameType: GameType, staged: JsonElement?): StagedMoveHighlight? {
        if (gameType != GameType.CHESS && gameType != GameType.CHECKERS) return null
        val move = staged as? JsonObject ?: return null
        val from = move.str("from") ?: return null
        val to = move.str("to") ?: return null
        return StagedMoveHighlight(from, to)
    }

    /** "Confirm e2 → e4?" for a staged piece move, otherwise [GENERIC_PROMPT]. */
    fun prompt(gameType: GameType, staged: JsonElement?): String =
        highlight(gameType, staged)?.let { "Confirm ${it.from} → ${it.to}?" } ?: GENERIC_PROMPT

    /**
     * The one shade for [square]: the staged shade on the staged squares, else the ANDGAME-024
     * last-move shade, else none.
     */
    fun shadeAt(
        square: String,
        staged: StagedMoveHighlight?,
        lastMove: LastMoveHighlight.PieceMove?,
    ): BoardSquareShade? = staged?.shadeFor(square) ?: lastMove?.shadeFor(square)

    /**
     * The selected square after the second tap requests a move from [from]: kept on the origin while
     * the move waits for Confirm, cleared when it submits at once.
     */
    fun selectionAfterRequest(from: String, confirmRequired: Boolean): String? =
        if (confirmRequired) from else null

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
