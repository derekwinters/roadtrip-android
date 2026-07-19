package com.roadtrip.core.games

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ANDGAME-023: resigning / host-ending a game is destructive and always requires an explicit
 * confirmation before the resign request is issued. Unlike the ANDGAME-022 move toggle this is
 * never a setting — every RESIGN / END_GAME control tap goes through this pure seam.
 */
class ResignConfirmTest {

    // covers: ANDGAME-023
    @Test
    fun `a fresh confirmation is not pending until requested ANDGAME-023`() {
        val confirmation = ResignConfirmation(onResign = {})
        assertFalse(confirmation.pending)
    }

    // covers: ANDGAME-023
    @Test
    fun `request opens the confirmation without resigning ANDGAME-023`() {
        var resigns = 0
        val confirmation = ResignConfirmation(onResign = { resigns++ })

        confirmation.request()

        assertTrue(confirmation.pending)
        assertEquals(0, resigns) // no request issued merely by opening the dialog
    }

    // covers: ANDGAME-023
    @Test
    fun `confirm invokes the resign action exactly once and closes ANDGAME-023`() {
        var resigns = 0
        val confirmation = ResignConfirmation(onResign = { resigns++ })

        confirmation.request()
        confirmation.confirm()

        assertEquals(1, resigns)
        assertFalse(confirmation.pending)
    }

    // covers: ANDGAME-023
    @Test
    fun `cancel never resigns and returns to idle ANDGAME-023`() {
        var resigns = 0
        val confirmation = ResignConfirmation(onResign = { resigns++ })

        confirmation.request()
        confirmation.cancel()

        assertEquals(0, resigns) // Cancel leaves the game untouched
        assertFalse(confirmation.pending)
    }

    // covers: ANDGAME-023
    @Test
    fun `confirm with nothing pending is a no-op ANDGAME-023`() {
        var resigns = 0
        val confirmation = ResignConfirmation(onResign = { resigns++ })

        confirmation.confirm()

        assertEquals(0, resigns)
        assertFalse(confirmation.pending)
    }

    // covers: ANDGAME-023
    @Test
    fun `a cancelled confirmation can be re-opened and confirmed ANDGAME-023`() {
        var resigns = 0
        val confirmation = ResignConfirmation(onResign = { resigns++ })

        confirmation.request()
        confirmation.cancel()
        confirmation.request()
        confirmation.confirm()

        assertEquals(1, resigns)
        assertFalse(confirmation.pending)
    }

    // covers: ANDGAME-023
    @Test
    fun `both RESIGN and END_GAME control kinds always confirm before resigning ANDGAME-023`() {
        // The seam is control-agnostic: whichever end control the board shows, the resign action
        // only ever fires after an explicit confirm — it always confirms regardless of which.
        for (control in listOf(EndControl.RESIGN, EndControl.END_GAME)) {
            var resigns = 0
            val confirmation = ResignConfirmation(onResign = { resigns++ })

            confirmation.request()
            assertEquals(0, resigns, "$control must not resign on open")
            confirmation.confirm()
            assertEquals(1, resigns, "$control must resign exactly once on confirm")
        }
    }

    // covers: ANDGAME-023
    @Test
    fun `the prompt words the destructive action per control kind ANDGAME-023`() {
        // Hangman host ends the game; everyone else resigns. Both are irreversible.
        assertEquals("Resign", ResignPrompts.confirmLabel(EndControl.RESIGN))
        assertEquals("End game", ResignPrompts.confirmLabel(EndControl.END_GAME))
        assertTrue(ResignPrompts.title(EndControl.RESIGN).contains("Resign"))
        assertTrue(ResignPrompts.title(EndControl.END_GAME).contains("End game"))
        // The body warns it can't be undone for both kinds.
        assertTrue(ResignPrompts.body(EndControl.RESIGN).contains("undone"))
        assertTrue(ResignPrompts.body(EndControl.END_GAME).contains("undone"))
    }
}
