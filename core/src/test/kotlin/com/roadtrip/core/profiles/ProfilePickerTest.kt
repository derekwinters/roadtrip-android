package com.roadtrip.core.profiles

import com.roadtrip.core.api.Profile
import com.roadtrip.core.common.Role
import com.roadtrip.core.storage.InMemorySelectedProfileStore
import com.roadtrip.core.testing.FakeRoadtripApi
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ProfilePickerTest {
    private val api = FakeRoadtripApi().apply { profiles = listOf(TestData.parent, TestData.kid) }
    private val store = InMemorySelectedProfileStore()

    @Test
    fun `lists avatar profiles from the api and selecting one needs no password AND-001`() = runTest {
        val picker = ProfilePicker(api, store)

        val profiles = picker.load()

        assertEquals(listOf(TestData.parent, TestData.kid), profiles)
        assertNull(picker.selected())

        picker.select(TestData.kid) // a single tap — no credentials anywhere
        assertEquals(TestData.kid, picker.selected())
    }

    @Test
    fun `persists the selection across app restarts AND-001`() = runTest {
        ProfilePicker(api, store).select(TestData.kid)

        // "Restart": a fresh picker over the same persistent store.
        val rebooted = ProfilePicker(api, store)
        assertEquals(TestData.kid, rebooted.selected())
    }

    @Test
    fun `hides parent-only sections for kid profiles AND-003`() {
        val kidView = FeatureVisibilityRules.forRole(Role.KID)
        assertFalse(kidView.destinationEditing)
        assertFalse(kidView.settingsTuning)
        assertFalse(kidView.profileAdmin)
        assertFalse(kidView.trackerToggle)
        // Shared surfaces stay available.
        assertTrue(kidView.journal && kidView.map && kidView.games && kidView.checklist && kidView.trip)

        val parentView = FeatureVisibilityRules.forRole(Role.PARENT)
        assertTrue(parentView.destinationEditing)
        assertTrue(parentView.settingsTuning)
        assertTrue(parentView.profileAdmin)
        assertTrue(parentView.trackerToggle)
    }

    @Test
    fun `zero profiles put the picker into the first-run family setup state AND-007`() = runTest {
        api.profiles = emptyList()
        val picker = ProfilePicker(api, store)

        assertEquals(ProfilePickerState.SetupRequired, ProfilePickerReducer.reduce(picker.load()))
        // Before any load completes the picker is still loading, never setup.
        assertEquals(ProfilePickerState.Loading, ProfilePickerReducer.reduce(null))
    }

    @Test
    fun `create-first flow creates a parent profile and signs in as them AND-007`() = runTest {
        api.profiles = emptyList()
        val picker = ProfilePicker(api, store)

        val created = picker.createFirstProfile("Derek", "bear")

        // The flow enforces the parent role (backend bootstrap accepts parent-first only).
        assertEquals(Role.PARENT, api.createProfileRequests.single().role)
        assertEquals(Role.PARENT, created.role)
        assertEquals("Derek", created.name)
        // ...and immediately signs in as the new profile.
        assertEquals(created, picker.selected())
    }

    @Test
    fun `one or more profiles show the select-only grid with no setup AND-007`() = runTest {
        val picker = ProfilePicker(api, store)

        val state = ProfilePickerReducer.reduce(picker.load())

        assertEquals(ProfilePickerState.Grid(listOf(TestData.parent, TestData.kid)), state)
        assertTrue(api.createProfileRequests.isEmpty()) // selecting never creates
    }

    @Test
    fun `load failure maps to unreachable never first-run setup AND-008`() {
        // No cache at all: the picker is dead-ended, not in setup (setup needs the server).
        assertEquals(ProfilePickerState.Unreachable, ProfilePickerReducer.reduce(null, loadFailed = true))
        // A cached-but-empty list is no proof of zero profiles — still unreachable.
        assertEquals(ProfilePickerState.Unreachable, ProfilePickerReducer.reduce(emptyList(), loadFailed = true))
        // Cached profiles keep offline selection working (AND-005).
        assertEquals(
            ProfilePickerState.Grid(listOf(TestData.parent)),
            ProfilePickerReducer.reduce(listOf(TestData.parent), loadFailed = true),
        )
    }

    @Test
    fun `derives visibility from the role attribute never from names AND-003`() {
        // A kid named like a parent is still a kid: only the role record matters.
        val trickyKid = Profile("p-9", "Mom", "cat", Role.KID)
        assertFalse(FeatureVisibilityRules.forRole(trickyKid.role).settingsTuning)

        val quietParent = Profile("p-8", "Junior", "dino", Role.PARENT)
        assertTrue(FeatureVisibilityRules.forRole(quietParent.role).settingsTuning)
    }
}
