package com.roadtrip.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.roadtrip.app.location.TrackerService
import com.roadtrip.core.location.TrackerRestartPolicy
import com.roadtrip.app.notifications.NavTargetExtras
import com.roadtrip.app.ui.AppShell
import com.roadtrip.app.ui.profile.ProfilePickerScreen
import com.roadtrip.core.journal.NavTarget
import com.roadtrip.core.sync.SyncTrigger
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val container get() = (application as RoadtripApplication).container

    /** Deep-link target from a tapped notification, consumed by the shell (ANDNOTIF-005). */
    private val pendingNavTarget = MutableStateFlow<NavTarget?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNavTarget.value = NavTargetExtras.fromIntent(intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            MaterialTheme {
                Surface {
                    val profile by container.settings.selectedProfile.collectAsState()
                    val current = profile
                    if (current == null) {
                        // AND-001: launch shows the profile picker until one is selected.
                        ProfilePickerScreen(container)
                    } else {
                        AppShell(
                            container = container,
                            profile = current,
                            pendingNavTarget = pendingNavTarget,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        NavTargetExtras.fromIntent(intent)?.let { pendingNavTarget.value = it }
    }

    override fun onStart() {
        super.onStart()
        container.activityVisible.value = true
        container.requestSync(SyncTrigger.FOREGROUND)
        // Restore the tracker after a relaunch if a parent left it enabled (ANDLOC-006).
        // The signed-in profile is irrelevant: pings stay attributed to the enabling
        // parent (ANDLOC-008), and the tracker never runs between trips (09-trips.md).
        if (TrackerRestartPolicy.shouldRun(
                trackerEnabled = container.settings.trackerEnabled.value,
                trackerMayRun = container.trackerMayRun(),
            )
        ) {
            TrackerService.start(this)
        }
    }

    override fun onStop() {
        container.activityVisible.value = false
        super.onStop()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
