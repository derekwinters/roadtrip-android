package com.roadtrip.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.roadtrip.app.di.AppContainer
import com.roadtrip.app.ui.common.Avatar
import com.roadtrip.core.api.Profile
import com.roadtrip.core.api.ProfilePatch
import com.roadtrip.core.common.Role
import com.roadtrip.core.common.SystemClock
import com.roadtrip.core.sync.SyncTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Parent profile administration (ANDSET-005, backend PRO-002): create and edit profiles
 * (name, avatar, role). Rendered only inside the parent-gated settings section (AND-003).
 */
@Composable
fun ProfileAdminSection(container: AppContainer) {
    val tick by container.refreshTick.collectAsState()
    val scope = rememberCoroutineScope()
    val profiles = remember(tick) { container.profilesCache.read()?.value.orEmpty() }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        for (profile in profiles) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Avatar(profile.avatar, profile.name, size = 32)
                Spacer(Modifier.width(12.dp))
                Text(profile.name, modifier = Modifier.weight(1f))
                Text(
                    if (profile.role == Role.PARENT) "parent" else "kid",
                    style = MaterialTheme.typography.labelMedium,
                )
                IconButton(onClick = { editing = profile }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${profile.name}")
                }
            }
        }
        TextButton(onClick = { creating = true }) {
            Text("Add profile")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    fun refreshProfiles() {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { container.profilesCache.write(container.api.getProfiles(), SystemClock.now()) }
            }
            container.bumpTick()
            container.requestSync(SyncTrigger.POST_WRITE)
        }
    }

    if (creating) {
        ProfileEditDialog(
            title = "New profile",
            initial = null,
            onDismiss = { creating = false },
            onSave = { name, avatar, role ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { container.api.createProfile(name, avatar.ifBlank { null }, role) }
                    }
                    result.fold(
                        onSuccess = {
                            creating = false
                            error = null
                            refreshProfiles()
                        },
                        onFailure = { error = it.message ?: "Could not create the profile" },
                    )
                }
            },
        )
    }

    editing?.let { profile ->
        ProfileEditDialog(
            title = "Edit ${profile.name}",
            initial = profile,
            onDismiss = { editing = null },
            onSave = { name, avatar, role ->
                scope.launch {
                    val patch = ProfilePatch(
                        name = name.takeIf { it != profile.name },
                        avatar = avatar.takeIf { it != profile.avatar && it.isNotBlank() },
                        role = role.takeIf { it != profile.role },
                    )
                    val result = withContext(Dispatchers.IO) {
                        runCatching { container.api.updateProfile(profile.id, patch) }
                    }
                    result.fold(
                        onSuccess = { updated ->
                            editing = null
                            error = null
                            // Keep the signed-in profile record fresh if it was edited.
                            if (container.settings.get()?.id == updated.id) {
                                container.settings.set(updated)
                            }
                            refreshProfiles()
                        },
                        onFailure = { error = it.message ?: "Could not update the profile" },
                    )
                }
            },
        )
    }
}

@Composable
private fun ProfileEditDialog(
    title: String,
    initial: Profile?,
    onDismiss: () -> Unit,
    onSave: (name: String, avatar: String, role: Role) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var avatar by remember { mutableStateOf(initial?.avatar ?: "") }
    var role by remember { mutableStateOf(initial?.role ?: Role.KID) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
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
                Spacer(Modifier.padding(4.dp))
                Row {
                    FilterChip(
                        selected = role == Role.KID,
                        onClick = { role = Role.KID },
                        label = { Text("Kid") },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    FilterChip(
                        selected = role == Role.PARENT,
                        onClick = { role = Role.PARENT },
                        label = { Text("Parent") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), avatar.trim(), role) },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
