package com.roadtrip.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadtrip.app.data.AppSettings
import com.roadtrip.app.di.AppContainer
import com.roadtrip.app.ui.common.Avatar
import com.roadtrip.core.api.Profile
import com.roadtrip.core.common.Role
import com.roadtrip.core.common.SystemClock
import com.roadtrip.core.profiles.FirstRunCreateResult
import com.roadtrip.core.profiles.ProfilePicker
import com.roadtrip.core.profiles.ProfilePickerLoader
import com.roadtrip.core.profiles.ProfilePickerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Launch profile picker (AND-001): avatars from GET /api/profiles, tapping one selects it
 * — no passwords anywhere. Falls back to the cached profile list offline. A zero-profile
 * server shows the first-run "Set up your family" flow instead of the grid (AND-007). The
 * server address is editable right here, before sign-in (AND-008): a header affordance in
 * every state, plus retry and address editing in the unreachable state; address changes
 * and connectivity returning re-probe immediately.
 */
@Composable
fun ProfilePickerScreen(container: AppContainer) {
    val picker = remember { ProfilePicker(container.api, container.settings) }
    val loader = remember { ProfilePickerLoader(picker, container.profilesCache, SystemClock) }
    var state by remember { mutableStateOf<ProfilePickerState>(ProfilePickerState.Loading) }
    var retryTick by remember { mutableStateOf(0) }
    var showServerDialog by remember { mutableStateOf(false) }
    val serverUrl by container.settings.serverUrl.collectAsState()
    val online by container.onlineMonitor.online.collectAsState()

    // First composition, every server-address change, and every explicit Retry re-probe
    // GET /api/profiles immediately (AND-008); the AppContainer swaps the HTTP client
    // whenever the url setting changes, so the restarted effect probes the new address.
    LaunchedEffect(serverUrl, retryTick) {
        state = ProfilePickerState.Loading
        state = withContext(Dispatchers.IO) { loader.refresh() }
    }
    // Connectivity returning re-probes a dead-ended picker, silently — no Loading flash
    // (AND-008; `online` flips via /api/health on connectivity-regained syncs, AND-006).
    LaunchedEffect(online) {
        if (online && state is ProfilePickerState.Unreachable) {
            state = withContext(Dispatchers.IO) { loader.refresh() }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Always-visible server-settings affordance (AND-008): even a picker pointed at
        // the WRONG server — grid or setup flow included — must be escapable.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { showServerDialog = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Server address")
            }
        }
        Spacer(Modifier.height(8.dp))

        val current = state
        if (current is ProfilePickerState.SetupRequired) {
            // A closed bootstrap with profiles on re-probe drops the wizard for the
            // ordinary select-only grid (AND-009).
            FirstRunSetup(picker, container, onProfilesAppeared = { state = ProfilePickerState.Grid(it) })
        } else {
            Text("Who are you?", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            when {
                current is ProfilePickerState.Unreachable -> UnreachableActions(
                    serverUrl = serverUrl,
                    onRetry = { retryTick++ },
                    onEditServer = { showServerDialog = true },
                )
                current is ProfilePickerState.Loading -> CircularProgressIndicator()
                // Select-only avatar grid: no create affordance once profiles exist (AND-007).
                current is ProfilePickerState.Grid -> ProfileGrid(container, current.profiles)
                else -> {}
            }
        }
    }

    if (showServerDialog) {
        ServerAddressDialog(
            currentUrl = serverUrl,
            onSave = { url ->
                showServerDialog = false
                container.settings.setServerUrl(url)
                // Saving re-probes even when the address is unchanged (AND-008): a
                // same-value save never re-emits the url StateFlow, so bump the tick.
                retryTick++
            },
            onDismiss = { showServerDialog = false },
        )
    }
}

/**
 * Unreachable state (AND-008): the fresh-install fix for the chicken-and-egg dead end —
 * no profile can load without the server, so the escape hatches live right here.
 */
@Composable
private fun UnreachableActions(
    serverUrl: String,
    onRetry: () -> Unit,
    onEditServer: () -> Unit,
) {
    Text(
        "Can't reach the trip server yet — check the connection or the server address.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(8.dp))
    Text("Server: $serverUrl", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(16.dp))
    Button(onClick = onRetry) { Text("Retry") }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onEditServer) { Text("Change server address") }
}

/**
 * Pre-sign-in server-address editor (AND-008): prefilled from the current setting.
 * Saving persists via [AppSettings.setServerUrl] (which trims and ignores blank) and the
 * caller re-probes immediately.
 */
@Composable
private fun ServerAddressDialog(
    currentUrl: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(currentUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server address") },
        text = {
            Column {
                Text("Where this device looks for the family trip server.")
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server address") },
                    singleLine = true,
                    supportingText = {
                        Text(
                            "Car hotspot default: ${AppSettings.DEFAULT_SERVER_URL} — on " +
                                "the Android emulator use http://10.0.2.2:8080 to reach " +
                                "the host machine.",
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(url) }, enabled = url.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ProfileGrid(container: AppContainer, profiles: List<Profile>) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(profiles, key = { it.id }) { profile ->
            Card(onClick = { container.selectProfile(profile) }) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(profile.avatar, profile.name, size = 64)
                    Spacer(Modifier.height(8.dp))
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (profile.role == Role.PARENT) "Parent" else "Kid",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * First-run family setup (AND-007): creates the first profile — parent role enforced by
 * the flow, matching the backend's zero-profiles bootstrap — and signs in as them. Name
 * only: no avatar input, the server assigns its default. Only ever shown after the server
 * itself confirmed zero profiles, never while unreachable (AND-008). Create failures are
 * human-readable and keep the wizard retryable; a closed bootstrap that re-probes into a
 * profile list exits to the grid via [onProfilesAppeared] (AND-009).
 */
@Composable
private fun FirstRunSetup(
    picker: ProfilePicker,
    container: AppContainer,
    onProfilesAppeared: (List<Profile>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Text("Set up your family", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text("The first family member is a parent", style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name") },
    )
    Spacer(Modifier.height(16.dp))
    Button(
        enabled = name.isNotBlank() && !creating,
        onClick = {
            creating = true
            error = null
            scope.launch {
                val result = withContext(Dispatchers.IO) { picker.runFirstRunCreate(name.trim()) }
                when (result) {
                    // runFirstRunCreate already signed the new parent in; selectProfile
                    // re-affirms it and kicks off the first sync (AND-002).
                    is FirstRunCreateResult.SignedIn -> container.selectProfile(result.profile)
                    is FirstRunCreateResult.ProfilesAppeared -> onProfilesAppeared(result.profiles)
                    is FirstRunCreateResult.Failed -> {
                        creating = false
                        error = result.message
                    }
                }
            }
        },
    ) {
        Text("Create profile")
    }
    error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error)
    }
}
