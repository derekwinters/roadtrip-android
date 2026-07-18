package com.roadtrip.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadtrip.app.di.AppContainer
import com.roadtrip.app.ui.common.Avatar
import com.roadtrip.core.api.Profile
import com.roadtrip.core.common.Role
import com.roadtrip.core.profiles.ProfilePicker
import com.roadtrip.core.profiles.ProfilePickerReducer
import com.roadtrip.core.profiles.ProfilePickerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Launch profile picker (AND-001): avatars from GET /api/profiles, tapping one selects it
 * — no passwords anywhere. Falls back to the cached profile list offline. A zero-profile
 * server shows the first-run "Set up your family" flow instead of the grid (AND-007).
 */
@Composable
fun ProfilePickerScreen(container: AppContainer) {
    val picker = remember { ProfilePicker(container.api, container.settings) }
    var profiles by remember { mutableStateOf<List<Profile>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            try {
                picker.load().also { container.profilesCache.write(it, com.roadtrip.core.common.SystemClock.now()) }
            } catch (e: Exception) {
                container.profilesCache.read()?.value
            }
        }
        profiles = loaded
        loadFailed = loaded == null
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        val state = ProfilePickerReducer.reduce(profiles)
        if (state is ProfilePickerState.SetupRequired) {
            FirstRunSetup(picker, container)
        } else {
            Text("Who are you?", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            when {
                loadFailed -> Text(
                    "Can't reach the trip server yet — check the connection or the server address.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                state is ProfilePickerState.Loading -> CircularProgressIndicator()
                // Select-only avatar grid: no create affordance once profiles exist (AND-007).
                state is ProfilePickerState.Grid -> ProfileGrid(container, state.profiles)
                else -> {}
            }
        }
    }
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
 * the flow, matching the backend's zero-profiles bootstrap — and signs in as them.
 */
@Composable
private fun FirstRunSetup(picker: ProfilePicker, container: AppContainer) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf("") }
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
    OutlinedTextField(
        value = avatar,
        onValueChange = { avatar = it },
        label = { Text("Avatar (emoji)") },
    )
    Spacer(Modifier.height(16.dp))
    Button(
        enabled = name.isNotBlank() && !creating,
        onClick = {
            creating = true
            error = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { picker.createFirstProfile(name.trim(), avatar.trim().ifBlank { null }) }
                }
                result.fold(
                    // createFirstProfile already signed the new parent in; selectProfile
                    // re-affirms it and kicks off the first sync (AND-002).
                    onSuccess = { container.selectProfile(it) },
                    onFailure = {
                        creating = false
                        error = it.message ?: "Could not create the profile"
                    },
                )
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
