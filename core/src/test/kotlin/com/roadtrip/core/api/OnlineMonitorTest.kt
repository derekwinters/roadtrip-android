package com.roadtrip.core.api

import com.roadtrip.core.testing.FakeRoadtripApi
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class OnlineMonitorTest {
    private val api = FakeRoadtripApi()
    private val monitor = OnlineMonitor(api)

    @Test
    fun `flips online when the health probe succeeds and offline when it fails AND-006`() = runTest {
        assertFalse(monitor.online.value) // unknown reachability starts as offline

        assertTrue(monitor.check())
        assertTrue(monitor.online.value)

        api.offline = true
        assertFalse(monitor.check())
        assertFalse(monitor.online.value)

        // Connectivity regained → re-check flips back.
        api.offline = false
        assertTrue(monitor.onConnectivityChanged())
        assertTrue(monitor.online.value)
    }

    @Test
    fun `sync attempts double as reachability signals AND-006`() = runTest {
        monitor.check()
        assertTrue(monitor.online.value)

        monitor.noteSyncResult(reachedServer = false)
        assertFalse(monitor.online.value)

        monitor.noteSyncResult(reachedServer = true)
        assertTrue(monitor.online.value)
    }
}
