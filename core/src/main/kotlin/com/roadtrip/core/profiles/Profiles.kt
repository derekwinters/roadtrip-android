package com.roadtrip.core.profiles

import com.roadtrip.core.api.Profile
import com.roadtrip.core.api.RoadtripApi
import com.roadtrip.core.common.Role
import com.roadtrip.core.storage.SelectedProfileStore

/**
 * Launch profile picker: avatars from GET /api/profiles, tapping one selects it — no
 * passwords anywhere; the selection persists across restarts via [SelectedProfileStore]
 * (AND-001/002).
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
