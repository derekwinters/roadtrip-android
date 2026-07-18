package com.roadtrip.core.journal

import com.roadtrip.core.api.DeepLink
import com.roadtrip.core.api.DeepLinkKind

/** Navigation targets the app's NavHost maps to routes. */
sealed class NavTarget {
    object Journal : NavTarget()
    data class MapPin(val lat: Double, val lon: Double) : NavTarget()
    data class GameReplay(val gameId: String) : NavTarget()
    data class GameBoard(val gameId: String) : NavTarget()
    data class ChecklistScreen(val stateCode: String?) : NavTarget()
    data class LegSummaryScreen(val destinationId: String) : NavTarget()
}

/**
 * Deep-link routing table (ANDJRNL-004): game result → replay, stop → map pin,
 * state crossing → checklist, leg arrival → leg summary.
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
    }
}
