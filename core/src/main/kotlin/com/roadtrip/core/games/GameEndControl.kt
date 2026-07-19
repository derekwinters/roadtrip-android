package com.roadtrip.core.games

import com.roadtrip.core.api.GameType

/**
 * Which end-of-game affordance (if any) a viewer of a board should see.
 *
 * - [NONE]     no control (spectator, finished game, or a hangman guesser).
 * - [RESIGN]   the plain "Resign" control shown to any participant of a non-hangman game.
 * - [END_GAME] the hangman "End game" control, shown only to the setter/creator (host).
 */
enum class EndControl { NONE, RESIGN, END_GAME }

/**
 * Pure decision seam for the board's end-of-game control (ANDGAME-018, backend GAME-015).
 *
 * In hangman the creator (players[0]) is the word-setter/host and the opponent (players[1])
 * is the guesser. A guesser resigning would abandon the whole game, so the guesser must not
 * see any end/resign control — only the creator may end a hangman game, via "End game" (which
 * the UI wires to the existing resign endpoint; the backend treats it as abandoning the game).
 * All other game types keep the plain "Resign" control for any active-game participant.
 *
 * Kept framework-free so BoardScreen stays thin and this is JVM-testable.
 */
object GameEndControls {
    fun gameEndControl(
        gameType: GameType,
        isCreator: Boolean,
        isParticipant: Boolean,
        isActive: Boolean,
    ): EndControl {
        if (!isActive) return EndControl.NONE
        return when (gameType) {
            GameType.HANGMAN -> if (isCreator) EndControl.END_GAME else EndControl.NONE
            else -> if (isParticipant) EndControl.RESIGN else EndControl.NONE
        }
    }
}
