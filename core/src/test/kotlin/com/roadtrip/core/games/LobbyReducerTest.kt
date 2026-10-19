package com.roadtrip.core.games

import com.roadtrip.core.api.GameMode
import com.roadtrip.core.api.GameStatus
import com.roadtrip.core.api.GameType
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun `hangman turn goes to the guesser on join and never alternates to the setter ANDGAME-015`() {
        val setter = TestData.parent.id
        val guesser = TestData.kid.id
        val hangman = TestData.game(
            id = "h1", type = GameType.HANGMAN, mode = GameMode.OPEN,
            status = GameStatus.OPEN, createdBy = setter,
        )

        // Guesser joins → turn goes to the guesser (opponent), NOT the creator/setter.
        val joined = LobbyReducer.applyEvent(
            listOf(hangman),
            TestData.event(
                seq = 1, type = "game.joined",
                payload = buildJsonObject {
                    put("game_id", "h1")
                    put("profile_id", guesser)
                },
                actorId = guesser,
            ),
        ).single()
        assertEquals(GameStatus.ACTIVE, joined.status)
        assertEquals(guesser, joined.turn)

        // Guesser guesses a letter → the turn STAYS with the guesser (no alternation back
        // to the setter, who must never guess).
        val afterMove = LobbyReducer.applyEvent(
            listOf(joined),
            TestData.gameMoveEvent(
                seq = 2, gameId = "h1", moveNo = 1,
                move = buildJsonObject { put("letter", "E") }, actorId = guesser,
            ),
        ).single()
        assertEquals(guesser, afterMove.turn)
        assertEquals(1, afterMove.moveCount)
    }

    @Test
    fun `non-hangman games still alternate the creator and opponent ANDGAME-015`() {
        val creator = TestData.parent.id
        val opponent = TestData.kid.id
        val ttt = TestData.game(
            id = "t1", type = GameType.TICTACTOE, mode = GameMode.OPEN,
            status = GameStatus.OPEN, createdBy = creator,
        )

        // Join: the creator opens play.
        val joined = LobbyReducer.applyEvent(
            listOf(ttt),
            TestData.event(
                seq = 1, type = "game.joined",
                payload = buildJsonObject {
                    put("game_id", "t1")
                    put("profile_id", opponent)
                },
                actorId = opponent,
            ),
        ).single()
        assertEquals(creator, joined.turn)

        // The creator moves → the turn alternates to the opponent.
        val afterMove = LobbyReducer.applyEvent(
            listOf(joined),
            TestData.gameMoveEvent(
                seq = 2, gameId = "t1", moveNo = 1,
                move = buildJsonObject { put("cell", 4) }, actorId = creator,
            ),
        ).single()
        assertEquals(opponent, afterMove.turn)
    }
}

class CheckersContinuationTurnTest {
    private val creator = TestData.parent.id
    private val opponent = TestData.kid.id

    private val active = TestData.game(
        id = "c1", type = GameType.CHECKERS, mode = GameMode.OPEN, status = GameStatus.ACTIVE,
        createdBy = creator, opponentId = opponent, turn = creator,
    )

    private fun jump(seq: Long, moveNo: Int, from: String, to: String, captured: String?, actor: String) =
        TestData.gameMoveEvent(
            seq = seq, gameId = "c1", moveNo = moveNo,
            move = buildJsonObject {
                put("from", from)
                put("to", to)
                if (captured != null) put("captured", captured)
            },
            actorId = actor,
        )

    /** The authoritative game the server returns after a move. */
    private fun server(turn: String, mustContinueFrom: Pair<Int, Int>?, moveCount: Int) =
        active.copy(turn = turn, moveCount = moveCount, view = CheckersContinuationTest.checkersView(mustContinueFrom, turn))

    @Test
    fun `a move that leaves a continuation keeps the turn with the actor ANDGAME-027`() {
        val after = LobbyReducer.applyEvent(
            listOf(active),
            jump(1, 1, "c3", "e5", "d4", creator),
            authoritative = server(creator, 4 to 4, 1),
        ).single()
        assertEquals(creator, after.turn)
        assertEquals("e5", CheckersContinuation.square(after))
    }

    @Test
    fun `a two-jump chain passes the turn only when the chain ends ANDGAME-027`() {
        val leg1 = LobbyReducer.applyEvent(
            listOf(active), jump(1, 1, "c3", "e5", "d4", creator), authoritative = server(creator, 4 to 4, 1),
        )
        assertEquals(creator, leg1.single().turn)
        assertEquals("e5", CheckersContinuation.square(leg1.single()))

        val leg2 = LobbyReducer.applyEvent(
            leg1, jump(2, 2, "e5", "g7", "f6", creator), authoritative = server(opponent, null, 2),
        ).single()
        assertEquals(opponent, leg2.turn)
        assertEquals(2, leg2.moveCount)
        assertNull(CheckersContinuation.square(leg2))
    }

    @Test
    fun `a non-capturing move or a final capture still passes the turn ANDGAME-027`() {
        val step = LobbyReducer.applyEvent(
            listOf(active), jump(1, 1, "c3", "d4", null, creator), authoritative = server(opponent, null, 1),
        ).single()
        assertEquals(opponent, step.turn)

        val capture = LobbyReducer.applyEvent(
            listOf(active), jump(1, 1, "c3", "e5", "d4", creator), authoritative = server(opponent, null, 1),
        ).single()
        assertEquals(opponent, capture.turn)
    }

    @Test
    fun `without an authoritative game the turn alternates and the stale checkers view is dropped ANDGAME-027`() {
        val midChain = active.copy(view = CheckersContinuationTest.checkersView(4 to 4, creator))
        val after = LobbyReducer.applyEvent(listOf(midChain), jump(2, 2, "e5", "g7", "f6", creator)).single()
        assertEquals(opponent, after.turn)
        assertNull(after.view)
        assertNull(CheckersContinuation.square(after))
    }

    @Test
    fun `a spectator following by events sees the same turn as the jumper ANDGAME-027`() {
        // The jumper's board takes each move response; the spectator's board folds the long-poll
        // batch with the game it re-fetches afterwards. Both must agree after every leg.
        val leg1Server = server(creator, 4 to 4, 1)
        val leg2Server = server(opponent, null, 2)

        var spectator = active
        assertTrue(LiveGameFold.refetchesAfterEvents(GameType.CHECKERS))
        spectator = LiveGameFold.foldBatch(spectator, listOf(jump(1, 1, "c3", "e5", "d4", creator)), leg1Server)
        assertEquals(leg1Server.turn, spectator.turn)
        assertEquals("e5", CheckersContinuation.square(spectator))

        spectator = LiveGameFold.foldBatch(spectator, listOf(jump(2, 2, "e5", "g7", "f6", creator)), leg2Server)
        assertEquals(leg2Server.turn, spectator.turn)
        assertNull(CheckersContinuation.square(spectator))

        // Both legs arriving in one long-poll batch fold to the same final turn.
        val batched = LiveGameFold.foldBatch(
            active,
            listOf(jump(1, 1, "c3", "e5", "d4", creator), jump(2, 2, "e5", "g7", "f6", creator)),
            leg2Server,
        )
        assertEquals(opponent, batched.turn)
        assertEquals(2, batched.moveCount)
    }

    @Test
    fun `other game types fold events on arrival without a refetch ANDGAME-027`() {
        assertFalse(LiveGameFold.refetchesAfterEvents(GameType.TICTACTOE))
        assertFalse(LiveGameFold.refetchesAfterEvents(GameType.CHESS))
        assertFalse(LiveGameFold.refetchesAfterEvents(GameType.ULTIMATE))
    }
}
