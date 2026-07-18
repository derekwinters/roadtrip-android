package com.roadtrip.core.games

data class GameActionsGate(val enabled: Boolean, val reason: String?)

/**
 * Game actions are online-only (server arbitrates turns). Offline, actions are disabled
 * with an explanatory reason for the banner; cached replays keep working (ANDGAME-008,
 * AND-005).
 */
object GameOfflineGate {
    const val OFFLINE_REASON = "Game moves need a connection to the trip server"

    fun evaluate(online: Boolean): GameActionsGate =
        if (online) GameActionsGate(enabled = true, reason = null)
        else GameActionsGate(enabled = false, reason = OFFLINE_REASON)
}
