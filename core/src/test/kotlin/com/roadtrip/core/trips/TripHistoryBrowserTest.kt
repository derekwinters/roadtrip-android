package com.roadtrip.core.trips

import com.roadtrip.core.api.Checklist
import com.roadtrip.core.api.ChecklistStateEntry
import com.roadtrip.core.api.JournalEntry
import com.roadtrip.core.api.JournalKind
import com.roadtrip.core.api.JournalPage
import com.roadtrip.core.api.TripStatus
import com.roadtrip.core.api.TripSummary
import com.roadtrip.core.testing.FakeRoadtripApi
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TripHistoryBrowserTest {
    private val api = FakeRoadtripApi()
    private val browser = TripHistoryBrowser(api)

    @Test
    fun `lists trips active first then most recently ended ANDTRIP-003`() = runTest {
        api.trips += TestData.trip("trip-1", "Spring", TripStatus.ENDED, TestData.ts(0), TestData.ts(1000))
        api.trips += TestData.trip("trip-3", "Fall", TripStatus.ACTIVE, TestData.ts(4000))
        api.trips += TestData.trip("trip-2", "Summer", TripStatus.ENDED, TestData.ts(2000), TestData.ts(3000))

        assertEquals(listOf("trip-3", "trip-2", "trip-1"), browser.listTrips().map { it.id })
    }

    @Test
    fun `opens read-only journal checklist and summary scoped per trip ANDTRIP-003`() = runTest {
        api.journalByTrip["trip-1"] = JournalPage(
            listOf(JournalEntry(seq = 1, kind = JournalKind.POST, ts = TestData.ts(10), text = "old trip post")),
            null,
        )
        api.checklistByTrip["trip-1"] = Checklist(
            states = listOf(ChecklistStateEntry("Colorado", "CO", TestData.ts(5))),
        )
        api.summaryByTrip["trip-1"] = TripSummary(1204.0, 5000.0, 3000.0, 5, 12, 9)
        api.journalByTrip["trip-2"] = JournalPage(emptyList(), null)
        api.summaryByTrip["trip-2"] = TripSummary(10.0, 60.0, 45.0, 1, 1, 0)

        assertEquals("old trip post", browser.journal("trip-1").entries.single().text)
        assertEquals("CO", browser.checklist("trip-1").states.single().stateCode)
        assertEquals(1204.0, browser.summary("trip-1").miles)

        // The other trip's views stay fully isolated — no history mixing.
        assertTrue(browser.journal("trip-2").entries.isEmpty())
        assertTrue(browser.checklist("trip-2").states.isEmpty())
        assertEquals(10.0, browser.summary("trip-2").miles)
    }
}
