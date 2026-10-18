package com.roadtrip.core.journal

import com.roadtrip.core.api.DeepLink
import com.roadtrip.core.api.DeepLinkKind
import java.util.Locale

/** Navigation targets the app's NavHost maps to routes. */
sealed class NavTarget {
    object Journal : NavTarget()
    data class MapPin(val lat: Double, val lon: Double) : NavTarget()
    data class GameReplay(val gameId: String) : NavTarget()
    data class GameBoard(val gameId: String) : NavTarget()
    data class ChecklistScreen(val stateCode: String?) : NavTarget()
    data class LegSummaryScreen(val destinationId: String) : NavTarget()
    data class TripSummaryScreen(val tripId: String) : NavTarget()
}

/**
 * Deep-link routing table (ANDJRNL-004): game result → replay, stop → map pin,
 * state crossing → checklist, leg arrival → leg summary, trip started/ended → trip summary.
 */
object DeepLinkRouter {
    fun route(link: DeepLink): NavTarget? = when (link.kind) {
        DeepLinkKind.GAME_REPLAY -> link.gameId?.let { NavTarget.GameReplay(it) }
        DeepLinkKind.MAP_PIN -> {
            val lat = link.lat
            val lon = link.lon
            if (lat != null && lon != null) NavTarget.MapPin(lat, lon) else null
        }
        DeepLinkKind.CHECKLIST -> NavTarget.ChecklistScreen(link.stateCode)
        DeepLinkKind.LEG_SUMMARY -> link.destinationId?.let { NavTarget.LegSummaryScreen(it) }
        DeepLinkKind.TRIP_SUMMARY -> link.tripId?.let { NavTarget.TripSummaryScreen(it) }
    }
}

/**
 * How a single feed row should be presented. Separates the two orthogonal decisions the
 * row card conflated (ANDJRNL-008):
 *  - [navTarget]: where a tap navigates, or `null` when the row is not deep-linkable;
 *  - [fullEmphasis]: whether the row draws with full-emphasis (enabled) colors.
 *
 * Emphasis is **always** full — a row's appearance never depends on whether it links
 * somewhere. Only [navigable] is gated by the link. This keeps a manual post (which carries
 * no deep link) from rendering inside a disabled/greyed card.
 */
data class JournalRowPresentation(
    val navTarget: NavTarget?,
    val fullEmphasis: Boolean,
    /** Clipboard text for press-and-hold, or `null` when the row offers no copy (ANDJRNL-009). */
    val copyText: String? = null,
) {
    val navigable: Boolean get() = navTarget != null
}

/**
 * Pure presentation seam for a journal feed row (ANDJRNL-008). A row is click-navigable iff
 * its link resolves to a [NavTarget] via [DeepLinkRouter] (ANDJRNL-004); its visual emphasis
 * is independent of that and always full, so non-linkable rows (e.g. manual posts) are never
 * drawn as disabled cards. Mirrors [DeepLinkRouter] so the composable stays thin.
 */
object JournalRowPresenter {
    fun present(item: JournalFeedItem): JournalRowPresentation =
        JournalRowPresentation(
            navTarget = item.link?.let(DeepLinkRouter::route),
            fullEmphasis = true,
            copyText = (item.display as? JournalDisplay.Stop)?.pin?.let(StopCoordinates::of),
        )
}

/**
 * Copyable stop coordinates (ANDJRNL-009): `lat, lon` in decimal degrees to
 * [DECIMAL_PLACES] places, always with a `.` decimal separator. Pure — reads only the
 * cached pin, so copying never depends on connectivity.
 */
object StopCoordinates {
    const val DECIMAL_PLACES = 5

    private const val PATTERN = "%.${DECIMAL_PLACES}f, %.${DECIMAL_PLACES}f"

    fun format(lat: Double, lon: Double): String = String.format(Locale.ROOT, PATTERN, lat, lon)

    /** The copy text for a stop pin, or `null` when either coordinate is missing. */
    fun of(pin: DeepLink): String? {
        val lat = pin.lat ?: return null
        val lon = pin.lon ?: return null
        return format(lat, lon)
    }
}
