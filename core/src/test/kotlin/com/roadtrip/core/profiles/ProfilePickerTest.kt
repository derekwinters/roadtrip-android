package com.roadtrip.core.profiles

import com.roadtrip.core.api.ApiException
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
    fun `create-first flow creates a parent profile with no avatar and signs in AND-007`() = runTest {
        api.profiles = emptyList()
        val picker = ProfilePicker(api, store)

        val result = picker.runFirstRunCreate("Derek")

        val created = (result as FirstRunCreateResult.SignedIn).profile
        // The flow enforces the parent role (backend bootstrap accepts parent-first only)
        // and sends no avatar — the wizard has no avatar input; the server assigns its default.
        assertEquals(Role.PARENT, api.createProfileRequests.single().role)
        assertNull(api.createProfileRequests.single().avatar)
        assertEquals(Role.PARENT, created.role)
        assertEquals("Derek", created.name)
        // ...and immediately signs in as the new profile.
        assertEquals(created, picker.selected())
    }

    @Test
    fun `closed-bootstrap 401 with profiles on re-probe leaves setup for the grid AND-009`() = runTest {
        api.createProfileError = ApiException(401, "unauthenticated", "Unknown or missing profile")
        api.profiles = listOf(TestData.parent, TestData.kid) // setup finished elsewhere
        val picker = ProfilePicker(api, store)

        val result = picker.runFirstRunCreate("Derek")

        // The wizard hands the fresh list to the picker instead of dead-ending on the 401…
        assertEquals(
            FirstRunCreateResult.ProfilesAppeared(listOf(TestData.parent, TestData.kid)),
            result,
        )
        // …and never guesses who this device belongs to.
        assertNull(picker.selected())
    }

    @Test
    fun `closed-bootstrap 401 with a still-empty list means an older server AND-009`() = runTest {
        api.createProfileError = ApiException(401, "unauthenticated", "Unknown or missing profile")
        api.profiles = emptyList() // the server refused first-run setup outright
        val picker = ProfilePicker(api, store)

        val result = picker.runFirstRunCreate("Derek")

        val failed = result as FirstRunCreateResult.Failed
        // Actionable: the server predates first-run setup — update it. Never the raw envelope.
        assertTrue(failed.message.contains("update", ignoreCase = true), failed.message)
        assertFalse(failed.message.contains("Unknown or missing", ignoreCase = true))
        assertNull(picker.selected())
    }

    @Test
    fun `re-probe failure after a 401 keeps a human offline message AND-009`() = runTest {
        api.createProfileError = ApiException(401, "unauthenticated", "Unknown or missing profile")
        api.offline = true // the follow-up GET /api/profiles cannot be reached
        val picker = ProfilePicker(api, store)

        val result = picker.runFirstRunCreate("Derek")

        val failed = result as FirstRunCreateResult.Failed
        assertTrue(failed.message.contains("connection", ignoreCase = true), failed.message)
        assertNull(picker.selected())
    }

    @Test
    fun `transport failure on create keeps the wizard retryable with an offline message AND-009`() = runTest {
        api.profiles = emptyList()
        api.offline = true
        val picker = ProfilePicker(api, store)

        val result = picker.runFirstRunCreate("Derek")

        val failed = result as FirstRunCreateResult.Failed
        assertTrue(failed.message.contains("connection", ignoreCase = true), failed.message)
        assertNull(picker.selected())
    }

    @Test
    fun `add-member creates the chosen role with no avatar and signs in AND-010`() = runTest {
        val picker = ProfilePicker(api, store)

        val result = picker.runAddMember("Cousin", Role.KID)

        val created = (result as AddMemberResult.SignedIn).profile
        val request = api.createProfileRequests.single()
        assertEquals(Role.KID, request.role)
        assertNull(request.avatar) // no avatar input anywhere — the server assigns its default
        assertEquals("Cousin", created.name)
        assertEquals(Role.KID, created.role)
        assertEquals(created, picker.selected())
    }

    @Test
    fun `add-member surfaces the server's own rejection message AND-010`() = runTest {
        // A parent turned creation off (backend PRO-009): the server's message says so.
        api.createProfileError = ApiException(
            401,
            "unauthenticated",
            "Profile creation is turned off — sign in as a parent to add family members",
        )
        val picker = ProfilePicker(api, store)

        val result = picker.runAddMember("Cousin", Role.KID)

        val failed = result as AddMemberResult.Failed
        assertTrue(failed.message.contains("turned off"), failed.message)
        assertNull(picker.selected())
    }

    @Test
    fun `add-member transport failures show an offline message AND-010`() = runTest {
        api.offline = true
        val picker = ProfilePicker(api, store)

        val result = picker.runAddMember("Cousin", Role.PARENT)

        val failed = result as AddMemberResult.Failed
        assertTrue(failed.message.contains("connection", ignoreCase = true), failed.message)
        assertNull(picker.selected())
    }

    @Test
    fun `non-401 server rejections surface their own message AND-009`() = runTest {
        api.createProfileError = ApiException(400, "validation", "name: String must contain at most 40 character(s)")
        val picker = ProfilePicker(api, store)

        val result = picker.runFirstRunCreate("x".repeat(41))

        val failed = result as FirstRunCreateResult.Failed
        assertTrue(failed.message.contains("name"), failed.message)
        assertNull(picker.selected())
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
