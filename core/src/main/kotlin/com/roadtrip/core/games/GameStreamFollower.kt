package com.roadtrip.core.games

import com.roadtrip.core.api.EventDto
import com.roadtrip.core.api.RoadtripApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Long-poll loop over GET /api/games/{id}/events so an opponent's move appears without
 * manual refresh while the game screen is open (ANDGAME-005).
 */
class GameStreamFollower(
    private val api: RoadtripApi,
    private val gameId: String,
    private val onEvent: (EventDto) -> Unit,
    private val waitSeconds: Int = 25,
    startAfter: Long = 0,
) {
    var cursor: Long = startAfter
        private set

    /** One long-poll round trip; applies events and advances the cursor. */
    suspend fun pollOnce(): Int {
        val page = api.getGameEvents(gameId, cursor, waitSeconds)
        page.events.forEach(onEvent)
        cursor = page.nextAfter
        return page.events.size
    }

    /** Poll until the surrounding coroutine is cancelled (screen closed). */
    suspend fun run() {
        while (currentCoroutineContext().isActive) {
            pollOnce()
        }
    }
}
