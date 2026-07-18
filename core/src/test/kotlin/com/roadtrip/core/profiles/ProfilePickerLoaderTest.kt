package com.roadtrip.core.profiles

import com.roadtrip.core.api.Profile
import com.roadtrip.core.api.RoadtripApi
import com.roadtrip.core.storage.InMemoryCacheStore
import com.roadtrip.core.storage.InMemorySelectedProfileStore
import com.roadtrip.core.testing.FakeClock
import com.roadtrip.core.testing.FakeRoadtripApi
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * [ProfilePickerLoader] owns the picker's probe → cache → reduce cycle: reachable-server
 * results refresh the cache (AND-007 semantics), failures fall back to the cached list
 * (AND-005) or the distinct Unreachable state, and every re-run — server-address change,
 * explicit Retry, connectivity return — is a fresh probe (AND-008).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProfilePickerLoaderTest {
    private val api = FakeRoadtripApi()
    private val store = InMemorySelectedProfileStore()
    private val cache = InMemoryCacheStore<List<Profile>>()
    private val clock = FakeClock()
    private val loader = ProfilePickerLoader(ProfilePicker(api, store), cache, clock)

    @Test
    fun `unreachable server with nothing cached is the unreachable state AND-008`() = runTest {
        api.offline = true

        assertEquals(ProfilePickerState.Unreachable, loader.refresh())
        // A failed probe never writes the cache.
        assertNull(cache.read())
    }

    @Test
    fun `unreachable server with an empty cached list stays unreachable never setup AND-008`() = runTest {
        // An earlier session cached "zero profiles". Only a live server may put the picker
        // into first-run setup (creating profile #1 needs the server, and an unreachable
        // server may actually have profiles) — offline this must be Unreachable.
        cache.write(emptyList(), clock.now())
        api.offline = true

        assertEquals(ProfilePickerState.Unreachable, loader.refresh())
    }

    @Test
    fun `unreachable server falls back to cached profiles so offline selection works AND-005`() = runTest {
        // covers: AND-008 — the unreachable state only appears when no cache is usable.
        cache.write(listOf(TestData.parent, TestData.kid), clock.now())
        api.offline = true

        assertEquals(ProfilePickerState.Grid(listOf(TestData.parent, TestData.kid)), loader.refresh())
    }

    @Test
    fun `successful refresh writes the cache and reduces to grid or setup AND-007`() = runTest {
        api.profiles = listOf(TestData.parent)

        assertEquals(ProfilePickerState.Grid(listOf(TestData.parent)), loader.refresh())
        assertEquals(listOf(TestData.parent), cache.read()?.value)
        assertEquals(clock.now(), cache.read()?.storedAt)

        // A live server confirming zero profiles is the only path into first-run setup.
        api.profiles = emptyList()
        assertEquals(ProfilePickerState.SetupRequired, loader.refresh())
    }

    @Test
    fun `retry re-probes and recovers once the server is reachable AND-008`() = runTest {
        api.offline = true
        assertEquals(ProfilePickerState.Unreachable, loader.refresh())

        // Address fixed / connectivity back / Retry tapped: the next refresh is a real
        // probe, not a replay of the failure.
        api.offline = false
        api.profiles = listOf(TestData.parent, TestData.kid)

        assertEquals(ProfilePickerState.Grid(listOf(TestData.parent, TestData.kid)), loader.refresh())
        assertEquals(listOf(TestData.parent, TestData.kid), cache.read()?.value)
    }

    @Test
    fun `refresh propagates cancellation instead of reporting unreachable AND-008`() = runTest {
        // The screen's load effect is cancelled and restarted on url change/retry; a
        // cancelled probe must rethrow, never masquerade as an Unreachable result.
        val hangingApi = object : RoadtripApi by api {
            override suspend fun getProfiles(): List<Profile> = awaitCancellation()
        }
        val hangingLoader = ProfilePickerLoader(ProfilePicker(hangingApi, store), cache, clock)

        var result: ProfilePickerState? = null
        val probe = launch { result = hangingLoader.refresh() }
        runCurrent() // reach the suspension point inside getProfiles
        probe.cancel()
        probe.join()

        assertNull(result)
        assertNull(cache.read())
    }
}
