package com.roadtrip.core.games

import com.roadtrip.core.api.GameMode
import com.roadtrip.core.api.GameType
import kotlin.test.Test
import kotlin.test.assertEquals

class GameChoicesTest {
    // covers: AND-013
    // covers: ANDGAME-002
    @Test
    fun `game type choices are ordered with stable labels AND-013`() {
        assertEquals(
            listOf(
                GameType.CHESS to "Chess",
                GameType.CHECKERS to "Checkers",
                GameType.TICTACTOE to "Tic-tac-toe",
                GameType.ULTIMATE to "Ultimate TTT",
                GameType.HANGMAN to "Hangman",
            ),
            GameTypeChoices.all.map { it.type to it.label },
        )
    }

    // covers: AND-013
    @Test
    fun `every game type has exactly one choice and labelFor matches AND-013`() {
        assertEquals(GameType.entries.toSet(), GameTypeChoices.all.map { it.type }.toSet())
        assertEquals(GameType.entries.size, GameTypeChoices.all.size)
        for (type in GameType.entries) {
            assertEquals(
                GameTypeChoices.all.first { it.type == type }.label,
                GameTypeChoices.labelFor(type),
            )
        }
    }

    // covers: AND-013
    // covers: ANDGAME-002
    @Test
    fun `game mode choices are Open then Challenge with stable labels AND-013`() {
        assertEquals(
            listOf(
                GameMode.OPEN to "Open to anyone",
                GameMode.CHALLENGE to "Challenge",
            ),
            GameModeChoices.all.map { it.mode to it.label },
        )
        assertEquals("Open to anyone", GameModeChoices.labelFor(GameMode.OPEN))
        assertEquals("Challenge", GameModeChoices.labelFor(GameMode.CHALLENGE))
    }
}
