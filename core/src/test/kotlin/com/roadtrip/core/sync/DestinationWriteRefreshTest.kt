package com.roadtrip.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** covers: ANDMAP-013 */
class DestinationWriteRefreshTest {

    @Test
    fun `a live in-trip write refreshes the destination list and the map ANDMAP-013`() {
        // Issue #134: after a live add/remove/reorder the destination list AND map must be
        // re-pulled at once, so the recomputed active destination + remaining distance and the
        // new/removed stop appear immediately rather than after the next foreground refresh.
        val targets = DestinationWriteRefresh.targets(staged = false)
        assertTrue(DestinationWriteTarget.LIVE_DESTINATIONS in targets)
        assertTrue(DestinationWriteTarget.MAP in targets)
        assertEquals(
            setOf(DestinationWriteTarget.LIVE_DESTINATIONS, DestinationWriteTarget.MAP),
            targets,
        )
    }

    @Test
    fun `a staged write refreshes only the planned trip's staged itinerary ANDMAP-013`() {
        assertEquals(
            setOf(DestinationWriteTarget.STAGED_ITINERARY),
            DestinationWriteRefresh.targets(staged = true),
        )
    }
}
