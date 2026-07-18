package com.roadtrip.core.sync

import com.roadtrip.core.api.Checklist
import com.roadtrip.core.api.ChecklistStateEntry
import com.roadtrip.core.api.GeoPoint
import com.roadtrip.core.api.JournalEntry
import com.roadtrip.core.api.JournalKind
import com.roadtrip.core.api.MapState
import com.roadtrip.core.api.OnlineMonitor
import com.roadtrip.core.api.TrackPoint
import com.roadtrip.core.common.Role
import com.roadtrip.core.games.GameOfflineGate
import com.roadtrip.core.journal.JournalFeedReducer
import com.roadtrip.core.map.MapScreenReducer
import com.roadtrip.core.settings.ChecklistReducer
import com.roadtrip.core.storage.InMemoryCacheStore
import com.roadtrip.core.testing.FakeClock
import com.roadtrip.core.testing.FakeRoadtripApi
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * AND-005: the app stays fully navigable offline — cached read models keep rendering and
 * online-only game actions surface an explicit offline state instead of failing silently.
 */
class OfflineFirstTest {

    @Test
    fun `cached journal map and checklist render offline and game actions gate off AND-005`() = runTest {
        val api = FakeRoadtripApi()
        val clock = FakeClock()

        // Caches were populated while online...
        val journalCache = InMemoryCacheStore<List<JournalEntry>>()
        journalCache.write(
            listOf(JournalEntry(seq = 1, kind = JournalKind.POST, ts = TestData.ts(0), text = "cached post")),
            clock.now(),
        )
        val mapCache = InMemoryCacheStore<MapState>()
        mapCache.write(
            MapState(
                current = TrackPoint(39.7, -105.0, TestData.ts(0)),
                start = GeoPoint(41.9, -87.6),
                breadcrumb = listOf(TrackPoint(41.9, -87.6), TrackPoint(39.7, -105.0)),
                legMiles = 120.0,
            ),
            clock.now(),
        )
        val checklistCache = InMemoryCacheStore<Checklist>()
        checklistCache.write(
            Checklist(states = listOf(ChecklistStateEntry("Colorado", "CO", TestData.ts(0)))),
            clock.now(),
        )

        // ...then the car drives out of coverage.
        api.offline = true
        val monitor = OnlineMonitor(api)
        assertFalse(monitor.check())
        assertFalse(monitor.online.value)

        val feed = JournalFeedReducer.reduce(journalCache.read()!!.value, emptyList(), null)
        assertEquals(1, feed.size)

        val map = MapScreenReducer.fromCache(mapCache.read()!!, emptyList(), Role.KID)
        assertTrue(map.offline)
        assertNotNull(map.freshnessLabel)
        assertEquals(2, map.polyline.size)

        val checklist = ChecklistReducer.fromDto(checklistCache.read()!!.value)
        assertEquals("CO", checklist.states.single().stateCode)

        val gate = GameOfflineGate.evaluate(online = monitor.online.value)
        assertFalse(gate.enabled)
        assertNotNull(gate.reason) // the offline banner has an explanation, never a silent failure
    }
}
