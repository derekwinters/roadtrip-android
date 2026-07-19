package com.roadtrip.core.journal

import com.roadtrip.core.api.DeepLink
import com.roadtrip.core.api.DeepLinkKind
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JournalRowPresenterTest {

    private fun item(link: DeepLink?, display: JournalDisplay) = JournalFeedItem(
        key = "k",
        clientTs = Instant.EPOCH,
        display = display,
        syncing = false,
        link = link,
    )

    @Test
    fun `a manual post has no link so it is not navigable but still full emphasis ANDJRNL-008`() {
        val post = item(
            link = null,
            display = JournalDisplay.ManualPost(authorName = "Maya", authorAvatar = "fox", text = "hi"),
        )

        val presentation = JournalRowPresenter.present(post)

        // Not click-navigable (no deep link)...
        assertFalse(presentation.navigable)
        assertNull(presentation.navTarget)
        // ...but never drawn as a disabled/greyed card: appearance is independent of the link.
        assertTrue(presentation.fullEmphasis)
    }

    @Test
    fun `a linked row is navigable to its target and full emphasis ANDJRNL-008`() {
        val stop = item(
            link = DeepLink(DeepLinkKind.MAP_PIN, lat = 39.7, lon = -105.2),
            display = JournalDisplay.Stop(text = "Stopped at overlook", pin = null),
        )

        val presentation = JournalRowPresenter.present(stop)

        assertTrue(presentation.navigable)
        assertEquals(NavTarget.MapPin(39.7, -105.2), presentation.navTarget)
        assertTrue(presentation.fullEmphasis)
    }

    @Test
    fun `a row whose link cannot resolve its payload is non-navigable yet full emphasis ANDJRNL-008`() {
        // A malformed deep link that DeepLinkRouter cannot route (missing gameId).
        val broken = item(
            link = DeepLink(DeepLinkKind.GAME_REPLAY),
            display = JournalDisplay.GameResult(text = "Maya won", gameId = null),
        )

        val presentation = JournalRowPresenter.present(broken)

        assertFalse(presentation.navigable)
        assertTrue(presentation.fullEmphasis)
    }
}
