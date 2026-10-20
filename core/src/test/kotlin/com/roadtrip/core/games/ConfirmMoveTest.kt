package com.roadtrip.core.games

import com.roadtrip.core.api.GameType
import com.roadtrip.core.storage.InMemoryConfirmMoveStore
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// covers: ANDGAME-022, ANDGAME-026
class ConfirmMoveTest {

    private val kid = TestData.kid.id
    private val parent = TestData.parent.id

    // ---- ConfirmMovePreference (default resolution) ------------------------------------

    @Test
    fun `default is ON for a never-set profile and game type ANDGAME-022`() {
        assertTrue(ConfirmMovePreference.DEFAULT)
        assertEquals(true, ConfirmMovePreference.resolve(null))
    }

    @Test
    fun `resolve returns the stored value once set ANDGAME-022`() {
        assertEquals(false, ConfirmMovePreference.resolve(false))
        assertEquals(true, ConfirmMovePreference.resolve(true))
    }

    @Test
    fun `keys distinguish profile and game type ANDGAME-022`() {
        assertTrue(ConfirmMovePreference.key(kid, GameType.CHESS).startsWith("confirm_move:"))
        // different game type -> different key
        assert(ConfirmMovePreference.key(kid, GameType.CHESS) != ConfirmMovePreference.key(kid, GameType.CHECKERS))
        // different profile -> different key
        assert(ConfirmMovePreference.key(kid, GameType.CHESS) != ConfirmMovePreference.key(parent, GameType.CHESS))
    }

    @Test
    fun `four in a row gets its own key and the ON default ANDGAME-022 ANDGAME-028`() {
        assertEquals("confirm_move:$kid:fourinarow", ConfirmMovePreference.key(kid, GameType.FOURINAROW))
        val store = InMemoryConfirmMoveStore()
        assertEquals(true, ConfirmMovePreference.resolve(store.get(kid, GameType.FOURINAROW)))
    }

    // ---- ConfirmMoveStore (round-trip + scoping + stickiness) --------------------------

    @Test
    fun `store round-trips a set value for a profile and type ANDGAME-022`() {
        val store = InMemoryConfirmMoveStore()
        assertNull(store.get(kid, GameType.CHESS)) // never set
        store.set(kid, GameType.CHESS, false)
        assertEquals(false, store.get(kid, GameType.CHESS))
        assertEquals(false, ConfirmMovePreference.resolve(store.get(kid, GameType.CHESS)))
    }

    @Test
    fun `setting is sticky and last-write-wins per profile and type ANDGAME-022`() {
        val store = InMemoryConfirmMoveStore()
        store.set(kid, GameType.CHESS, false)
        store.set(kid, GameType.CHESS, true)
        assertEquals(true, store.get(kid, GameType.CHESS))
    }

    @Test
    fun `scope is independent across game types for the same profile ANDGAME-022`() {
        val store = InMemoryConfirmMoveStore()
        store.set(kid, GameType.CHESS, false)
        // chess is off, but a different game type is untouched -> resolves to the ON default
        assertNull(store.get(kid, GameType.CHECKERS))
        assertEquals(true, ConfirmMovePreference.resolve(store.get(kid, GameType.CHECKERS)))
        assertEquals(false, store.get(kid, GameType.CHESS))
    }

    @Test
    fun `scope is independent across profiles for the same game type ANDGAME-022`() {
        val store = InMemoryConfirmMoveStore()
        store.set(kid, GameType.CHESS, false)
        // one player's chess setting must not affect another player's chess
        assertNull(store.get(parent, GameType.CHESS))
        assertEquals(true, ConfirmMovePreference.resolve(store.get(parent, GameType.CHESS)))
    }

    // ---- MoveConfirmGate (stage -> confirm/cancel state machine) -----------------------

    private val move: JsonElement = buildJsonObject { put("cell", 4) }

    @Test
    fun `with confirm off a requested move submits immediately and never stages ANDGAME-022`() {
        val submitted = mutableListOf<JsonElement>()
        val gate = MoveConfirmGate(confirmRequired = false, onSubmit = { submitted += it })

        gate.request(move)

        assertEquals(listOf(move), submitted)
        assertNull(gate.staged)
    }

    @Test
    fun `with confirm on a requested move stages and does not submit until confirmed ANDGAME-022`() {
        val submitted = mutableListOf<JsonElement>()
        val gate = MoveConfirmGate(confirmRequired = true, onSubmit = { submitted += it })

        gate.request(move)
        assertEquals(move, gate.staged)
        assertTrue(submitted.isEmpty()) // nothing sent yet

        gate.confirm()
        assertEquals(listOf(move), submitted) // submitted exactly once
        assertNull(gate.staged)
    }

    @Test
    fun `cancel discards the staged move without submitting and restores state ANDGAME-022`() {
        val submitted = mutableListOf<JsonElement>()
        var restores = 0
        val gate = MoveConfirmGate(
            confirmRequired = true,
            onSubmit = { submitted += it },
            onRestore = { restores++ },
        )

        gate.request(move)
        gate.cancel()

        assertTrue(submitted.isEmpty()) // stage -> cancel submits nothing
        assertNull(gate.staged)
        assertEquals(1, restores) // board restored to the pre-move state
    }

    @Test
    fun `confirm with nothing staged is a no-op ANDGAME-022`() {
        val submitted = mutableListOf<JsonElement>()
        val gate = MoveConfirmGate(confirmRequired = true, onSubmit = { submitted += it })

        gate.confirm()

        assertTrue(submitted.isEmpty())
        assertNull(gate.staged)
    }

    // ---- StagedMove (show the staged {from,to} move before confirming) -------------------
    // covers: ANDGAME-026

    private fun pieceMove(from: String, to: String): JsonElement = buildJsonObject {
        put("from", from)
        put("to", to)
    }

    @Test
    fun `chess staged e2 to e4 shades both squares and names the move ANDGAME-026`() {
        val staged = pieceMove("e2", "e4")

        val highlight = assertNotNull(StagedMove.highlight(GameType.CHESS, staged))
        assertEquals(StagedMoveHighlight(from = "e2", to = "e4"), highlight)
        assertEquals(StagedMoveShade.STAGED_MOVE, highlight.shadeFor("e2"))
        assertEquals(StagedMoveShade.STAGED_MOVE, highlight.shadeFor("e4"))
        assertNull(highlight.shadeFor("e3"))
        assertEquals("Confirm e2 → e4?", StagedMove.prompt(GameType.CHESS, staged))
    }

    @Test
    fun `checkers staged move shades its algebraic squares and names the move ANDGAME-026`() {
        val staged = pieceMove("c3", "d4")

        val highlight = assertNotNull(StagedMove.highlight(GameType.CHECKERS, staged))
        assertEquals(StagedMoveHighlight(from = "c3", to = "d4"), highlight)
        assertEquals(StagedMoveShade.STAGED_MOVE, highlight.shadeFor("c3"))
        assertEquals(StagedMoveShade.STAGED_MOVE, highlight.shadeFor("d4"))
        assertEquals("Confirm c3 → d4?", StagedMove.prompt(GameType.CHECKERS, staged))
    }

    @Test
    fun `staged shade is its own role distinct from every last-move shade ANDGAME-026`() {
        val staged: BoardSquareShade = StagedMoveShade.STAGED_MOVE
        LastMoveShade.entries.forEach { lastMoveShade ->
            assertNotEquals<BoardSquareShade>(lastMoveShade, staged)
        }
    }

    @Test
    fun `staged shade wins its squares and the last move keeps its others ANDGAME-026`() {
        val staged = StagedMoveHighlight(from = "e5", to = "d6")
        val last = LastMoveHighlight.PieceMove(origin = "e7", destination = "e5")

        assertEquals(StagedMoveShade.STAGED_MOVE, StagedMove.shadeAt("e5", staged, last))
        assertEquals(StagedMoveShade.STAGED_MOVE, StagedMove.shadeAt("d6", staged, last))
        assertEquals(LastMoveShade.PIECE_ORIGIN, StagedMove.shadeAt("e7", staged, last))
        assertNull(StagedMove.shadeAt("a1", staged, last))
        // Nothing staged -> exactly the ANDGAME-024 last-move shade.
        assertEquals(LastMoveShade.PIECE_DESTINATION, StagedMove.shadeAt("e5", null, last))
        assertNull(StagedMove.shadeAt("e5", null, null))
    }

    @Test
    fun `mark games and hangman keep the generic prompt and get no staged highlight ANDGAME-026`() {
        val ttt = buildJsonObject { put("cell", 4) }
        val ultimate = buildJsonObject {
            put("board", 2)
            put("cell", 7)
        }
        val hangman = buildJsonObject { put("letter", "E") }

        listOf(GameType.TICTACTOE to ttt, GameType.ULTIMATE to ultimate, GameType.HANGMAN to hangman)
            .forEach { (type, staged) ->
                assertNull(StagedMove.highlight(type, staged), "$type gets no staged highlight")
                assertEquals(StagedMove.GENERIC_PROMPT, StagedMove.prompt(type, staged))
            }
        assertEquals("Confirm your move?", StagedMove.GENERIC_PROMPT)
    }

    @Test
    fun `nothing staged gives no highlight ANDGAME-026`() {
        assertNull(StagedMove.highlight(GameType.CHESS, null))
        assertNull(StagedMove.highlight(GameType.CHECKERS, buildJsonObject { put("from", "c3") }))
    }

    @Test
    fun `origin stays selected once staged and clears when submitted at once ANDGAME-026`() {
        assertEquals("e2", StagedMove.selectionAfterRequest(from = "e2", confirmRequired = true))
        assertNull(StagedMove.selectionAfterRequest(from = "e2", confirmRequired = false))
    }

    @Test
    fun `cancel clears the highlight and leaves the pre-move board unchanged ANDGAME-026`() {
        val session = ReplaySession(
            engine = ReplayEngine(GameType.CHESS),
            initialMoves = listOf(buildJsonObject {
                put("from", "e7")
                put("to", "e5")
            }),
            pinnedToLive = true,
        )
        val before = session.board()
        var highlight: StagedMoveHighlight? = null
        val submitted = mutableListOf<JsonElement>()
        val gate = MoveConfirmGate(
            confirmRequired = true,
            onSubmit = { submitted += it },
            onStageChanged = { highlight = StagedMove.highlight(GameType.CHESS, it) },
        )

        gate.request(pieceMove("e2", "e4"))
        assertEquals(StagedMoveHighlight("e2", "e4"), highlight)
        assertEquals(before, session.board()) // staged: no piece preview, position untouched

        gate.cancel()
        assertNull(highlight)
        assertTrue(submitted.isEmpty())
        assertEquals(before, session.board())
    }

    @Test
    fun `confirm clears the highlight and submits the same move exactly once ANDGAME-026`() {
        var highlight: StagedMoveHighlight? = null
        val submitted = mutableListOf<JsonElement>()
        val gate = MoveConfirmGate(
            confirmRequired = true,
            onSubmit = { submitted += it },
            onStageChanged = { highlight = StagedMove.highlight(GameType.CHECKERS, it) },
        )
        val staged = pieceMove("c3", "d4")

        gate.request(staged)
        gate.confirm()
        gate.confirm() // a second confirm is a no-op

        assertNull(highlight)
        assertEquals(listOf(staged), submitted)
    }
}
