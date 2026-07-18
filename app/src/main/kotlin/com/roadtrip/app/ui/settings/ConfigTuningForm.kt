package com.roadtrip.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadtrip.app.di.AppContainer
import com.roadtrip.core.api.Config
import com.roadtrip.core.api.ConfigPatch
import com.roadtrip.core.common.Role
import com.roadtrip.core.settings.ConfigBounds
import com.roadtrip.core.settings.ConfigForm
import com.roadtrip.core.settings.SaveResult
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Detection tuning (ANDSET-001): loads GET /api/config through the core ConfigForm,
 * validates edits against the documented bounds client-side, saves via PUT /api/config;
 * failures restore the last known server values with the reason shown (ANDSET-002).
 */
@Composable
fun ConfigTuningForm(container: AppContainer, role: Role) {
    val scope = rememberCoroutineScope()
    val form = remember { ConfigForm(container.api, role) }
    var loaded by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var pingText by remember { mutableStateOf("") }
    var stopRadiusText by remember { mutableStateOf("") }
    var stopDurationText by remember { mutableStateOf("") }
    var arrivalRadiusText by remember { mutableStateOf("") }
    var cityRadiusText by remember { mutableStateOf("") }

    fun syncFieldsFromForm() {
        val values = form.values ?: return
        pingText = values.pingIntervalS.toString()
        stopRadiusText = fmt(values.stopRadiusM)
        stopDurationText = fmt(values.minStopDurationMin)
        arrivalRadiusText = fmt(values.arrivalRadiusM)
        cityRadiusText = fmt(values.cityRadiusKm)
    }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { runCatching { form.load() } }
        loaded = result.isSuccess
        loadFailed = result.isFailure
        if (result.isSuccess) syncFieldsFromForm()
    }

    if (loadFailed) {
        Text(
            "Couldn't load the current config — try again when the server is reachable.",
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    if (!loaded) {
        Text("Loading config…")
        return
    }

    val parsedPing = pingText.toIntOrNull()
    val parsedStopRadius = stopRadiusText.toDoubleOrNull()
    val parsedStopDuration = stopDurationText.toDoubleOrNull()
    val parsedArrival = arrivalRadiusText.toDoubleOrNull()
    val parsedCity = cityRadiusText.toDoubleOrNull()
    val allParsed = parsedPing != null && parsedStopRadius != null && parsedStopDuration != null &&
        parsedArrival != null && parsedCity != null

    val boundsErrors = if (allParsed) {
        ConfigBounds.validate(
            ConfigPatch(
                pingIntervalS = parsedPing,
                stopRadiusM = parsedStopRadius,
                minStopDurationMin = parsedStopDuration,
                arrivalRadiusM = parsedArrival,
                cityRadiusKm = parsedCity,
            ),
        )
    } else {
        listOf("All values must be numbers")
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        NumberField("Ping interval (s)", pingText) { pingText = it }
        NumberField("Stop radius (m)", stopRadiusText) { stopRadiusText = it }
        NumberField("Min stop duration (min)", stopDurationText) { stopDurationText = it }
        NumberField("Arrival radius (m)", arrivalRadiusText) { arrivalRadiusText = it }
        NumberField("City radius (km)", cityRadiusText) { cityRadiusText = it }

        boundsErrors.forEach { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }

        TextButton(
            enabled = allParsed && boundsErrors.isEmpty() && form.canSave,
            onClick = {
                statusMessage = null
                form.edit {
                    Config(
                        pingIntervalS = parsedPing!!,
                        stopRadiusM = parsedStopRadius!!,
                        minStopDurationMin = parsedStopDuration!!,
                        arrivalRadiusM = parsedArrival!!,
                        cityRadiusKm = parsedCity!!,
                    )
                }
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        try {
                            form.save()
                        } catch (e: Exception) {
                            SaveResult.Failed(e.message ?: "save failed")
                        }
                    }
                    statusMessage = when (result) {
                        is SaveResult.Saved -> "Saved."
                        is SaveResult.ValidationFailed -> result.errors.joinToString()
                        is SaveResult.Failed -> "Not saved: ${result.reason} — values restored."
                        SaveResult.NotAllowed -> "Only parents can change tuning."
                    }
                    // On failure the form restored the last server values (ANDSET-002).
                    syncFieldsFromForm()
                }
            },
        ) {
            Text("Save tuning")
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}

private fun fmt(value: Double): String =
    if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
