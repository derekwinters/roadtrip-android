package com.roadtrip.core.location

import com.roadtrip.core.common.DeviceClass
import com.roadtrip.core.common.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

private class RecordingPermissionRequester(private val grant: Boolean = true) : PermissionRequester {
    var requests = 0
        private set

    override suspend fun requestLocationPermissions(): Boolean {
        requests++
        return grant
    }
}

class TrackerGateTest {

    @Test
    fun `only parent profiles on phones may enable the tracker ANDLOC-003`() = runTest {
        assertIs<TrackerGateResult.Available>(TrackerGate.evaluate(Role.PARENT, DeviceClass.PHONE))

        val kid = assertIs<TrackerGateResult.NotAvailable>(TrackerGate.evaluate(Role.KID, DeviceClass.PHONE))
        assertEquals(TrackerGate.REASON_PARENT_REQUIRED, kid.reason)

        val tablet = assertIs<TrackerGateResult.NotAvailable>(TrackerGate.evaluate(Role.PARENT, DeviceClass.TABLET))
        assertEquals(TrackerGate.REASON_PHONE_REQUIRED, tablet.reason)
    }

    @Test
    fun `enabling requires granted location permission ANDLOC-003`() = runTest {
        val granted = RecordingPermissionRequester(grant = true)
        assertIs<EnableResult.Enabled>(TrackerEnabler(granted).requestEnable(Role.PARENT, DeviceClass.PHONE))
        assertEquals(1, granted.requests)

        val denied = RecordingPermissionRequester(grant = false)
        assertIs<EnableResult.PermissionDenied>(TrackerEnabler(denied).requestEnable(Role.PARENT, DeviceClass.PHONE))
    }

    @Test
    fun `kid profiles and tablets never reach the permission request path ANDLOC-005`() = runTest {
        val requester = RecordingPermissionRequester()
        val enabler = TrackerEnabler(requester)

        assertIs<EnableResult.NotAvailable>(enabler.requestEnable(Role.KID, DeviceClass.PHONE))
        assertIs<EnableResult.NotAvailable>(enabler.requestEnable(Role.PARENT, DeviceClass.TABLET))
        assertIs<EnableResult.NotAvailable>(enabler.requestEnable(Role.KID, DeviceClass.TABLET))

        // The runtime permission flow was never touched (manifest permission stays dormant).
        assertEquals(0, requester.requests)
    }
}
