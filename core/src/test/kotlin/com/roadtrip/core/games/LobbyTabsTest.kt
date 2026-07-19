package com.roadtrip.core.games

import com.roadtrip.core.api.GameMode
import com.roadtrip.core.api.GameStatus
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LobbyTabsTest {
    private val me = TestData.kid.id
    private val derek = TestData.parent.id
    private val theo = TestData.otherKid.id

    @Test
    fun `the lobby always opens on the Active tab and never persists selection ANDGAME-021`() {
        // "Start on active games all the time" — the default is a fixed constant, not remembered.
        assertEquals(LobbyTab.ACTIVE, LobbyTabs.DEFAULT)
    }

    @Test
    fun `a game is Finished only when its status is FINISHED ANDGAME-021`() {
        assertEquals(
            LobbyTab.FINISHED,
            LobbyTabs.tabFor(TestData.game(id = "f", status = GameStatus.FINISHED)),
        )
        // Every non-finished status is Active (open/joinable/playable/spectatable).
        for (status in listOf(GameStatus.OPEN, GameStatus.ACTIVE, GameStatus.ABANDONED)) {
            assertEquals(
                LobbyTab.ACTIVE,
                LobbyTabs.tabFor(TestData.game(id = "g-$status", status = status)),
                "status $status should bucket to Active",
            )
        }
    }

    @Test
    fun `Active always includes the bingo family entry even with no games ANDGAME-021`() {
        val content = LobbyTabs.bucket(games = emptyList(), myProfileId = me)
        assertTrue(content.active.includesBingo, "bingo pins to the top of Active")
        assertTrue(content.active.myGames.isEmpty())
        assertTrue(content.active.incomingChallenges.isEmpty())
        assertTrue(content.active.openGames.isEmpty())
        assertTrue(content.active.spectatable.isEmpty())
        assertTrue(content.finished.isEmpty(), "Finished empty state — 'No finished games yet'")
    }

    @Test
    fun `each lobby section is bucketed into the correct tab ANDGAME-021`() {
        val myActive = TestData.game(id = "mine", createdBy = me, opponentId = derek, status = GameStatus.ACTIVE)
        val challenge = TestData.game(
            id = "chal", createdBy = derek, mode = GameMode.CHALLENGE,
            invitedProfileId = me, status = GameStatus.OPEN,
        )
        val open = TestData.game(id = "open", createdBy = derek, mode = GameMode.OPEN, status = GameStatus.OPEN)
        val watch = TestData.game(id = "watch", createdBy = derek, opponentId = theo, status = GameStatus.ACTIVE)
        val done = TestData.game(id = "done", createdBy = me, opponentId = derek, status = GameStatus.FINISHED)

        val content = LobbyTabs.bucket(listOf(myActive, challenge, open, watch, done), me)

        assertEquals(listOf("mine"), content.active.myGames.map { it.game.id })
        assertEquals(listOf("chal"), content.active.incomingChallenges.map { it.id })
        assertEquals(listOf("open"), content.active.openGames.map { it.id })
        assertEquals(listOf("watch"), content.active.spectatable.map { it.id })
        assertEquals(listOf("done"), content.finished.map { it.id })
    }

    @Test
    fun `finished games never appear in any Active section ANDGAME-021`() {
        val done = TestData.game(id = "done", createdBy = me, opponentId = derek, status = GameStatus.FINISHED)
        val content = LobbyTabs.bucket(listOf(done), me)

        val activeIds = buildList {
            addAll(content.active.myGames.map { it.game.id })
            addAll(content.active.incomingChallenges.map { it.id })
            addAll(content.active.openGames.map { it.id })
            addAll(content.active.spectatable.map { it.id })
        }
        assertFalse("done" in activeIds, "finished replay must not leak into Active")
        assertEquals(listOf("done"), content.finished.map { it.id })
    }

    @Test
    fun `watch-live excludes the viewer's own active games ANDGAME-021`() {
        // An active game I created and Derek joined is "My games", not spectatable.
        val mine = TestData.game(id = "mine", createdBy = me, opponentId = derek, status = GameStatus.ACTIVE)
        val content = LobbyTabs.bucket(listOf(mine), me)
        assertTrue(content.active.spectatable.isEmpty())
        assertEquals(listOf("mine"), content.active.myGames.map { it.game.id })
    }
}
