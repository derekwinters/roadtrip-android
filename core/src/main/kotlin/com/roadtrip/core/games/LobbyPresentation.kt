package com.roadtrip.core.games

import com.roadtrip.core.api.Game
import com.roadtrip.core.api.GameType

/**
 * Pure labelling for lobby rows so the composable stays thin (ANDGAME-009). Callers pass the
 * already-localised game-type label (e.g. "Chess") plus a `profileId -> display name` map read
 * from the profiles cache; resolution and fallback live here where they can be unit tested.
 */
object GameLobbyLabeler {
    /** Neutral stand-in when the creator/opponent profile isn't cached yet. */
    const val UNKNOWN_PLAYER = "Someone"

    /**
     * Max characters of the masked hangman progress shown in a lobby title before it's
     * truncated with a trailing ellipsis, so a long phrase never blows out the row
     * (ANDGAME-019). Includes the ellipsis, so the displayed suffix is never longer than this.
     */
    const val HANGMAN_MASK_CAP = 20

    /**
     * Title for a game created by someone else — "Derek's Chess". The game type stays
     * identifiable; an uncached creator degrades to "Someone's Chess" rather than a raw id.
     * Hangman rows carry the masked word progress (ANDGAME-019).
     */
    fun creatorTitle(game: Game, typeLabel: String, names: Map<String, String>): String {
        val who = names[game.createdBy]?.takeIf { it.isNotBlank() } ?: UNKNOWN_PLAYER
        return withHangmanProgress(game, "$who's $typeLabel")
    }

    /**
     * Title for one of the profile's own active games: prefer the opponent ("Chess vs Theo"),
     * falling back to "Your Chess" when there is no opponent yet or it isn't cached. Hangman
     * rows carry the masked word progress (ANDGAME-019).
     */
    fun myGameTitle(game: Game, myProfileId: String, typeLabel: String, names: Map<String, String>): String {
        val opponentId = if (game.createdBy == myProfileId) game.opponentId else game.createdBy
        val opponent = opponentId?.let { names[it] }?.takeIf { it.isNotBlank() }
        val base = if (opponent != null) "$typeLabel vs $opponent" else "Your $typeLabel"
        return withHangmanProgress(game, base)
    }

    /**
     * Appends the server-redacted masked progress to a hangman title (ANDGAME-019). The mask is
     * used verbatim — already redacted server-side (`roadtrip-backend` GAME-001), so nothing here
     * reveals unguessed letters. No-op for non-hangman games or when the display is absent/blank
     * (open/unstarted games). Truncated at [HANGMAN_MASK_CAP] with a trailing ellipsis when longer.
     */
    private fun withHangmanProgress(game: Game, base: String): String {
        if (game.gameType != GameType.HANGMAN) return base
        val mask = game.hangmanDisplay?.takeIf { it.isNotBlank() } ?: return base
        val shown =
            if (mask.length > HANGMAN_MASK_CAP) mask.take(HANGMAN_MASK_CAP - 1) + "…" else mask
        return "$base — $shown"
    }
}

/** Layout constants for the lobby list. */
object GamesLobbyLayout {
    /**
     * Bottom inset (dp) reserved below the lobby list so the last row's trailing action never
     * scrolls under the floating "new game" button (ANDGAME-011). A Material FAB is 56dp plus a
     * 16dp margin; 88dp clears it with headroom.
     */
    const val FAB_CLEARANCE_DP: Int = 88
}
