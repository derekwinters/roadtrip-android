package com.roadtrip.core.map

import com.roadtrip.core.api.Destination
import com.roadtrip.core.api.DestinationStatus
import com.roadtrip.core.common.Role
import com.roadtrip.core.sync.DestinationWriteRefresh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The manual End-leg safety net availability + refresh decision (ANDMAP-014, backend LOC-013). */
class EndLegControlTest {
    private val active = Destination("d-1", "Rocky Mountain NP", 40.3428, -105.6836, 0, DestinationStatus.ACTIVE)
    private val pending = Destination("d-2", "Arches NP", 38.7331, -109.5925, 1, DestinationStatus.PENDING)
    private val arrived = Destination("d-0", "Denver", 39.7392, -104.9903, 0, DestinationStatus.ARRIVED)

    @Test
    fun `an online parent with an active destination can end the leg ANDMAP-014`() {
        val action = EndLegControl.action(Role.PARENT, online = true, destinations = listOf(active, pending))
        assertTrue(action.visible)
        assertTrue(action.enabled)
        assertTrue(action.requiresConfirmation) // always dialog-confirmed, like End trip
        assertNull(action.disabledReason)
    }

    @Test
    fun `offline shows the control disabled with a reason ANDMAP-014`() {
        val action = EndLegControl.action(Role.PARENT, online = false, destinations = listOf(active))
        assertTrue(action.visible)
        assertFalse(action.enabled)
        assertEquals(EndLegControl.OFFLINE_REASON, action.disabledReason)
    }

    @Test
    fun `kids never see the End leg control ANDMAP-014`() {
        val action = EndLegControl.action(Role.KID, online = true, destinations = listOf(active))
        assertFalse(action.visible)
        assertFalse(action.enabled)
    }

    @Test
    fun `hidden when there is no active destination - between trips or all arrived ANDMAP-014`() {
        assertFalse(EndLegControl.action(Role.PARENT, online = true, destinations = emptyList()).visible)
        assertFalse(EndLegControl.action(Role.PARENT, online = true, destinations = listOf(arrived)).visible)
        // Staging a planned trip: every staged stop is pending, so nothing is active yet.
        assertFalse(EndLegControl.action(Role.PARENT, online = true, destinations = listOf(pending)).visible)
    }

    @Test
    fun `end-leg writes through the live destination list and the map ANDMAP-014`() {
        // Reuses the same write-through a live in-trip destination write performs (ANDMAP-013),
        // so the arrived stop and recomputed active destination / remaining distance appear at once.
        assertEquals(DestinationWriteRefresh.targets(staged = false), EndLegControl.refreshTargets())
    }
}
