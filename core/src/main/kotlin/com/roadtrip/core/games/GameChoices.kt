package com.roadtrip.core.games

import com.roadtrip.core.api.GameMode
import com.roadtrip.core.api.GameType

/** One game-type option for the single-choice game picker, with its display label (AND-013). */
data class GameTypeChoice(val type: GameType, val label: String)

/** One game-mode option for the single-choice mode picker, with its display label (AND-013). */
data class GameModeChoice(val mode: GameMode, val label: String)

/**
 * The ordered game-type options for the new-game dialog's single-choice picker, rendered as
 * wrapping single-select choice chips (AND-013, ANDGAME-002). These labels are the single source
 * of truth for a game type's display name across the app (lobby titles, board/replay headers).
 */
object GameTypeChoices {
    val all: List<GameTypeChoice> = listOf(
        GameTypeChoice(GameType.CHESS, "Chess"),
        GameTypeChoice(GameType.CHECKERS, "Checkers"),
        GameTypeChoice(GameType.TICTACTOE, "Tic-tac-toe"),
        GameTypeChoice(GameType.ULTIMATE, "Ultimate TTT"),
        GameTypeChoice(GameType.HANGMAN, "Hangman"),
    )

    /** Display label for [type]; the one place game-type names are spelled out. */
    fun labelFor(type: GameType): String = all.first { it.type == type }.label
}

/**
 * The ordered game-mode options (open to anyone / challenge) for the new-game dialog's
 * single-choice picker, rendered as wrapping single-select choice chips (AND-013, ANDGAME-002).
 */
object GameModeChoices {
    val all: List<GameModeChoice> = listOf(
        GameModeChoice(GameMode.OPEN, "Open to anyone"),
        GameModeChoice(GameMode.CHALLENGE, "Challenge"),
    )

    /** Display label for [mode]. */
    fun labelFor(mode: GameMode): String = all.first { it.mode == mode }.label
}
