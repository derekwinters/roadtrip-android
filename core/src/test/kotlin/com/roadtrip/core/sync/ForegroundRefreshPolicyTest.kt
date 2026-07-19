package com.roadtrip.core.sync

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** covers: ANDSYNC-008 */
class ForegroundRefreshPolicyTest {
    private val policy = ForegroundRefreshPolicy(intervalMillis = 30_000, offlineBackoffMillis = 30_000)
    private val t0 = Instant.parse("2026-07-19T12:00:00Z")

    @Test
    fun `refreshes immediately when visible, online and never refreshed before ANDSYNC-008`() {
        val decision = policy.decide(visible = true, online = true, lastRefreshAt = null, now = t0)
        assertEquals(RefreshDecision.RefreshNow, decision)
    }

    @Test
    fun `waits the remaining interval when refreshed too recently ANDSYNC-008`() {
        val last = t0.minusMillis(10_000) // only 10s ago, interval is 30s
        val decision = policy.decide(visible = true, online = true, lastRefreshAt = last, now = t0)
        assertEquals(RefreshDecision.Wait(20_000), decision)
    }

    @Test
    fun `refreshes once a full interval has elapsed ANDSYNC-008`() {
        val last = t0.minusMillis(30_000)
        val decision = policy.decide(visible = true, online = true, lastRefreshAt = last, now = t0)
        assertEquals(RefreshDecision.RefreshNow, decision)
    }

    @Test
    fun `idles while backgrounded so the loop never spins ANDSYNC-008`() {
        // Backgrounded takes precedence over everything else, including a long-overdue refresh.
        val last = t0.minusMillis(10 * 60_000)
        assertEquals(
            RefreshDecision.Idle,
            policy.decide(visible = false, online = true, lastRefreshAt = last, now = t0),
        )
        assertEquals(
            RefreshDecision.Idle,
            policy.decide(visible = false, online = false, lastRefreshAt = null, now = t0),
        )
    }

    @Test
    fun `backs off without a network pass while offline ANDSYNC-008`() {
        // Visible but offline: never RefreshNow (no hammering), just re-check after the backoff.
        val decision = policy.decide(visible = true, online = false, lastRefreshAt = null, now = t0)
        assertEquals(RefreshDecision.Wait(30_000), decision)
    }
}
