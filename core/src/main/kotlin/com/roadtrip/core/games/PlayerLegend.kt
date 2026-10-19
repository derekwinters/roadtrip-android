package com.roadtrip.core.games

import com.roadtrip.core.api.Game
import com.roadtrip.core.api.GameStatus
import com.roadtrip.core.api.GameType

/**
 * One row of the player-identity legend (ANDGAME-020). A row is identified EITHER by a [mark]
 * (tic-tac-toe / ultimate) OR by an [ink] colour swatch (chess / checkers) — exactly one is set.
 *
 * @param mark      the board glyph this row identifies — 'X' or 'O'; null for colour rows.
 * @param playerId  the resolved profile id, or null when there is no such player yet
 *                  (e.g. an open game with no opponent).
 * @param name      the display name from the profiles cache, degrading to the neutral
 *                  "Someone" fallback ([GameLobbyLabeler.UNKNOWN_PLAYER]) when uncached/absent.
 * @param isYou     whether this mark belongs to the viewing profile.
 * @param isTurn    whether it is currently this player's turn (active game && `Game.turn`
 *                  matches) — the view boxes this row like an active board square.
 * @param ink       the side's piece ink, drawn as a swatch; null for mark rows.
 */
data class LegendEntry(
    val mark: Char?,
    val playerId: String?,
    val name: String,
    val isYou: Boolean,
    val isTurn: Boolean,
    val ink: PieceInk? = null,
)

/**
 * Pure mapping of a game's sides to player identities so the boards can show a persistent
 * "which side am I" legend with a turn highlight (ANDGAME-020). The creator always plays the
 * first-moving side: X (matching the move-index alternation in [ReplayEngine] `symbolFor`),
 * chess white, checkers red; the opponent plays O / black / black. Swatch inks come from the
 * same [BoardPieces] mapping that colours the pieces, so the legend cannot disagree with the
 * board. Resolution and fallback mirror [GameLobbyLabeler] so names read consistently with the
 * lobby. Hangman has no two-sided turn and gets no legend.
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
     * The legend for [game]: X/O rows for tic-tac-toe / ultimate, colour rows for chess /
     * checkers, and an empty list for hangman. `names` is a `profileId -> display name` map read
     * from the profiles cache; an uncached or absent player degrades to
     * [GameLobbyLabeler.UNKNOWN_PLAYER].
     */
    fun forGame(game: Game, myProfileId: String, names: Map<String, String>): List<LegendEntry> =
        when (game.gameType) {
            GameType.TICTACTOE, GameType.ULTIMATE -> listOf(
                entry(game.createdBy, myProfileId, names, game).copy(mark = 'X'),
                entry(game.opponentId, myProfileId, names, game).copy(mark = 'O'),
            )
            GameType.CHESS -> listOf(
                entry(game.createdBy, myProfileId, names, game).copy(ink = BoardPieces.chessInk(CHESS_WHITE)),
                entry(game.opponentId, myProfileId, names, game).copy(ink = BoardPieces.chessInk(CHESS_BLACK)),
            )
            GameType.CHECKERS -> listOf(
                entry(game.createdBy, myProfileId, names, game).copy(ink = BoardPieces.checkersInk(CHECKERS_RED)),
                entry(game.opponentId, myProfileId, names, game).copy(ink = BoardPieces.checkersInk(CHECKERS_BLACK)),
            )
            GameType.HANGMAN -> emptyList()
        }

    // Piece codes whose ink identifies each side (ANDGAME-013 codes; see BoardPieces).
    private const val CHESS_WHITE = "w"
    private const val CHESS_BLACK = "b"
    private const val CHECKERS_RED = "r"
    private const val CHECKERS_BLACK = "w"

    private fun entry(
        playerId: String?,
        myProfileId: String,
        names: Map<String, String>,
        game: Game,
    ): LegendEntry {
        val name = playerId?.let { names[it] }?.takeIf { it.isNotBlank() }
            ?: GameLobbyLabeler.UNKNOWN_PLAYER
        return LegendEntry(
            mark = null,
            playerId = playerId,
            name = name,
            isYou = playerId != null && playerId == myProfileId,
            isTurn = game.status == GameStatus.ACTIVE && playerId != null && game.turn == playerId,
        )
    }
}
