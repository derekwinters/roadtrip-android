package com.roadtrip.core.games

import com.roadtrip.core.api.Game
import com.roadtrip.core.api.GameStatus
import com.roadtrip.core.api.GameType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** What a tap on a `{from, to}` piece board does: change the selection, or request a move. */
sealed interface PieceTap {
    data class Select(val square: String?) : PieceTap

    data class Move(val from: String, val to: String) : PieceTap {
        fun json(): JsonObject = buildJsonObject {
            put("from", from)
            put("to", to)
        }
    }
}

/**
 * Checkers multi-jump continuation as one continued turn (ANDGAME-027). The forced piece is read
 * from the server's checkers view (`must_continue_from`), never recomputed from local rules: the
 * backend is the rules authority and the `game.move` payload does not carry the continuation.
 */
object CheckersContinuation {
    const val KEEP_JUMPING_STATUS: String = "Keep jumping"

    /**
     * The square whose piece must keep jumping, from the checkers view's `must_continue_from`
     * (`[row, col]` → file `'a' + col`, rank `row + 1`), or null when there is no continuation,
     * no view, or the game is not checkers.
     */
    fun square(game: Game?): String? {
        if (game?.gameType != GameType.CHECKERS) return null
        val rc = (game.view as? JsonObject)?.get("must_continue_from") as? JsonArray ?: return null
        val row = rc.getOrNull(0)?.jsonPrimitive?.intOrNull ?: return null
        val col = rc.getOrNull(1)?.jsonPrimitive?.intOrNull ?: return null
        if (row !in 0..7 || col !in 0..7) return null
        return "${'a' + col}${row + 1}"
    }

    /** The forced square when [profileId] is the jumper on an active game, else null. */
    fun forcedFrom(game: Game?, profileId: String): String? {
        if (game == null || game.status != GameStatus.ACTIVE || game.turn != profileId) return null
        return square(game)
    }

    /** "Keep jumping" while [profileId] must continue a multi-jump, else null (normal status). */
    fun status(game: Game?, profileId: String): String? =
        forcedFrom(game, profileId)?.let { KEEP_JUMPING_STATUS }

    /** The square drawn as selected: the forced piece wins over the local selection. */
    fun selection(selected: String?, forcedFrom: String?): String? = forcedFrom ?: selected

    /**
     * The decision for a tap on [tapped]. During a continuation the forced piece cannot be
     * deselected or swapped, and any other square is the next hop from it. Otherwise: tap a piece
     * to select it, tap it again to deselect, tap another square to move there.
     */
    fun tap(selected: String?, tapped: String, occupied: Boolean, forcedFrom: String?): PieceTap {
        if (forcedFrom != null) {
            return if (tapped == forcedFrom) PieceTap.Select(forcedFrom) else PieceTap.Move(forcedFrom, tapped)
        }
        return when (selected) {
            null -> PieceTap.Select(if (occupied) tapped else null)
            tapped -> PieceTap.Select(null)
            else -> PieceTap.Move(selected, tapped)
        }
    }
}
