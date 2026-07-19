package com.roadtrip.core.games

import com.roadtrip.core.api.Game
import com.roadtrip.core.api.GameStatus
import com.roadtrip.core.api.GameType

/**
 * One row of the mark-to-player legend for tic-tac-toe / ultimate boards (ANDGAME-020).
 *
 * @param mark      the board glyph this row identifies — 'X' or 'O'.
 * @param playerId  the resolved profile id, or null when there is no such player yet
 *                  (e.g. an open game with no opponent).
 * @param name      the display name from the profiles cache, degrading to the neutral
 *                  "Someone" fallback ([GameLobbyLabeler.UNKNOWN_PLAYER]) when uncached/absent.
 * @param isYou     whether this mark belongs to the viewing profile.
 * @param isTurn    whether it is currently this player's turn (active game && `Game.turn`
 *                  matches) — the view boxes this row like an active board square.
 */
data class LegendEntry(
    val mark: Char,
    val playerId: String?,
    val name: String,
    val isYou: Boolean,
    val isTurn: Boolean,
)

/**
 * Pure mapping of a game's marks to player identities so the tic-tac-toe / ultimate boards can
 * show a persistent "which mark am I" legend with a turn highlight (ANDGAME-020). X is always
 * the creator (first player); O is the opponent, matching the deterministic move-index
 * alternation in [ReplayEngine] `symbolFor`. Resolution and fallback mirror [GameLobbyLabeler]
 * so names read consistently with the lobby.
 */
object PlayerLegend {
    /**
     * Minimum available width (dp) at which the legend sits beside the board rather than below
     * it. Matches the ANDGAME-012 tablet threshold (`WindowSizeClass` medium/expanded ≈ 600dp).
     */
    const val BESIDE_MIN_WIDTH_DP: Int = 600

    /** Legend beside the board on tablet/wide, below it on phone (ANDGAME-020). */
    fun placeBeside(availableWidthDp: Int): Boolean = availableWidthDp >= BESIDE_MIN_WIDTH_DP

    /**
     * The X/O legend for [game], or an empty list for game types with no X/O marks (chess,
     * checkers, hangman). `names` is a `profileId -> display name` map read from the profiles
     * cache; an uncached or absent player degrades to [GameLobbyLabeler.UNKNOWN_PLAYER].
     */
    fun forGame(game: Game, myProfileId: String, names: Map<String, String>): List<LegendEntry> {
        if (game.gameType != GameType.TICTACTOE && game.gameType != GameType.ULTIMATE) {
            return emptyList()
        }
        return listOf(
            entry('X', game.createdBy, myProfileId, names, game),
            entry('O', game.opponentId, myProfileId, names, game),
        )
    }

    private fun entry(
        mark: Char,
        playerId: String?,
        myProfileId: String,
        names: Map<String, String>,
        game: Game,
    ): LegendEntry {
        val name = playerId?.let { names[it] }?.takeIf { it.isNotBlank() }
            ?: GameLobbyLabeler.UNKNOWN_PLAYER
        return LegendEntry(
            mark = mark,
            playerId = playerId,
            name = name,
            isYou = playerId != null && playerId == myProfileId,
            isTurn = game.status == GameStatus.ACTIVE && playerId != null && game.turn == playerId,
        )
    }
}
