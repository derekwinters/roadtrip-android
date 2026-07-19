package com.roadtrip.core.sync

import java.time.Duration
import java.time.Instant

/** Outcome of one foreground-refresh evaluation (ANDSYNC-008). */
sealed interface RefreshDecision {
    /** App is backgrounded: suspend the loop until it becomes visible again — never spin. */
    data object Idle : RefreshDecision

    /** Trigger a read-model refresh now (then re-evaluate after a full interval). */
    data object RefreshNow : RefreshDecision

    /** Re-evaluate after this delay: either the remaining interval, or the offline backoff. */
    data class Wait(val delayMillis: Long) : RefreshDecision
}

/**
 * Pure cadence/gating decision for the in-app foreground live-refresh loop (ANDSYNC-008).
 *
 * Kept free of coroutines/flows so the "should we refresh, and when next?" logic is unit-testable
 * on the JVM; the [com.roadtrip.app.di.AppContainer] loop that drives it stays a thin shell.
 */
class ForegroundRefreshPolicy(
    val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    /** How long to wait before re-checking while offline (no network pass is run). */
    val offlineBackoffMillis: Long = intervalMillis,
) {
    fun decide(
        visible: Boolean,
        online: Boolean,
        lastRefreshAt: Instant?,
        now: Instant,
    ): RefreshDecision {
        // Backgrounded wins over everything: the caller suspends until visible again.
        if (!visible) return RefreshDecision.Idle
        // Visible but offline: don't hammer the network — surface offline state and re-check later.
        if (!online) return RefreshDecision.Wait(offlineBackoffMillis)
        if (lastRefreshAt == null) return RefreshDecision.RefreshNow
        val elapsed = Duration.between(lastRefreshAt, now).toMillis()
        return if (elapsed >= intervalMillis) {
            RefreshDecision.RefreshNow
        } else {
            RefreshDecision.Wait(intervalMillis - elapsed)
        }
    }

    companion object {
        /** ~30 s: responsive on-screen updates without hammering the car-hotspot VPN. */
        const val DEFAULT_INTERVAL_MILLIS = 30_000L
    }
}
