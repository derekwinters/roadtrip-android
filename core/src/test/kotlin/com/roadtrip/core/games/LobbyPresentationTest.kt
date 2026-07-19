package com.roadtrip.core.games

import com.roadtrip.core.api.GameStatus
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LobbyPresentationTest {
    private val me = TestData.kid.id
    private val derek = TestData.parent.id
    private val names = mapOf(
        TestData.parent.id to "Derek",
        TestData.kid.id to "Maya",
        TestData.otherKid.id to "Theo",
    )

    @Test
    fun `attributes games created by others to the creator by name ANDGAME-009`() {
        val game = TestData.game(id = "g", createdBy = derek, status = GameStatus.OPEN)
        assertEquals("Derek's Chess", GameLobbyLabeler.creatorTitle(game, "Chess", names))
    }

    @Test
    fun `falls back to a neutral label when the creator is not cached ANDGAME-009`() {
        val game = TestData.game(id = "g", createdBy = "p-ghost", status = GameStatus.OPEN)
        val title = GameLobbyLabeler.creatorTitle(game, "Hangman", names)
        assertEquals("Someone's Hangman", title)
        // Never leaks the raw profile id.
        assertTrue(!title.contains("p-ghost"))
    }

    @Test
    fun `my games attribute the opponent and keep the game type ANDGAME-009`() {
        // I created it, Theo joined.
        val mine = TestData.game(
            id = "g", createdBy = me, opponentId = TestData.otherKid.id, status = GameStatus.ACTIVE,
        )
        assertEquals("Checkers vs Theo", GameLobbyLabeler.myGameTitle(mine, me, "Checkers", names))

        // Derek created it, I joined — the opponent is still the other player, not me.
        val theirs = TestData.game(
            id = "g2", createdBy = derek, opponentId = me, status = GameStatus.ACTIVE,
        )
        assertEquals("Chess vs Derek", GameLobbyLabeler.myGameTitle(theirs, me, "Chess", names))
    }

    @Test
    fun `my games without a cached opponent read as your game ANDGAME-009`() {
        val solo = TestData.game(id = "g", createdBy = me, opponentId = null, status = GameStatus.OPEN)
        assertEquals("Your Tic-tac-toe", GameLobbyLabeler.myGameTitle(solo, me, "Tic-tac-toe", names))

        val unknownOpponent = TestData.game(
            id = "g2", createdBy = me, opponentId = "p-ghost", status = GameStatus.ACTIVE,
        )
        assertEquals("Your Chess", GameLobbyLabeler.myGameTitle(unknownOpponent, me, "Chess", names))
    }

    @Test
    fun `lobby reserves bottom inset that clears the FAB ANDGAME-011`() {
        // FAB is 56dp; with any reasonable margin the inset must sit clear above it so the last
        // row's action is never obscured, in every section.
        assertTrue(GamesLobbyLayout.FAB_CLEARANCE_DP >= 56 + 16)
    }
}
