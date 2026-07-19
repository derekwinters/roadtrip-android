package com.roadtrip.core.games

import com.roadtrip.core.api.Game
import com.roadtrip.core.api.GameStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Hangman is driven by the backend's viewer-aware engine `view`, not by folding the events
 * feed (ANDGAME-016). The backend redacts the secret word to `•••` for ongoing games
 * (`roadtrip-backend` GAME-014), so a local fold over `options.word` would count every real
 * guess as wrong and garble the mask. `GET /api/games/{id}` returns the already-masked
 * `display`, the `guessed` letters, and the `wrong`/`max_wrong` counts for the requesting
 * viewer; we render those verbatim.
 */
object HangmanView {
    /**
     * Maps a hangman `view` object (from `Game.view`) into a [BoardState.HangmanBoard].
     * Returns null when the element is absent or not an object (e.g. a non-hangman game, or
     * a server that has not attached a view yet), so callers can fall back gracefully.
     *
     * Expected shape:
     * `{ display, guessed: string[], wrong, max_wrong, turn, players:{setter,guesser}, word? }`
     */
    fun toBoard(view: JsonElement?): BoardState.HangmanBoard? {
        val obj = view as? JsonObject ?: return null
        val display = obj["display"]?.jsonPrimitive?.contentOrNull ?: return null
        val guessed = (obj["guessed"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.firstOrNull()?.uppercaseChar() }
            ?.toSet()
            ?: emptySet()
        val wrong = obj["wrong"]?.jsonPrimitive?.intOrNull ?: 0
        val maxWrong = obj["max_wrong"]?.jsonPrimitive?.intOrNull ?: 6
        // display already has unguessed = '_' and word boundaries as ' '; render it verbatim
        // (uppercasing only affects revealed letters — '_' and ' ' are unchanged).
        return BoardState.HangmanBoard(
            masked = display.uppercase(),
            guessed = guessed,
            wrongCount = wrong,
            maxWrong = maxWrong,
        )
    }
}

/**
 * Viewer-relative status for a hangman board (ANDGAME-017). Hangman has exactly one guesser
 * (the joining opponent) and one setter (the creator); the setter never takes a turn, so the
 * board must never show a generic "waiting for the other player" — there is no second guesser.
 */
enum class HangmanBoardStatus {
    OPEN, // waiting for an opponent to join
    YOUR_TURN, // the viewer is the guesser and it is their move
    WAITING_FOR_GUESSER, // the viewer is the setter; the guesser is thinking
    YOU_WON,
    YOU_LOST,
    SPECTATING, // a non-participant watching the live game
    FINISHED, // a non-participant, game over
    ABANDONED,
}

/** Computes the [HangmanBoardStatus] a given [profileId] should see for [game]. */
fun hangmanBoardStatus(game: Game, profileId: String): HangmanBoardStatus {
    val isParticipant = game.createdBy == profileId || game.opponentId == profileId
    return when (game.status) {
        GameStatus.OPEN -> HangmanBoardStatus.OPEN
        GameStatus.ABANDONED -> HangmanBoardStatus.ABANDONED
        GameStatus.FINISHED -> when {
            !isParticipant -> HangmanBoardStatus.FINISHED
            game.winnerId == profileId -> HangmanBoardStatus.YOU_WON
            else -> HangmanBoardStatus.YOU_LOST
        }
        GameStatus.ACTIVE -> when {
            !isParticipant -> HangmanBoardStatus.SPECTATING
            // In hangman the turn is always the guesser; the only other participant is the
            // setter, who never guesses.
            game.turn == profileId -> HangmanBoardStatus.YOUR_TURN
            else -> HangmanBoardStatus.WAITING_FOR_GUESSER
        }
    }
}
