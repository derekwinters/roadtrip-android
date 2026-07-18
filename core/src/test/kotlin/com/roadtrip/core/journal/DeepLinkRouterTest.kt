package com.roadtrip.core.journal

import com.roadtrip.core.api.DeepLink
import com.roadtrip.core.api.DeepLinkKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkRouterTest {

    @Test
    fun `routes each entry kind to its navigation target ANDJRNL-004`() {
        assertEquals(
            NavTarget.GameReplay("g-42"),
            DeepLinkRouter.route(DeepLink(DeepLinkKind.GAME_REPLAY, gameId = "g-42")),
        )
        assertEquals(
            NavTarget.MapPin(39.7392, -104.9903),
            DeepLinkRouter.route(DeepLink(DeepLinkKind.MAP_PIN, lat = 39.7392, lon = -104.9903)),
        )
        assertEquals(
            NavTarget.ChecklistScreen("CO"),
            DeepLinkRouter.route(DeepLink(DeepLinkKind.CHECKLIST, stateCode = "CO")),
        )
        assertEquals(
            NavTarget.LegSummaryScreen("d-7"),
            DeepLinkRouter.route(DeepLink(DeepLinkKind.LEG_SUMMARY, destinationId = "d-7")),
        )
    }

    @Test
    fun `returns null for links missing their required payload ANDJRNL-004`() {
        assertNull(DeepLinkRouter.route(DeepLink(DeepLinkKind.GAME_REPLAY)))
        assertNull(DeepLinkRouter.route(DeepLink(DeepLinkKind.MAP_PIN, lat = 39.7)))
        assertNull(DeepLinkRouter.route(DeepLink(DeepLinkKind.LEG_SUMMARY)))
    }
}
