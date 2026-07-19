package com.roadtrip.core.games

import com.roadtrip.core.api.EventDto
import com.roadtrip.core.api.Game
import com.roadtrip.core.api.GameMode
import com.roadtrip.core.api.GameResultKind
import com.roadtrip.core.api.GameStatus
import com.roadtrip.core.api.GameType
import com.roadtrip.core.api.RoadtripJson
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

data class MyGame(val game: Game, val myTurn: Boolean)

data class LobbyState(
    val openGames: List<Game>,
    val myGames: List<MyGame>,
    val incomingChallenges: List<Game>,
)

/**
 * Lobby read model: open games to join, my active games with turn indicator, and incoming
 * challenges — reduced from GET /api/games plus live `game.*` events (ANDGAME-001).
 */
object LobbyReducer {
    fun reduce(games: List<Game>, myProfileId: String): LobbyState {
        val openGames = games.filter {
            it.status == GameStatus.OPEN && it.mode == GameMode.OPEN && it.createdBy != myProfileId
        }
        val myGames = games
            .filter {
                it.status == GameStatus.ACTIVE &&
                    (it.createdBy == myProfileId || it.opponentId == myProfileId)
            }
            .map { MyGame(it, myTurn = it.turn == myProfileId) }
        val incomingChallenges = games.filter {
            it.status == GameStatus.OPEN && it.mode == GameMode.CHALLENGE && it.invitedProfileId == myProfileId
        }
        return LobbyState(openGames, myGames, incomingChallenges)
    }

    /** Folds a `game.*` event from the feed into the games list without a full reload. */
    fun applyEvent(games: List<Game>, event: EventDto): List<Game> {
        val payload = event.payload
        fun str(key: String): String? = payload[key]?.jsonPrimitive?.contentOrNull
        val gameId = str("game_id") ?: return games

        return when (event.type) {
            "game.created" -> {
                if (games.any { it.id == gameId }) return games
                val actor = event.actorId ?: return games
                val gameType = str("game_type")
                    ?.let { name -> GameType.entries.firstOrNull { serialName(it) == name } }
                    ?: return games
                val mode = if (str("mode") == "challenge") GameMode.CHALLENGE else GameMode.OPEN
                games + Game(
                    id = gameId,
                    gameType = gameType,
                    mode = mode,
                    status = GameStatus.OPEN,
                    createdBy = actor,
                    invitedProfileId = str("invited_profile_id"),
                    createdAt = event.clientTs,
                )
            }
            "game.joined" -> games.update(gameId) {
                val joiner = str("profile_id")
                it.copy(
                    status = GameStatus.ACTIVE,
                    opponentId = joiner,
                    // Hangman: the creator set the secret word and never guesses — the turn
                    // goes to the joining guesser and stays there (ANDGAME-015, backend
                    // GAME-013). Other games: the creator opens play (X/white = creator).
                    turn = if (it.gameType == GameType.HANGMAN) joiner else it.createdBy,
                )
            }
            "game.move" -> games.update(gameId) { game ->
                game.copy(
                    moveCount = payload["move_no"]?.jsonPrimitive?.intOrNull ?: (game.moveCount + 1),
                    // Hangman has a single guesser: the turn never leaves them (no
                    // creator↔opponent alternation, ANDGAME-015). Other games alternate.
                    turn = if (game.gameType == GameType.HANGMAN) {
                        game.opponentId
                    } else when (event.actorId) {
                        game.createdBy -> game.opponentId
                        else -> game.createdBy
                    },
                )
            }
            "game.finished" -> games.update(gameId) {
                it.copy(
                    status = GameStatus.FINISHED,
                    result = if (str("result") == "draw") GameResultKind.DRAW else GameResultKind.WIN,
                    winnerId = str("winner_profile_id"),
                    turn = null,
                )
            }
            "game.abandoned" -> games.update(gameId) {
                it.copy(status = GameStatus.ABANDONED, result = GameResultKind.ABANDONED, turn = null)
            }
            else -> games
        }
    }

    private fun List<Game>.update(gameId: String, transform: (Game) -> Game): List<Game> =
        map { if (it.id == gameId) transform(it) else it }

    private fun serialName(type: GameType): String =
        RoadtripJson.encodeToString(GameType.serializer(), type).trim('"')
}
