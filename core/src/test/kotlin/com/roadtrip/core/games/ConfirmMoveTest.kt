package com.roadtrip.core.games

import com.roadtrip.core.api.GameType
import com.roadtrip.core.storage.InMemoryConfirmMoveStore
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// covers: ANDGAME-022
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
}
