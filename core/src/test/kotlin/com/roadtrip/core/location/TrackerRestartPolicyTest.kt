package com.roadtrip.core.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The pure restart/scheduling decisions shared by the task-removed self-restart, the
 * BOOT_COMPLETED receiver, and the Doze fallback alarm. Keeping them here lets the thin
 * Service/receiver wiring stay a straight call into these predicates.
 */
class TrackerRestartPolicyTest {

    @Test
    fun `restart only when enabled by a parent and a trip is active ANDLOC-009`() {
        // covers: ANDLOC-010 — the BOOT_COMPLETED receiver uses the same predicate as the
        // task-removed restart and the relaunch restore, so all restart paths agree.
        assertTrue(TrackerRestartPolicy.shouldRun(trackerEnabled = true, trackerMayRun = true))

        // Disabled tracker: nothing to restart, on swipe-away or after reboot.
        assertFalse(TrackerRestartPolicy.shouldRun(trackerEnabled = false, trackerMayRun = true))

        // Enabled but no trip active (trackerMayRun == false): stay idle, do not restart.
        assertFalse(TrackerRestartPolicy.shouldRun(trackerEnabled = true, trackerMayRun = false))

        assertFalse(TrackerRestartPolicy.shouldRun(trackerEnabled = false, trackerMayRun = false))
    }

    @Test
    fun `doze fallback is exact only with a live trip and exact-alarm permission ANDLOC-012`() {
        // A live trip plus the exact-alarm permission held: use setExactAndAllowWhileIdle so
        // the fallback tick cannot coalesce/drift under Doze (matches 03-location.md).
        assertEquals(
            DozeAlarmPolicy.Mode.EXACT,
            DozeAlarmPolicy.mode(tripActive = true, canScheduleExact = true),
        )

        // Exact alarms are permission-gated on API 31+; without the grant, degrade to the
        // inexact setAndAllowWhileIdle form rather than crash.
        assertEquals(
            DozeAlarmPolicy.Mode.INEXACT,
            DozeAlarmPolicy.mode(tripActive = true, canScheduleExact = false),
        )

        // Between trips the tick never needs to be exact even if the permission is held.
        assertEquals(
            DozeAlarmPolicy.Mode.INEXACT,
            DozeAlarmPolicy.mode(tripActive = false, canScheduleExact = true),
        )
        assertEquals(
            DozeAlarmPolicy.Mode.INEXACT,
            DozeAlarmPolicy.mode(tripActive = false, canScheduleExact = false),
        )
    }
}
