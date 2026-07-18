package com.roadtrip.app.ui

import com.roadtrip.core.journal.NavTarget

/** Navigation-compose route patterns and builders for core [NavTarget]s (ANDJRNL-004). */
object Routes {
    const val JOURNAL = "journal"
    const val MAP = "map?lat={lat}&lon={lon}"
    const val GAMES = "games"
    const val CHECKLIST = "checklist?state={state}"
    const val TRIP = "trip?dest={dest}"
    const val TRIPS = "trips?trip={trip}"
    const val SETTINGS = "settings"
    const val BOARD = "board/{gameId}"
    const val REPLAY = "replay/{gameId}"
    const val BINGO = "bingo?trip={trip}"

    fun map(lat: Double? = null, lon: Double? = null): String =
        if (lat != null && lon != null) "map?lat=$lat&lon=$lon" else "map"

    fun checklist(stateCode: String? = null): String =
        if (stateCode.isNullOrBlank()) "checklist" else "checklist?state=$stateCode"

    fun trip(destinationId: String? = null): String =
        if (destinationId.isNullOrBlank()) "trip" else "trip?dest=$destinationId"

    /** Trip history browser, optionally opened on one trip's views (ANDTRIP-003). */
    fun trips(tripId: String? = null): String =
        if (tripId.isNullOrBlank()) "trips" else "trips?trip=$tripId"

    fun board(gameId: String): String = "board/$gameId"

    fun replay(gameId: String): String = "replay/$gameId"

    /** License plate bingo; a trip id opens a past trip's card read-only (ANDBNG-004). */
    fun bingo(tripId: String? = null): String =
        if (tripId.isNullOrBlank()) "bingo" else "bingo?trip=$tripId"

    fun forTarget(target: NavTarget): String = when (target) {
        is NavTarget.Journal -> JOURNAL
        is NavTarget.MapPin -> map(target.lat, target.lon)
        is NavTarget.GameBoard -> board(target.gameId)
        is NavTarget.GameReplay -> replay(target.gameId)
        is NavTarget.ChecklistScreen -> checklist(target.stateCode)
        is NavTarget.LegSummaryScreen -> trip(target.destinationId)
        is NavTarget.TripSummaryScreen -> trips(target.tripId)
    }
}
