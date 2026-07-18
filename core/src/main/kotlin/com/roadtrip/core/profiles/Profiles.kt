package com.roadtrip.core.profiles

import com.roadtrip.core.api.Profile
import com.roadtrip.core.api.RoadtripApi
import com.roadtrip.core.common.Role
import com.roadtrip.core.storage.SelectedProfileStore

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

/** What the launch picker shows (AND-001/007). */
sealed class ProfilePickerState {
    /** Profiles not loaded yet (server or cache pending). */
    object Loading : ProfilePickerState()

    /** Zero profiles on the server: first-run "Set up your family" flow (AND-007). */
    object SetupRequired : ProfilePickerState()

    /** Select-only avatar grid — no create affordance once profiles exist (AND-007). */
    data class Grid(val profiles: List<Profile>) : ProfilePickerState()
}

object ProfilePickerReducer {
    fun reduce(profiles: List<Profile>?): ProfilePickerState = when {
        profiles == null -> ProfilePickerState.Loading
        profiles.isEmpty() -> ProfilePickerState.SetupRequired
        else -> ProfilePickerState.Grid(profiles)
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
