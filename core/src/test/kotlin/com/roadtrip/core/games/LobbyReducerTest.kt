package com.roadtrip.core.games

import com.roadtrip.core.api.GameMode
import com.roadtrip.core.api.GameStatus
import com.roadtrip.core.api.GameType
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class LobbyReducerTest {
    private val me = TestData.kid.id
    private val other = TestData.parent.id

    private val games = listOf(
        TestData.game(id = "g-open", createdBy = other, status = GameStatus.OPEN, mode = GameMode.OPEN),
        TestData.game(id = "g-my-open", createdBy = me, status = GameStatus.OPEN, mode = GameMode.OPEN),
        TestData.game(
            id = "g-my-turn", createdBy = me, opponentId = other,
            status = GameStatus.ACTIVE, turn = me,
        ),
        TestData.game(
            id = "g-their-turn", createdBy = other, opponentId = me,
            status = GameStatus.ACTIVE, turn = other,
        ),
        TestData.game(
            id = "g-challenge-me", createdBy = other, mode = GameMode.CHALLENGE,
            status = GameStatus.OPEN, invitedProfileId = me,
        ),
        TestData.game(
            id = "g-challenge-other", createdBy = me, mode = GameMode.CHALLENGE,
            status = GameStatus.OPEN, invitedProfileId = other,
        ),
        TestData.game(id = "g-done", createdBy = me, opponentId = other, status = GameStatus.FINISHED),
        TestData.game(
            id = "g-not-mine", createdBy = other, opponentId = "p-kid2",
            status = GameStatus.ACTIVE, turn = other,
        ),
    )

    @Test
    fun `lists open games my games with turn indicator and incoming challenges ANDGAME-001`() {
        val lobby = LobbyReducer.reduce(games, me)

        // Open games to join: someone else's open-mode games only.
        assertEquals(listOf("g-open"), lobby.openGames.map { it.id })

        // My games: active games I'm in, each with a turn indicator.
        assertEquals(
            mapOf("g-my-turn" to true, "g-their-turn" to false),
            lobby.myGames.associate { it.game.id to it.myTurn },
        )

        // Incoming challenges: challenge games inviting ME, not ones I sent.
        assertEquals(listOf("g-challenge-me"), lobby.incomingChallenges.map { it.id })
    }

    @Test
    fun `folds feed events into the lobby without a reload ANDGAME-001`() {
        // A new challenge arrives on the event feed.
        val challengeEvent = TestData.event(
            seq = 90,
            type = "game.created",
            payload = buildJsonObject {
                put("game_id", "g-new")
                put("game_type", "hangman")
                put("mode", "challenge")
                put("invited_profile_id", me)
            },
            actorId = other,
        )
        var updated = LobbyReducer.applyEvent(games, challengeEvent)
        assertTrue(LobbyReducer.reduce(updated, me).incomingChallenges.any { it.id == "g-new" })

        // The opponent moves in g-their-turn: it becomes my turn without refetching.
        val moveEvent = TestData.gameMoveEvent(
            seq = 91, gameId = "g-their-turn", moveNo = 5,
            move = buildJsonObject { put("cell", 4) }, actorId = other,
        )
        updated = LobbyReducer.applyEvent(updated, moveEvent)
        val mine = LobbyReducer.reduce(updated, me).myGames.single { it.game.id == "g-their-turn" }
        assertTrue(mine.myTurn)
        assertEquals(5, mine.game.moveCount)

        // A finished event retires the game from the active list.
        val finishedEvent = TestData.event(
            seq = 92,
            type = "game.finished",
            payload = buildJsonObject {
                put("game_id", "g-my-turn")
                put("result", "win")
                put("winner_profile_id", me)
            },
        )
        updated = LobbyReducer.applyEvent(updated, finishedEvent)
        assertFalse(LobbyReducer.reduce(updated, me).myGames.any { it.game.id == "g-my-turn" })
    }
}
