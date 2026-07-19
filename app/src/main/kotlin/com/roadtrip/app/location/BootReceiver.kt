package com.roadtrip.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.roadtrip.app.RoadtripApplication
import com.roadtrip.core.location.TrackerRestartPolicy

/**
 * Resumes trip tracking after a device reboot without the user reopening the app
 * (ANDLOC-010). Mirrors the relaunch-restore logic in `MainActivity.onStart` (ANDLOC-006):
 * it starts the tracker only when a parent left it enabled on this device AND a trip is
 * active — the shared [TrackerRestartPolicy.shouldRun] predicate. Because that gate keys only
 * on device state a parent set, kid profiles are never involved (ANDLOC-005).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> Unit
            else -> return
        }
        val container = (context.applicationContext as RoadtripApplication).container
        if (TrackerRestartPolicy.shouldRun(
                trackerEnabled = container.settings.trackerEnabled.value,
                trackerMayRun = container.trackerMayRun(),
            )
        ) {
            // Android 14+ disallows starting a location-type foreground service from
            // BOOT_COMPLETED; guard so a blocked start can never crash the boot broadcast.
            // The next app open (MainActivity.onStart) still restores tracking on such OSes.
            try {
                TrackerService.start(context)
            } catch (e: Exception) {
                // Swallowed intentionally: boot receivers must not throw.
            }
        }
    }
}
