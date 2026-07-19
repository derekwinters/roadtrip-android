package com.roadtrip.core.games

import com.roadtrip.core.api.GameType
import com.roadtrip.core.games.GameEndControls.gameEndControl
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ANDGAME-018 (backend GAME-015): only a hangman creator/setter may end a hangman game;
 * a hangman guesser sees no end/resign control. Non-hangman games keep "Resign" for any
 * active-game participant.
 */
class GameEndControlTest {

    // covers: ANDGAME-018
    @Test
    fun `hangman creator sees End game control ANDGAME-018`() {
        val control = gameEndControl(
            gameType = GameType.HANGMAN,
            isCreator = true,
            isParticipant = true,
            isActive = true,
        )
        assertEquals(EndControl.END_GAME, control)
    }

    // covers: ANDGAME-018
    @Test
    fun `hangman guesser sees no end control ANDGAME-018`() {
        val control = gameEndControl(
            gameType = GameType.HANGMAN,
            isCreator = false,
            isParticipant = true,
            isActive = true,
        )
        assertEquals(EndControl.NONE, control)
    }

    // covers: ANDGAME-018
    @Test
    fun `non-hangman participant sees Resign ANDGAME-018`() {
        for (type in GameType.entries.filter { it != GameType.HANGMAN }) {
            // creator side
            assertEquals(
                EndControl.RESIGN,
                gameEndControl(type, isCreator = true, isParticipant = true, isActive = true),
                "creator of $type should see Resign",
            )
            // opponent side
            assertEquals(
                EndControl.RESIGN,
                gameEndControl(type, isCreator = false, isParticipant = true, isActive = true),
                "opponent of $type should see Resign",
            )
        }
    }

    // covers: ANDGAME-018
    @Test
    fun `non-participant spectator sees no control ANDGAME-018`() {
        for (type in GameType.entries) {
            assertEquals(
                EndControl.NONE,
                gameEndControl(type, isCreator = false, isParticipant = false, isActive = true),
                "spectator of $type should see no control",
            )
        }
    }

    // covers: ANDGAME-018
    @Test
    fun `no control once the game is not active ANDGAME-018`() {
        // Even the hangman creator loses the control on a finished/abandoned game.
        assertEquals(
            EndControl.NONE,
            gameEndControl(GameType.HANGMAN, isCreator = true, isParticipant = true, isActive = false),
        )
        assertEquals(
            EndControl.NONE,
            gameEndControl(GameType.CHESS, isCreator = false, isParticipant = true, isActive = false),
        )
    }
}
