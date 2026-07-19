package com.roadtrip.core.games

import com.roadtrip.core.api.Game

/**
 * Pure labelling for lobby rows so the composable stays thin (ANDGAME-009). Callers pass the
 * already-localised game-type label (e.g. "Chess") plus a `profileId -> display name` map read
 * from the profiles cache; resolution and fallback live here where they can be unit tested.
 */
object GameLobbyLabeler {
    /** Neutral stand-in when the creator/opponent profile isn't cached yet. */
    const val UNKNOWN_PLAYER = "Someone"

    /**
     * Title for a game created by someone else — "Derek's Chess". The game type stays
     * identifiable; an uncached creator degrades to "Someone's Chess" rather than a raw id.
     */
    fun creatorTitle(game: Game, typeLabel: String, names: Map<String, String>): String {
        val who = names[game.createdBy]?.takeIf { it.isNotBlank() } ?: UNKNOWN_PLAYER
        return "$who's $typeLabel"
    }

    /**
     * Title for one of the profile's own active games: prefer the opponent ("Chess vs Theo"),
     * falling back to "Your Chess" when there is no opponent yet or it isn't cached.
     */
    fun myGameTitle(game: Game, myProfileId: String, typeLabel: String, names: Map<String, String>): String {
        val opponentId = if (game.createdBy == myProfileId) game.opponentId else game.createdBy
        val opponent = opponentId?.let { names[it] }?.takeIf { it.isNotBlank() }
        return if (opponent != null) "$typeLabel vs $opponent" else "Your $typeLabel"
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
