package com.roadtrip.core.journal

import com.roadtrip.core.api.DeepLink
import com.roadtrip.core.api.DeepLinkKind
import java.time.Instant
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StopCoordinatesTest {

    private fun item(link: DeepLink?, display: JournalDisplay) = JournalFeedItem(
        key = "k",
        clientTs = Instant.EPOCH,
        display = display,
        syncing = false,
        link = link,
    )

    @Test
    fun `formats lat comma lon to 5 decimal places ANDJRNL-009`() {
        assertEquals("39.70000, -105.20000", StopCoordinates.format(39.7, -105.2))
        assertEquals("39.73916, -104.99025", StopCoordinates.format(39.739157, -104.990251))
    }

    @Test
    fun `always uses a dot decimal separator regardless of device locale ANDJRNL-009`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("48.13743, 11.57549", StopCoordinates.format(48.137431, 11.575490))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `a stop row with a pin exposes its coordinates as copy text ANDJRNL-009`() {
        val pin = DeepLink(DeepLinkKind.MAP_PIN, lat = 39.7, lon = -105.2)
        val stop = item(link = pin, display = JournalDisplay.Stop(text = "Stopped at overlook", pin = pin))

        val presentation = JournalRowPresenter.present(stop)

        assertEquals("39.70000, -105.20000", presentation.copyText)
        // Tap still navigates to the map pin (ANDJRNL-004).
        assertEquals(NavTarget.MapPin(39.7, -105.2), presentation.navTarget)
    }

    @Test
    fun `a stop row without a pin offers neither tap nor copy and stays full emphasis ANDJRNL-009`() {
        val stop = item(link = null, display = JournalDisplay.Stop(text = "Stopped somewhere", pin = null))

        val presentation = JournalRowPresenter.present(stop)

        assertNull(presentation.copyText)
        assertNull(presentation.navTarget)
        assertEquals(true, presentation.fullEmphasis)
    }

    @Test
    fun `a stop pin missing a coordinate offers no copy ANDJRNL-009`() {
        val pin = DeepLink(DeepLinkKind.MAP_PIN, lat = 39.7, lon = null)
        val stop = item(link = pin, display = JournalDisplay.Stop(text = "Stopped", pin = pin))

        assertNull(JournalRowPresenter.present(stop).copyText)
    }

    @Test
    fun `other entry kinds offer no copy even when they carry coordinates ANDJRNL-009`() {
        val pin = DeepLink(DeepLinkKind.MAP_PIN, lat = 39.7, lon = -105.2)
        val post = item(link = pin, display = JournalDisplay.ManualPost("Maya", "fox", "hi"))

        assertNull(JournalRowPresenter.present(post).copyText)
    }
}
