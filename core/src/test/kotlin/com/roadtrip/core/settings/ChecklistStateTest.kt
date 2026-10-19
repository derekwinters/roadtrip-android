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

    @Test
    fun `cities nest under the state with the same stateCode with a city count ANDSET-009`() {
        val screen = ChecklistReducer.fromDto(
            Checklist(
                states = listOf(
                    ChecklistStateEntry("Illinois", "IL", TestData.ts(0)),
                    ChecklistStateEntry("Iowa", "IA", TestData.ts(3600)),
                    ChecklistStateEntry("Nebraska", "NE", TestData.ts(7200)),
                ),
                cities = listOf(
                    ChecklistCityEntry("Des Moines", "IA", TestData.ts(4000)),
                    ChecklistCityEntry("Chicago", "IL", TestData.ts(100)),
                    ChecklistCityEntry("Iowa City", "IA", TestData.ts(3800)),
                ),
            ),
        )

        val groups = screen.stateGroups

        assertEquals(listOf("IL", "IA", "NE"), groups.map { it.state.stateCode })
        assertEquals(listOf("Chicago"), groups[0].cities.map { it.city })
        assertEquals(listOf("Des Moines", "Iowa City"), groups[1].cities.map { it.city })
        assertEquals(emptyList(), groups[2].cities)
        assertEquals(listOf(1, 2, 0), groups.map { it.cityCount })
        // Per-city first-visited time is kept on the nested row.
        assertEquals(TestData.t(4000), groups[1].cities[0].firstAt)
    }

    @Test
    fun `a city with no state row gets a synthesized unentered state group ANDSET-010`() {
        val screen = ChecklistReducer.fromDto(
            Checklist(
                states = listOf(ChecklistStateEntry("Illinois", "IL", TestData.ts(0))),
                cities = listOf(
                    ChecklistCityEntry("Omaha", "NE", TestData.ts(8000)),
                    ChecklistCityEntry("Chicago", "IL", TestData.ts(100)),
                    ChecklistCityEntry("Lincoln", "NE", TestData.ts(9000)),
                ),
            ),
        )

        val groups = screen.stateGroups

        assertEquals(listOf("IL", "NE"), groups.map { it.state.stateCode })
        val synthesized = groups[1]
        assertEquals(StateRow(state = "NE", stateCode = "NE", firstEnteredAt = null), synthesized.state)
        assertEquals(listOf("Omaha", "Lincoln"), synthesized.cities.map { it.city })
        assertEquals(2, synthesized.cityCount)
    }

    @Test
    fun `a later crossing marks the synthesized state entered without duplicating it ANDSET-010`() {
        var screen = ChecklistReducer.fromDto(
            Checklist(
                states = listOf(ChecklistStateEntry("Illinois", "IL", TestData.ts(0))),
                cities = listOf(ChecklistCityEntry("Omaha", "NE", TestData.ts(8000))),
            ),
        )

        screen = ChecklistReducer.applyEvent(
            screen,
            TestData.stateCrossingEvent(50, "Nebraska", "NE", clientTs = TestData.ts(7200)),
        )

        val groups = screen.stateGroups
        assertEquals(listOf("IL", "NE"), groups.map { it.state.stateCode })
        assertEquals("Nebraska", groups[1].state.state)
        assertEquals(TestData.t(7200), groups[1].state.firstEnteredAt)
        assertEquals(listOf("Omaha"), groups[1].cities.map { it.city })
    }

    @Test
    fun `groups start collapsed and only the deep-linked state auto-expands ANDSET-011`() {
        val screen = ChecklistReducer.fromDto(
            Checklist(
                states = listOf(
                    ChecklistStateEntry("Illinois", "IL", TestData.ts(0)),
                    ChecklistStateEntry("Iowa", "IA", TestData.ts(3600)),
                ),
                cities = listOf(ChecklistCityEntry("Omaha", "NE", TestData.ts(8000))),
            ),
        )

        // Opened normally: everything collapsed.
        assertEquals(emptySet(), ChecklistExpansion.initial(screen.stateGroups, highlightStateCode = null))

        // Arriving from a state-crossing deep link: only that state is expanded.
        val fromDeepLink = ChecklistExpansion.initial(screen.stateGroups, highlightStateCode = "IA")
        assertEquals(setOf("IA"), fromDeepLink)

        // A highlight for a state that is not on the checklist expands nothing.
        assertEquals(emptySet(), ChecklistExpansion.initial(screen.stateGroups, highlightStateCode = "CO"))

        // Tapping toggles one group and leaves the others alone.
        val withIllinois = ChecklistExpansion.toggle(fromDeepLink, "IL")
        assertEquals(setOf("IA", "IL"), withIllinois)
        assertEquals(setOf("IL"), ChecklistExpansion.toggle(withIllinois, "IA"))
    }
}
