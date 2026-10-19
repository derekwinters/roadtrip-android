package com.roadtrip.core.games

import com.roadtrip.core.api.EventDto
import com.roadtrip.core.api.Game
import com.roadtrip.core.api.GameType

/**
 * How the open board folds a long-poll batch of game events into its [Game] (ANDGAME-005,
 * ANDGAME-027). Checkers turns cannot be read from the event payload (a capture may keep the turn
 * with the jumper), so for checkers the board re-fetches the game after a batch and folds the
 * batch with that authoritative game instead of folding each event on arrival.
 */
object LiveGameFold {
    /** True when the board must re-fetch the game and fold the batch with it. */
    fun refetchesAfterEvents(gameType: GameType): Boolean = gameType == GameType.CHECKERS

    /** Folds [events] in order, each with [authoritative] (null when the re-fetch failed). */
    fun foldBatch(game: Game, events: List<EventDto>, authoritative: Game?): Game =
        events.fold(game) { current, event ->
            LobbyReducer.applyEvent(listOf(current), event, authoritative).single()
        }
}
