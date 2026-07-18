package com.roadtrip.core.trips

import com.roadtrip.core.storage.InMemoryCacheStore
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TripCacheScopingTest {

    @Test
    fun `cache keys include the trip id ANDTRIP-002`() {
        assertEquals("journal_trip-1", TripCacheKeys.scoped("journal", "trip-1"))
        assertEquals("checklist_trip-2", TripCacheKeys.scoped("checklist", "trip-2"))
        // No known trip (pre-trips backend / first sync): the unscoped legacy key.
        assertEquals("journal", TripCacheKeys.scoped("journal", null))
        assertEquals("journal", TripCacheKeys.scoped("journal", ""))
    }

    @Test
    fun `switching or ending a trip never mixes cached histories ANDTRIP-002`() {
        val backing = mutableMapOf<String, InMemoryCacheStore<List<String>>>()
        var currentTrip: String? = "trip-1"
        val store = TripScopedCacheStore<List<String>>("journal", { currentTrip }) { key ->
            backing.getOrPut(key) { InMemoryCacheStore() }
        }

        store.write(listOf("first trip post"), TestData.t(0))
        assertEquals(listOf("first trip post"), store.read()?.value)

        // Trip 1 ends and trip 2 starts: the store transparently switches rows.
        currentTrip = "trip-2"
        assertNull(store.read()) // the new trip starts with an empty history
        store.write(listOf("second trip post"), TestData.t(100))
        assertEquals(listOf("second trip post"), store.read()?.value)

        // Browsing back to the ended trip finds its history untouched.
        currentTrip = "trip-1"
        assertEquals(listOf("first trip post"), store.read()?.value)
        assertEquals(setOf("journal_trip-1", "journal_trip-2"), backing.keys)
    }
}
