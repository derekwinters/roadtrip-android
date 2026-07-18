package com.roadtrip.core.api

import com.roadtrip.core.common.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * DTO serialization against recorded backend-shaped fixtures (08-testing.md layer 3),
 * so the client and server contract cannot drift silently.
 */
class DtoContractFixturesTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { "missing fixture $name" }
            .bufferedReader().readText()

    @Test
    fun `parses the journal page fixture with all five entry kinds`() {
        val page = RoadtripJson.decodeFromString(JournalPage.serializer(), fixture("journal.json"))

        assertEquals(5, page.entries.size)
        assertEquals(
            listOf(
                JournalKind.POST, JournalKind.STATE_CROSSING, JournalKind.STOP,
                JournalKind.GAME_RESULT, JournalKind.LEG_ARRIVAL,
            ),
            page.entries.map { it.kind },
        )
        assertEquals(Role.KID, page.entries[0].actor?.role)
        assertEquals("CO", page.entries[1].link?.stateCode)
        assertEquals(39.7002, page.entries[2].link?.lat)
        assertEquals(30L, page.nextBefore)
    }

    @Test
    fun `parses the map state fixture`() {
        val map = RoadtripJson.decodeFromString(MapState.serializer(), fixture("map.json"))

        assertEquals(39.9012, map.current?.lat)
        assertEquals("Rocky Mountain NP", map.activeDestination?.name)
        assertEquals(DestinationStatus.ACTIVE, map.activeDestination?.status)
        assertEquals(42.7, map.remainingMi)
        assertEquals(3, map.breadcrumb.size)
    }

    @Test
    fun `parses the game fixture including the opaque engine view`() {
        val game = RoadtripJson.decodeFromString(Game.serializer(), fixture("game.json"))

        assertEquals(GameType.ULTIMATE, game.gameType)
        assertEquals(GameStatus.ACTIVE, game.status)
        assertEquals(7, game.moveCount)
        assertNotNull(game.view) // engine view is carried opaquely for the board renderer
        assertNull(game.result)
    }

    @Test
    fun `parses the notifications fixture`() {
        val page = RoadtripJson.decodeFromString(NotificationsPage.serializer(), fixture("notifications.json"))

        assertEquals(NotificationKind.CHALLENGE_RECEIVED, page.items[0].kind)
        assertEquals("5b7c9d10-2222-4a1b-8c3d-000000000042", page.items[0].gameId)
        assertEquals(NotificationKind.JOURNAL_ACTIVITY, page.items[1].kind)
        assertEquals(56L, page.nextAfter)
    }
}
