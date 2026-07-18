package com.roadtrip.core.profiles

import com.roadtrip.core.api.Profile
import com.roadtrip.core.api.RoadtripApi
import com.roadtrip.core.common.Clock
import com.roadtrip.core.common.Role
import com.roadtrip.core.storage.CacheStore
import com.roadtrip.core.storage.SelectedProfileStore
import kotlinx.coroutines.CancellationException

/**
 * Launch profile picker: avatars from GET /api/profiles, tapping one selects it — no
 * passwords anywhere; the selection persists across restarts via [SelectedProfileStore]
 * (AND-001/002). A zero-profile server puts the picker into the first-run family setup
 * flow instead of the grid (AND-007).
 */
class ProfilePicker(
    private val api: RoadtripApi,
    private val store: SelectedProfileStore,
) {
    suspend fun load(): List<Profile> = api.getProfiles()

    fun select(profile: Profile) {
        store.set(profile)
    }

    fun selected(): Profile? = store.get()

    /**
     * First-run bootstrap (AND-007): creates the very first profile and signs in as them.
     * The parent role is enforced by the flow — the backend's zero-profiles bootstrap
     * accepts an unauthenticated POST /api/profiles only for role=parent — and the call
     * carries no `X-Profile-Id` because nobody is signed in yet ([RoadtripApi]
     * implementations omit the header when there is no selected profile).
     */
    suspend fun createFirstProfile(name: String, avatar: String?): Profile {
        val created = api.createProfile(name, avatar, Role.PARENT)
        store.set(created)
        return created
    }
}

/** What the launch picker shows (AND-001/007/008). */
sealed class ProfilePickerState {
    /** Profiles not loaded yet (server or cache pending). */
    object Loading : ProfilePickerState()

    /** Zero profiles on the server: first-run "Set up your family" flow (AND-007). */
    object SetupRequired : ProfilePickerState()

    /** Select-only avatar grid — no create affordance once profiles exist (AND-007). */
    data class Grid(val profiles: List<Profile>) : ProfilePickerState()

    /**
     * The server could not be reached and no cached profile list is usable (AND-008):
     * instead of dead-ending, the picker shows the current server address, an explicit
     * Retry, and the pre-sign-in server-address editor. Never rendered as
     * [SetupRequired] — only a live server can confirm zero profiles, and creating the
     * first profile needs the server anyway.
     */
    object Unreachable : ProfilePickerState()
}

object ProfilePickerReducer {
    /**
     * [loadFailed] = the `GET /api/profiles` probe failed and [profiles] is the cached
     * list, if any. A non-empty cache still renders the grid so offline selection keeps
     * working (AND-005); a null or empty cache is [ProfilePickerState.Unreachable],
     * never [ProfilePickerState.SetupRequired] (AND-008).
     */
    fun reduce(profiles: List<Profile>?, loadFailed: Boolean = false): ProfilePickerState = when {
        loadFailed -> if (profiles.isNullOrEmpty()) {
            ProfilePickerState.Unreachable
        } else {
            ProfilePickerState.Grid(profiles)
        }
        profiles == null -> ProfilePickerState.Loading
        profiles.isEmpty() -> ProfilePickerState.SetupRequired
        else -> ProfilePickerState.Grid(profiles)
    }
}

/**
 * Owns the picker's probe → cache → reduce cycle so the Compose screen stays thin
 * (AND-008): a successful probe of `GET /api/profiles` refreshes the cache and reduces
 * per AND-007; a failed one falls back to the cached list (AND-005) or Unreachable.
 * Callers re-run [refresh] on server-address changes, explicit Retry, and connectivity
 * return — every run is a fresh probe.
 */
class ProfilePickerLoader(
    private val picker: ProfilePicker,
    private val cache: CacheStore<List<Profile>>,
    private val clock: Clock,
) {
    suspend fun refresh(): ProfilePickerState = try {
        val loaded = picker.load()
        cache.write(loaded, clock.now())
        ProfilePickerReducer.reduce(loaded)
    } catch (e: CancellationException) {
        // The caller's effect was cancelled (url change / retry restarting it): rethrow —
        // an aborted probe must never masquerade as a result.
        throw e
    } catch (e: Exception) {
        ProfilePickerReducer.reduce(cache.read()?.value, loadFailed = true)
    }
}

/**
 * Role-driven feature visibility (AND-003): parent-only sections are hidden for kid
 * profiles, based purely on the profile record's role attribute.
 */
data class FeatureVisibility(
    val destinationEditing: Boolean,
    val settingsTuning: Boolean,
    val profileAdmin: Boolean,
    val trackerToggle: Boolean,
    val journal: Boolean,
    val map: Boolean,
    val games: Boolean,
    val checklist: Boolean,
    val trip: Boolean,
)

object FeatureVisibilityRules {
    fun forRole(role: Role): FeatureVisibility {
        val parent = role == Role.PARENT
        return FeatureVisibility(
            destinationEditing = parent,
            settingsTuning = parent,
            profileAdmin = parent,
            trackerToggle = parent, // any device class; kid profiles never see it (ANDLOC-003)
            journal = true,
            map = true,
            games = true,
            checklist = true,
            trip = true,
        )
    }
}
