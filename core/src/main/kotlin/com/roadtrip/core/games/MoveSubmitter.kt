package com.roadtrip.core.games

import com.roadtrip.core.api.ApiException
import com.roadtrip.core.api.Game
import com.roadtrip.core.api.RoadtripApi
import java.io.IOException
import kotlinx.serialization.json.JsonElement

sealed class MoveOutcome {
    data class Applied(val game: Game) : MoveOutcome()

    /** Server rejected the move (400/409); UI state was restored to [restored]. */
    data class Rejected(val reason: String, val restored: Game) : MoveOutcome()

    /** Transport failure — game actions are online-only, state restored. */
    data class NetworkFailure(val restored: Game) : MoveOutcome()
}

/**
 * Optimistic move submission: the board shows the optimistic state immediately; a rejected
 * move (400/409) restores the exact pre-move state with the server's reason (ANDGAME-003).
 */
class MoveSubmitter(
    private val api: RoadtripApi,
    initial: Game,
) {
    var current: Game = initial
        private set

    var lastRejectionReason: String? = null
        private set

    suspend fun submit(move: JsonElement, optimistic: (Game) -> Game): MoveOutcome {
        val preMove = current
        current = optimistic(preMove) // the board updates immediately
        lastRejectionReason = null

        return try {
            val serverGame = api.submitMove(preMove.id, move)
            current = serverGame
            MoveOutcome.Applied(serverGame)
        } catch (e: ApiException) {
            if (e.status == 400 || e.status == 409) {
                current = preMove // exact pre-move state, not a re-derivation
                lastRejectionReason = e.message
                MoveOutcome.Rejected(e.message ?: "rejected", preMove)
            } else {
                current = preMove
                lastRejectionReason = e.message
                throw e
            }
        } catch (e: IOException) {
            current = preMove
            MoveOutcome.NetworkFailure(preMove)
        }
    }
}
