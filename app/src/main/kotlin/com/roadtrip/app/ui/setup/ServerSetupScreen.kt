package com.roadtrip.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roadtrip.app.di.AppContainer
import com.roadtrip.core.settings.ServerAddress
import com.roadtrip.core.settings.ServerAddressResult

/**
 * First-run server-address setup gate (AND-014/AND-015): shown before the profile picker
 * whenever no server address is stored, so a fresh install never talks to an assumed host.
 * The field starts empty — the emulator hint is placeholder/help text only, never a
 * pre-filled working default. Input is validated through the pure [ServerAddress] seam; a
 * malformed address is rejected inline, and a valid one is saved (which flips the gate and
 * advances to the picker). Kept thin: all logic lives in the tested core seam.
 */
@Composable
fun ServerSetupScreen(container: AppContainer) {
    var urlText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Enter server address", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tell this device where your family trip server lives. You can change it later " +
                "in Settings.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = urlText,
            onValueChange = {
                urlText = it
                error = null
            },
            label = { Text("Server address") },
            singleLine = true,
            isError = error != null,
            placeholder = { Text("http://…") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            supportingText = {
                Text(
                    "For example http://192.168.1.10:8080. On the Android emulator use " +
                        "http://10.0.2.2:8080 to reach the host machine.",
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = urlText.isNotBlank(),
            onClick = {
                when (val result = ServerAddress.validate(urlText)) {
                    is ServerAddressResult.Valid -> {
                        // Saving a non-null address flips the gate (AND-014); MainActivity
                        // recomposes to the profile picker.
                        container.settings.setServerUrl(result.normalizedUrl)
                    }
                    is ServerAddressResult.Invalid -> error = result.reason
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue")
        }
    }
}
