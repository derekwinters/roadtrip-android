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
import com.roadtrip.core.settings.ServerAddress
import com.roadtrip.core.settings.ServerAddressResult
import com.roadtrip.core.storage.SelectedProfileStore
import com.roadtrip.core.storage.TrackerConfigStore
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
 * persists across restarts), the tracker config (which parent enabled it, ANDLOC-003/008),
 * and a stable device id for sync batches.
 *
 * Values are loaded once at startup (tiny file) and mirrored in StateFlows so the
 * synchronous core ports ([SelectedProfileStore], [TrackerConfigStore]) and Compose UI
 * read without blocking; writes persist asynchronously on [scope].
 */
class AppSettings(
    private val context: Context,
    private val scope: CoroutineScope,
) : SelectedProfileStore, TrackerConfigStore {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val SELECTED_PROFILE = stringPreferencesKey("selected_profile_json")
        val TRACKER_ENABLED = booleanPreferencesKey("tracker_enabled")
        val TRACKER_ENABLED_BY = stringPreferencesKey("tracker_enabled_by")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }

    private val initial: Preferences = runBlocking { context.roadtripDataStore.data.first() }

    // No baked-in default (AND-014): a fresh install has nothing stored, so `serverUrl` stays
    // null until the first-run setup gate saves a validated address. Null/blank means
    // "not configured yet" — the app must prompt before any API call.
    private val _serverUrl = MutableStateFlow(initial[Keys.SERVER_URL])
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

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

    /** Profile id of the parent who enabled the tracker on this device (ANDLOC-003/008). */
    private val _trackerEnabledBy = MutableStateFlow(initial[Keys.TRACKER_ENABLED_BY])
    val trackerEnabledBy: StateFlow<String?> = _trackerEnabledBy.asStateFlow()

    /** Stable per-install id sent as device_id in sync batches. */
    val deviceId: String = initial[Keys.DEVICE_ID] ?: UuidIdGenerator.newId().also { id ->
        persist { it[Keys.DEVICE_ID] = id }
    }

    /**
     * Persists a server address, but only after it validates as a well-formed http(s) URL
     * (AND-015). Invalid input is ignored here — callers validate through the same
     * [ServerAddress] seam first and surface the rejection reason to the user.
     */
    fun setServerUrl(url: String) {
        val result = ServerAddress.validate(url)
        if (result is ServerAddressResult.Valid) {
            _serverUrl.value = result.normalizedUrl
            persist { it[Keys.SERVER_URL] = result.normalizedUrl }
        }
    }

    // ---- TrackerConfigStore --------------------------------------------------------------

    override fun enabledBy(): String? = _trackerEnabledBy.value

    /**
     * Non-null enables the tracker recording the enabling parent; null disables and
     * clears the record (ANDLOC-003/008).
     */
    override fun setEnabledBy(profileId: String?) {
        _trackerEnabledBy.value = profileId
        _trackerEnabled.value = profileId != null
        persist { prefs ->
            prefs[Keys.TRACKER_ENABLED] = profileId != null
            if (profileId == null) {
                prefs.remove(Keys.TRACKER_ENABLED_BY)
            } else {
                prefs[Keys.TRACKER_ENABLED_BY] = profileId
            }
        }
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
}
