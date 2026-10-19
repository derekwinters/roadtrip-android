package com.roadtrip.core.games

import com.roadtrip.core.api.Game
import com.roadtrip.core.api.GameMode
import com.roadtrip.core.api.GameStatus
import com.roadtrip.core.api.GameType
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CheckersContinuationTest {
    private val jumper = TestData.parent.id
    private val opponent = TestData.kid.id

    private fun checkers(mustContinueFrom: Pair<Int, Int>?, turn: String = jumper, type: GameType = GameType.CHECKERS): Game =
        TestData.game(
            id = "c1", type = type, mode = GameMode.OPEN, status = GameStatus.ACTIVE,
            createdBy = jumper, opponentId = opponent, turn = turn,
        ).copy(view = checkersView(mustContinueFrom, turn))

    @Test
    fun `reads must_continue_from from the server view as an algebraic square ANDGAME-027`() {
        // Backend [row, col] -> file 'a' + col, rank row + 1: [3, 5] is f4.
        assertEquals("f4", CheckersContinuation.square(checkers(3 to 5)))
        assertEquals("a1", CheckersContinuation.square(checkers(0 to 0)))
        assertNull(CheckersContinuation.square(checkers(null)))
        assertNull(CheckersContinuation.square(checkers(3 to 5).copy(view = null)))
        // Only the checkers view carries a continuation.
        assertNull(CheckersContinuation.square(checkers(3 to 5, type = GameType.CHESS)))
    }

    @Test
    fun `only the jumper on an active game is forced to continue ANDGAME-027`() {
        val mid = checkers(3 to 5)
        assertEquals("f4", CheckersContinuation.forcedFrom(mid, jumper))
        assertNull(CheckersContinuation.forcedFrom(mid, opponent))
        assertNull(CheckersContinuation.forcedFrom(mid, "spectator"))
        assertNull(CheckersContinuation.forcedFrom(mid.copy(status = GameStatus.FINISHED), jumper))
        assertNull(CheckersContinuation.forcedFrom(checkers(null), jumper))
    }

    @Test
    fun `status reads Keep jumping during a continuation and clears after ANDGAME-027`() {
        assertEquals("Keep jumping", CheckersContinuation.status(checkers(3 to 5), jumper))
        assertNull(CheckersContinuation.status(checkers(null), jumper))
        assertNull(CheckersContinuation.status(checkers(3 to 5), opponent))
    }

    @Test
    fun `the continuing piece stays selected even after the selection is cleared ANDGAME-027`() {
        // Cancel / submit clears the local selection; the forced piece is still shown selected.
        assertEquals("f4", CheckersContinuation.selection(selected = null, forcedFrom = "f4"))
        assertEquals("f4", CheckersContinuation.selection(selected = "b2", forcedFrom = "f4"))
        assertEquals("b2", CheckersContinuation.selection(selected = "b2", forcedFrom = null))
        assertNull(CheckersContinuation.selection(selected = null, forcedFrom = null))
    }

    @Test
    fun `a tap during a continuation requests the next hop from the forced piece without reselecting ANDGAME-027`() {
        // Any other square, empty or occupied, is the next hop from the forced piece.
        assertEquals(PieceTap.Move("f4", "d6"), CheckersContinuation.tap(null, "d6", occupied = false, forcedFrom = "f4"))
        assertEquals(PieceTap.Move("f4", "b2"), CheckersContinuation.tap("f4", "b2", occupied = true, forcedFrom = "f4"))
        // Tapping the forced piece never deselects it.
        assertEquals(PieceTap.Select("f4"), CheckersContinuation.tap("f4", "f4", occupied = true, forcedFrom = "f4"))
    }

    @Test
    fun `without a continuation taps keep the select then move behaviour ANDGAME-027`() {
        assertEquals(PieceTap.Select("b2"), CheckersContinuation.tap(null, "b2", occupied = true, forcedFrom = null))
        assertEquals(PieceTap.Select(null), CheckersContinuation.tap(null, "c3", occupied = false, forcedFrom = null))
        assertEquals(PieceTap.Select(null), CheckersContinuation.tap("b2", "b2", occupied = true, forcedFrom = null))
        assertEquals(PieceTap.Move("b2", "c3"), CheckersContinuation.tap("b2", "c3", occupied = false, forcedFrom = null))
    }

    @Test
    fun `confirm on stages each hop inline and cancel keeps the continuation ANDGAME-027 ANDGAME-022`() {
        val submitted = mutableListOf<JsonElement>()
        val gate = MoveConfirmGate(confirmRequired = true, onSubmit = { submitted += it })
        val forced = "f4"
        var selected: String? = null

        // Hop requested straight from the forced piece: no fresh selection tap needed.
        val hop = CheckersContinuation.tap(selected, "d6", occupied = false, forcedFrom = forced) as PieceTap.Move
        selected = StagedMove.selectionAfterRequest(hop.from, confirmRequired = true)
        gate.request(hop.json())
        assertEquals(hop.json(), gate.staged)
        assertEquals(0, submitted.size)

        // Cancel discards only the hop; the forced piece is still selected.
        gate.cancel()
        selected = null
        assertEquals(null, gate.staged as Any?)
        assertEquals("f4", CheckersContinuation.selection(selected, forced))

        // Re-request and confirm: exactly one submit for the hop.
        gate.request(CheckersContinuation.tap(null, "d6", false, forced).let { (it as PieceTap.Move).json() })
        gate.confirm()
        assertEquals(listOf<JsonElement>(hop.json()), submitted.toList())
    }

    @Test
    fun `confirm off submits each hop directly ANDGAME-027`() {
        val submitted = mutableListOf<JsonElement>()
        val gate = MoveConfirmGate(confirmRequired = false, onSubmit = { submitted += it })
        val hop = CheckersContinuation.tap(null, "d6", occupied = false, forcedFrom = "f4") as PieceTap.Move
        gate.request(hop.json())
        assertEquals(null, gate.staged as Any?)
        assertEquals(listOf<JsonElement>(hop.json()), submitted.toList())
        assertEquals(buildJsonObject { put("from", "f4"); put("to", "d6") }, hop.json())
    }

    companion object {
        fun checkersView(mustContinueFrom: Pair<Int, Int>?, turn: String) = buildJsonObject {
            put("turn", turn)
            if (mustContinueFrom == null) {
                put("must_continue_from", JsonNull)
            } else {
                put(
                    "must_continue_from",
                    buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive(mustContinueFrom.first))
                        add(kotlinx.serialization.json.JsonPrimitive(mustContinueFrom.second))
                    },
                )
            }
        }
    }
}
