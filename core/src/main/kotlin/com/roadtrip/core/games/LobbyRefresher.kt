package com.roadtrip.core.games

import com.roadtrip.core.api.Game

/**
 * Shared reload action behind both lobby refresh affordances — pull-to-refresh and the header
 * refresh button (ANDGAME-010). Keeping the wiring here (rather than in the composable) means the
 * online gate and the sync/refetch sequence are unit-testable.
 *
 * Dependencies are injected as lambdas so the container can supply the real sync trigger, API and
 * cache while tests supply fakes.
 */
class LobbyRefresher(
    private val isOnline: () -> Boolean,
    private val requestForegroundSync: () -> Unit,
    private val fetchGames: suspend () -> List<Game>,
    private val storeGames: (List<Game>) -> Unit,
) {
    /**
     * Reloads the lobby. Returns `true` when a network reload ran, `false` when it was skipped
     * because we're offline — the caller dismisses the indicator either way so it never spins
     * indefinitely. A failed refetch still counts as a run (the foreground sync was requested and
     * the cache is left untouched) so the indicator dismisses cleanly.
     */
    suspend fun reload(): Boolean {
        if (!isOnline()) return false
        requestForegroundSync()
        runCatching { fetchGames() }.onSuccess { storeGames(it) }
        return true
    }
}
