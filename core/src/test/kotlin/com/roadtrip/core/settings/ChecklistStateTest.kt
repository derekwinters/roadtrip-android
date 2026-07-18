package com.roadtrip.core.settings

import com.roadtrip.core.api.Checklist
import com.roadtrip.core.api.ChecklistCityEntry
import com.roadtrip.core.api.ChecklistStateEntry
import com.roadtrip.core.api.ChecklistStopEntry
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals

class ChecklistStateTest {

    @Test
    fun `renders states with first-entered time plus cities and stops ANDSET-003`() {
        val dto = Checklist(
            states = listOf(
                ChecklistStateEntry("Illinois", "IL", TestData.ts(0)),
                ChecklistStateEntry("Iowa", "IA", TestData.ts(3600)),
            ),
            cities = listOf(ChecklistCityEntry("Des Moines", "IA", TestData.ts(4000))),
            stops = listOf(ChecklistStopEntry(41.6, -93.6, TestData.ts(4200), 25.0, "World's Largest Truck Stop")),
        )

        val screen = ChecklistReducer.fromDto(dto)

        assertEquals(listOf("IL", "IA"), screen.states.map { it.stateCode })
        assertEquals(TestData.t(0), screen.states[0].firstEnteredAt)
        assertEquals("Des Moines", screen.cities.single().city)
        assertEquals("World's Largest Truck Stop", screen.stops.single().place)
        assertEquals(25.0, screen.stops.single().durationMin)
    }

    @Test
    fun `states light up as crossing events arrive on the feed ANDSET-003`() {
        var screen = ChecklistReducer.fromDto(
            Checklist(states = listOf(ChecklistStateEntry("Illinois", "IL", TestData.ts(0)))),
        )

        // Crossing into Nebraska arrives on the event feed: it lights up immediately,
        // stamped with the crossing's client_ts as its first-entered time.
        screen = ChecklistReducer.applyEvent(
            screen,
            TestData.stateCrossingEvent(50, "Nebraska", "NE", clientTs = TestData.ts(7200)),
        )
        assertEquals(listOf("IL", "NE"), screen.states.map { it.stateCode })
        assertEquals(TestData.t(7200), screen.states.last().firstEnteredAt)

        // Re-entering a state does not duplicate it or move its first-entered time.
        screen = ChecklistReducer.applyEvent(
            screen,
            TestData.stateCrossingEvent(60, "Illinois", "IL", clientTs = TestData.ts(9000)),
        )
        assertEquals(listOf("IL", "NE"), screen.states.map { it.stateCode })
        assertEquals(TestData.t(0), screen.states.first().firstEnteredAt)

        // Non-crossing events leave the checklist alone.
        val untouched = ChecklistReducer.applyEvent(screen, TestData.pingEvent(70, 41.0, -100.0))
        assertEquals(screen, untouched)
    }
}
