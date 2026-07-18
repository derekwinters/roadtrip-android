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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadtrip.app.di.AppContainer
import com.roadtrip.app.ui.common.Avatar
import com.roadtrip.core.api.Profile
import com.roadtrip.core.common.Role
import com.roadtrip.core.profiles.ProfilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Launch profile picker (AND-001): avatars from GET /api/profiles, tapping one selects it
 * — no passwords anywhere. Falls back to the cached profile list offline.
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
        Text("Who are you?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        val current = profiles
        when {
            current == null && !loadFailed -> CircularProgressIndicator()
            current == null -> Text(
                "Can't reach the trip server yet — check the connection or the server address.",
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(current, key = { it.id }) { profile ->
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
    }
}
