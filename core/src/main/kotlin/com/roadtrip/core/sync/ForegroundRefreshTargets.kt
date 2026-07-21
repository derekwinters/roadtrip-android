package com.roadtrip.core.sync

import com.roadtrip.core.notifications.Screen

/** A server-derived read model the foreground live-refresh loop can re-pull (ANDSYNC-008). */
enum class ForegroundReadModel {
    JOURNAL,
    MAP,
    DESTINATIONS,
    GAMES,
    CHECKLIST,
    TRIPS,
    TRIP_SUMMARY,
    LEGS,
}

/**
 * Pure `Screen` -> read-models mapping for the in-app foreground live-refresh loop
 * (ANDSYNC-008). Kept out of `AppContainer` so "which read models does this screen need
 * live?" is unit-testable on the JVM while the container loop stays a thin shell.
 *
 * The MAP screen refreshes both the map state AND the destination list (ANDMAP-012): a parent
 * viewing the map must see mid-trip destination adds/removes/reorders and arrivals landing in
 * the destination panel without leaving the screen. Otherwise local pings keep moving the
 * "current" dot while the frozen active-destination marker and remaining distance go stale.
 */
object ForegroundRefreshTargets {
    fun forScreen(screen: Screen): Set<ForegroundReadModel> = when (screen) {
        Screen.JOURNAL -> setOf(ForegroundReadModel.JOURNAL)
        Screen.MAP -> setOf(ForegroundReadModel.MAP, ForegroundReadModel.DESTINATIONS)
        Screen.GAMES -> setOf(ForegroundReadModel.GAMES)
        Screen.CHECKLIST -> setOf(ForegroundReadModel.CHECKLIST)
        Screen.TRIP -> setOf(ForegroundReadModel.TRIPS, ForegroundReadModel.TRIP_SUMMARY, ForegroundReadModel.LEGS)
        // The game board self-updates via its long-poll (ANDGAME-005) and settings has no live
        // feed, so both only bump the refresh tick — no read model to re-pull. Bingo needs a full
        // sync pass (requiresFullSync below), not a cheap read-model target.
        Screen.BINGO, Screen.GAME_BOARD, Screen.SETTINGS -> emptySet()
    }

    /**
     * Screens whose live correctness needs a **full serialized sync pass** (flush the outbox +
     * pull), not a pull-only re-pull (ANDSYNC-009). The bingo card is fed by locally-queued
     * offline `plate.*` writes, so a device parked on it must push its own queued marks and pull
     * other devices' marks on the foreground cadence — a read-model re-pull would do neither
     * (ANDBNG-006). Every other screen's read model is server-owned (journal is flushed at post
     * time) or server-arbitrated (game moves), so they keep the cheap re-pull.
     */
    fun requiresFullSync(screen: Screen): Boolean = screen == Screen.BINGO
}
