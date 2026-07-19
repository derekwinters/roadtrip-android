package com.roadtrip.core.location

import com.roadtrip.core.common.Role
import com.roadtrip.core.storage.InMemoryTrackerConfigStore
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

private class RecordingPermissionRequester(private val grant: Boolean = true) : PermissionRequester {
    var requests = 0
        private set

    override suspend fun requestLocationPermissions(): Boolean {
        requests++
        return grant
    }
}

private class RecordingBatteryExempter : BatteryOptimizationExempter {
    var requests = 0
        private set

    override fun requestExemptionIfNeeded() {
        requests++
    }
}

class TrackerGateTest {
    private val config = InMemoryTrackerConfigStore()

    @Test
    fun `parent profiles may enable the tracker on any device class ANDLOC-003`() = runTest {
        // The gate is role-only — there is no device-class check anymore, so the same
        // parent flow works on phones and tablets alike.
        assertIs<TrackerGateResult.Available>(TrackerGate.evaluate(Role.PARENT))

        val kid = assertIs<TrackerGateResult.NotAvailable>(TrackerGate.evaluate(Role.KID))
        assertEquals(TrackerGate.REASON_PARENT_REQUIRED, kid.reason)

        val requester = RecordingPermissionRequester(grant = true)
        assertIs<EnableResult.Enabled>(TrackerEnabler(requester, config).requestEnable(TestData.parent))
        assertEquals(1, requester.requests)
    }

    @Test
    fun `enabling requires granted location permission ANDLOC-003`() = runTest {
        val denied = RecordingPermissionRequester(grant = false)

        assertIs<EnableResult.PermissionDenied>(TrackerEnabler(denied, config).requestEnable(TestData.parent))

        // A denied enable never records an enabling parent.
        assertNull(config.enabledBy())
    }

    @Test
    fun `enabling records the enabling parent profile id ANDLOC-003`() = runTest {
        val requester = RecordingPermissionRequester(grant = true)

        assertIs<EnableResult.Enabled>(TrackerEnabler(requester, config).requestEnable(TestData.parent))

        // covers: ANDLOC-008 — this stored id is the attribution source for every ping.
        assertEquals(TestData.parent.id, config.enabledBy())

        config.setEnabledBy(null) // disabling clears the record
        assertNull(config.enabledBy())
    }

    @Test
    fun `kid profiles never reach the permission request path ANDLOC-005`() = runTest {
        val requester = RecordingPermissionRequester()
        val battery = RecordingBatteryExempter()
        val enabler = TrackerEnabler(requester, config, battery)

        val result = enabler.requestEnable(TestData.kid)

        assertIs<EnableResult.NotAvailable>(result)
        assertEquals(TrackerGate.REASON_PARENT_REQUIRED, result.reason)
        // The runtime permission flow was never touched (manifest permission stays dormant).
        assertEquals(0, requester.requests)
        // covers: ANDLOC-011 — the battery-optimization prompt is inside the parent flow, so
        // a kid never triggers it either.
        assertEquals(0, battery.requests)
        assertNull(config.enabledBy())
    }

    @Test
    fun `enabling requests a battery-optimization exemption in the parent flow ANDLOC-011`() = runTest {
        val battery = RecordingBatteryExempter()

        assertIs<EnableResult.Enabled>(
            TrackerEnabler(RecordingPermissionRequester(grant = true), config, battery)
                .requestEnable(TestData.parent),
        )
        assertEquals(1, battery.requests)
    }

    @Test
    fun `a denied enable never requests a battery-optimization exemption ANDLOC-011`() = runTest {
        val battery = RecordingBatteryExempter()

        assertIs<EnableResult.PermissionDenied>(
            TrackerEnabler(RecordingPermissionRequester(grant = false), config, battery)
                .requestEnable(TestData.parent),
        )
        // No location grant means the service never starts, so no exemption is requested.
        assertEquals(0, battery.requests)
    }
}
