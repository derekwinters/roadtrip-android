package com.roadtrip.core.games

import com.roadtrip.core.api.ApiException
import com.roadtrip.core.api.GameStatus
import com.roadtrip.core.testing.FakeRoadtripApi
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MoveSubmitterTest {
    private val api = FakeRoadtripApi()
    private val initial = TestData.game(
        id = "g-1", status = GameStatus.ACTIVE,
        createdBy = TestData.kid.id, opponentId = TestData.parent.id,
        moveCount = 4, turn = TestData.kid.id,
    )
    private val move = buildJsonObject { put("cell", 4) }
    private val optimistic: (com.roadtrip.core.api.Game) -> com.roadtrip.core.api.Game = {
        it.copy(moveCount = it.moveCount + 1, turn = it.opponentId)
    }

    @Test
    fun `shows the optimistic state then applies the server state on success ANDGAME-003`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val serverGame = initial.copy(moveCount = 5, turn = TestData.parent.id)
        api.moveHandler = { _, _ ->
            gate.await()
            serverGame
        }
        val submitter = MoveSubmitter(api, initial)

        val job = launch { submitter.submit(move, optimistic) }
        runCurrent()
        // While the request is in flight the board already shows the optimistic move.
        assertEquals(5, submitter.current.moveCount)
        assertEquals(TestData.parent.id, submitter.current.turn)

        gate.complete(Unit)
        job.join()
        assertEquals(serverGame, submitter.current)
    }

    @Test
    fun `a 409 rejection restores the exact pre-move state with the server reason ANDGAME-003`() = runTest {
        api.moveHandler = { _, _ -> throw ApiException(409, "not_your_turn", "Not your turn") }
        val submitter = MoveSubmitter(api, initial)

        val outcome = submitter.submit(move, optimistic)

        val rejected = assertIs<MoveOutcome.Rejected>(outcome)
        assertEquals("Not your turn", rejected.reason)
        assertEquals(initial, rejected.restored)
        assertEquals(initial, submitter.current) // rollback, not a partial state
        assertEquals("Not your turn", submitter.lastRejectionReason)
    }

    @Test
    fun `a 400 illegal move rolls back with the engine reason ANDGAME-003`() = runTest {
        api.moveHandler = { _, _ -> throw ApiException(400, "illegal_move", "Bishop cannot jump pieces") }
        val submitter = MoveSubmitter(api, initial)

        val outcome = submitter.submit(move, optimistic)

        assertEquals("Bishop cannot jump pieces", assertIs<MoveOutcome.Rejected>(outcome).reason)
        assertEquals(initial, submitter.current)
    }

    @Test
    fun `a transport failure also restores the board ANDGAME-003`() = runTest {
        api.offline = true
        val submitter = MoveSubmitter(api, initial)

        val outcome = submitter.submit(move, optimistic)

        assertIs<MoveOutcome.NetworkFailure>(outcome)
        assertEquals(initial, submitter.current)
    }
}
