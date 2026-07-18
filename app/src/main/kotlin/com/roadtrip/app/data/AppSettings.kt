package com.roadtrip.app.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.roadtrip.core.api.Profile
import com.roadtrip.core.api.RoadtripJson
import com.roadtrip.core.common.UuidIdGenerator
import com.roadtrip.core.storage.SelectedProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.roadtripDataStore by preferencesDataStore(name = "roadtrip_settings")

/**
 * DataStore-backed device settings: server base URL, the selected profile (AND-002:
 * persists across restarts), the tracker toggle, and a stable device id for sync batches.
 *
 * Values are loaded once at startup (tiny file) and mirrored in StateFlows so the
 * synchronous core ports ([SelectedProfileStore]) and Compose UI read without blocking;
 * writes persist asynchronously on [scope].
 */
class AppSettings(
    private val context: Context,
    private val scope: CoroutineScope,
) : SelectedProfileStore {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val SELECTED_PROFILE = stringPreferencesKey("selected_profile_json")
        val TRACKER_ENABLED = booleanPreferencesKey("tracker_enabled")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }

    private val initial: Preferences = runBlocking { context.roadtripDataStore.data.first() }

    private val _serverUrl = MutableStateFlow(initial[Keys.SERVER_URL] ?: DEFAULT_SERVER_URL)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _selectedProfile = MutableStateFlow(
        initial[Keys.SELECTED_PROFILE]?.let { json ->
            try {
                RoadtripJson.decodeFromString(Profile.serializer(), json)
            } catch (e: Exception) {
                null
            }
        },
    )
    val selectedProfile: StateFlow<Profile?> = _selectedProfile.asStateFlow()

    private val _trackerEnabled = MutableStateFlow(initial[Keys.TRACKER_ENABLED] ?: false)
    val trackerEnabled: StateFlow<Boolean> = _trackerEnabled.asStateFlow()

    /** Stable per-install id sent as device_id in sync batches. */
    val deviceId: String = initial[Keys.DEVICE_ID] ?: UuidIdGenerator.newId().also { id ->
        persist { it[Keys.DEVICE_ID] = id }
    }

    fun setServerUrl(url: String) {
        val cleaned = url.trim()
        if (cleaned.isEmpty()) return
        _serverUrl.value = cleaned
        persist { it[Keys.SERVER_URL] = cleaned }
    }

    fun setTrackerEnabled(enabled: Boolean) {
        _trackerEnabled.value = enabled
        persist { it[Keys.TRACKER_ENABLED] = enabled }
    }

    // ---- SelectedProfileStore ----------------------------------------------------------

    override fun get(): Profile? = _selectedProfile.value

    override fun set(profile: Profile?) {
        _selectedProfile.value = profile
        persist { prefs ->
            if (profile == null) {
                prefs.remove(Keys.SELECTED_PROFILE)
            } else {
                prefs[Keys.SELECTED_PROFILE] = RoadtripJson.encodeToString(Profile.serializer(), profile)
            }
        }
    }

    private fun persist(mutate: (MutablePreferences) -> Unit) {
        scope.launch {
            context.roadtripDataStore.edit { mutate(it) }
        }
    }

    companion object {
        /** Car-hotspot VPN default; editable in settings (docs/spec/07-settings.md). */
        const val DEFAULT_SERVER_URL = "http://10.0.0.2:8080"
    }
}
