package com.roadtrip.core.games

/**
 * Pure request → confirm/cancel state machine guarding a resign / host-end action (ANDGAME-023).
 *
 * Resigning is destructive and irreversible: it hits `POST /api/games/{id}/resign` and ends the
 * game for BOTH players, so an accidental tap must never throw the game. Unlike the ANDGAME-022
 * move-confirm toggle this gate is ALWAYS on — there is no setting and no bypass. Every board
 * end-of-game control (ANDGAME-018) — the plain "Resign" ([EndControl.RESIGN]) and the hangman
 * host's "End game" ([EndControl.END_GAME]) — routes through here:
 *
 * - [request] opens the confirmation (sets [pending]); it issues no resign request on its own.
 * - [confirm] invokes [onResign] exactly once and returns to idle (no-op when nothing is pending).
 * - [cancel] closes the confirmation without resigning, leaving the game untouched.
 *
 * Kept framework-free so BoardScreen stays thin and the gating is JVM-testable.
 */
class ResignConfirmation(
    private val onResign: () -> Unit,
) {
    /** True while the confirmation dialog is open awaiting an explicit confirm/cancel. */
    var pending: Boolean = false
        private set

    /** Tap the end control: open the confirmation. No resign request is issued yet. */
    fun request() {
        pending = true
    }

    /** Explicit confirm: run the resign action exactly once and close. No-op if nothing pending. */
    fun confirm() {
        if (!pending) return
        pending = false
        onResign()
    }

    /** Dismiss the confirmation without resigning; the game is left untouched. */
    fun cancel() {
        pending = false
    }
}

/**
 * Wording for the resign / host-end confirmation dialog (ANDGAME-023), keyed off the board's
 * end control kind so the hangman host reads "End game" while everyone else reads "Resign".
 * Both actions are irreversible, so the body always warns it can't be undone. Kept pure so the
 * Compose dialog stays a thin renderer.
 */
object ResignPrompts {
    fun title(control: EndControl): String = when (control) {
        EndControl.END_GAME -> "End game?"
        else -> "Resign this game?"
    }

    fun confirmLabel(control: EndControl): String = when (control) {
        EndControl.END_GAME -> "End game"
        else -> "Resign"
    }

    fun body(control: EndControl): String = "This can't be undone."
}
