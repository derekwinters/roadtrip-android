package com.roadtrip.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.roadtrip.app.di.AppContainer
import com.roadtrip.app.location.TrackerService
import com.roadtrip.app.ui.common.Avatar
import com.roadtrip.app.ui.common.SectionHeader
import com.roadtrip.core.api.Profile
import com.roadtrip.core.common.DeviceClass
import com.roadtrip.core.common.Role
import com.roadtrip.core.location.EnableResult
import com.roadtrip.core.location.PermissionRequester
import com.roadtrip.core.location.TrackerEnabler
import com.roadtrip.core.location.TrackerGate
import com.roadtrip.core.location.TrackerGateResult
import com.roadtrip.core.profiles.FeatureVisibilityRules
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/**
 * Settings (docs/spec/07-settings.md). Parents: server address, the phone tracker toggle
 * (gated by the core TrackerGate so kids/tablets never reach the permission flow,
 * ANDLOC-003/005), detection tuning via the core ConfigForm (ANDSET-001/002), and profile
 * administration (ANDSET-005). Kids see only the server address and app version.
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    profile: Profile,
    deviceClass: DeviceClass,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val visibility = FeatureVisibilityRules.forRole(profile.role)
    val online by container.onlineMonitor.online.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // ---- connection (everyone; kids read-only per spec 07) --------------------------
        SectionHeader("Connection")
        val serverUrl by container.settings.serverUrl.collectAsState()
        if (profile.role == Role.PARENT) {
            var urlText by remember { mutableStateOf(serverUrl) }
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text("Server address") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = { container.settings.setServerUrl(urlText) },
                enabled = urlText.isNotBlank() && urlText != serverUrl,
            ) {
                Text("Save server address")
            }
        } else {
            Text("Server: $serverUrl", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            if (online) "Server reachable" else "Server unreachable (offline)",
            style = MaterialTheme.typography.labelMedium,
            color = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Text("App version: ${appVersion(context)}", style = MaterialTheme.typography.labelMedium)

        // ---- tracker toggle: parent profiles on phone-class devices only ------------------
        if (visibility.trackerToggle && TrackerGate.evaluate(profile.role, deviceClass) is TrackerGateResult.Available) {
            SectionHeader("Location tracking")
            val trackerEnabled by container.settings.trackerEnabled.collectAsState()
            val gpsWarning by container.trackerGpsWarning.collectAsState()
            var enableError by remember { mutableStateOf<String?>(null) }

            val permissionRequester = remember { LauncherPermissionRequester() }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { grants ->
                permissionRequester.onResult(grants[Manifest.permission.ACCESS_FINE_LOCATION] == true)
            }
            permissionRequester.alreadyGranted = {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            }
            permissionRequester.launch = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = trackerEnabled,
                    onCheckedChange = { wanted ->
                        if (!wanted) {
                            container.settings.setTrackerEnabled(false)
                            TrackerService.stop(context)
                        } else {
                            enableError = null
                            scope.launch {
                                val enabler = TrackerEnabler(permissionRequester)
                                when (val result = enabler.requestEnable(profile.role, deviceClass)) {
                                    EnableResult.Enabled -> {
                                        container.settings.setTrackerEnabled(true)
                                        TrackerService.start(context)
                                    }
                                    EnableResult.PermissionDenied ->
                                        enableError = "Location permission is needed to track the trip."
                                    is EnableResult.NotAvailable ->
                                        enableError = "Tracking is only for parents on a phone (${result.reason})."
                                }
                            }
                        }
                    },
                )
                Spacer(Modifier.width(12.dp))
                Text("This phone is the trip tracker")
            }
            Text(
                "Reports the family position every few minutes while driving; " +
                    "a persistent notification shows while tracking is active.",
                style = MaterialTheme.typography.labelSmall,
            )
            enableError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (gpsWarning) {
                Text(
                    "GPS has failed several times in a row — check that location is on.",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // ---- detection tuning (parents, ANDSET-001/002) ------------------------------------
        if (visibility.settingsTuning) {
            SectionHeader("Detection tuning")
            ConfigTuningForm(container, profile.role)
        }

        // ---- profile administration (parents, ANDSET-005) ----------------------------------
        if (visibility.profileAdmin) {
            SectionHeader("Profiles")
            ProfileAdminSection(container)
        }

        // ---- switch profile -------------------------------------------------------------------
        SectionHeader("Profile")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(profile.avatar, profile.name)
            Spacer(Modifier.width(12.dp))
            Text(profile.name, modifier = Modifier.weight(1f))
            TextButton(onClick = { container.selectProfile(null) }) {
                Text("Switch profile")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Suspend permission port bridged to the Compose activity-result launcher (ANDLOC-003). */
private class LauncherPermissionRequester : PermissionRequester {
    var alreadyGranted: () -> Boolean = { false }
    var launch: () -> Unit = {}
    private var pending: CompletableDeferred<Boolean>? = null

    override suspend fun requestLocationPermissions(): Boolean {
        if (alreadyGranted()) return true
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        launch()
        return deferred.await()
    }

    fun onResult(granted: Boolean) {
        pending?.complete(granted)
        pending = null
    }
}

private fun appVersion(context: android.content.Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
} catch (e: Exception) {
    "?"
}
