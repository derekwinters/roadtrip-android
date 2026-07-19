package com.roadtrip.core.games

import com.roadtrip.core.api.GameType
import kotlinx.serialization.json.JsonElement

/**
 * Default-resolution seam for the per-profile, per-game-type "confirm move" toggle (ANDGAME-022).
 *
 * The toggle is client-side only. The stored value for a `(profileId, gameType)` pair is the
 * profile's last-set choice for that game type (last-write-wins), or `null` when the pair was
 * never set. A never-set pair defaults to **on** (staging with an explicit confirm), so
 * [resolve] maps `null` to [DEFAULT]. [key] builds the stable local storage key shared by the
 * [com.roadtrip.core.storage.ConfirmMoveStore] port and its app-module DataStore implementation.
 */
object ConfirmMovePreference {
    /** Initial default for any `(profileId, gameType)` never set before: confirm is ON. */
    const val DEFAULT: Boolean = true

    /** Resolve the effective toggle value from what the store holds (null == never set). */
    fun resolve(stored: Boolean?): Boolean = stored ?: DEFAULT

    /** Stable, per-pair storage key: `confirm_move:<profileId>:<gameType>`. */
    fun key(profileId: String, gameType: GameType): String =
        "confirm_move:$profileId:${gameType.name.lowercase()}"
}

/**
 * Pure stage → confirm/cancel state machine for board moves (ANDGAME-022). It sits BEFORE the
 * optimistic [MoveSubmitter]: when [confirmRequired] is false a requested move submits at once;
 * when true it is staged and only submitted after an explicit [confirm]. [cancel] discards the
 * staged move without submitting and asks the board to restore its pre-move state (the same
 * restore idiom as a rejected move, ANDGAME-003). Kept out of Compose so the gating is JVM-testable.
 */
class MoveConfirmGate(
    val confirmRequired: Boolean,
    private val onSubmit: (JsonElement) -> Unit,
    private val onStageChanged: (JsonElement?) -> Unit = {},
    private val onRestore: () -> Unit = {},
) {
    /** The move awaiting confirmation, or null when nothing is staged. */
    var staged: JsonElement? = null
        private set

    /** Board asks to make [move]: submit immediately, or stage it behind a confirm. */
    fun request(move: JsonElement) {
        if (!confirmRequired) {
            onSubmit(move)
            return
        }
        staged = move
        onStageChanged(move)
    }

    /** Confirm the staged move: submit it exactly once and clear staging. No-op if nothing staged. */
    fun confirm() {
        val move = staged ?: return
        staged = null
        onStageChanged(null)
        onSubmit(move)
    }

    /** Discard the staged move without submitting and restore the pre-move board. No-op if nothing staged. */
    fun cancel() {
        if (staged == null) return
        staged = null
        onStageChanged(null)
        onRestore()
    }
}
