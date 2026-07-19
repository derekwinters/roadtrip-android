package com.roadtrip.core.settings

import com.roadtrip.core.api.ApiException
import com.roadtrip.core.api.Config
import com.roadtrip.core.api.ConfigPatch
import com.roadtrip.core.common.Role
import com.roadtrip.core.testing.FakeRoadtripApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ConfigFormTest {
    private val api = FakeRoadtripApi().apply {
        config = Config(300, 100.0, 10.0, 800.0, 10.0)
    }

    @Test
    fun `loads current values and validates edits against the backend bounds ANDSET-001`() = runTest {
        val form = ConfigForm(api, Role.PARENT)
        form.load()

        assertEquals(300, form.values?.pingIntervalS)
        assertFalse(form.isDirty)

        // In-bounds edit: dirty, valid, and only the changed key goes in the PUT body.
        form.edit { it.copy(pingIntervalS = 60) }
        assertTrue(form.isDirty)
        assertTrue(form.validationErrors().isEmpty())
        assertEquals(ConfigPatch(pingIntervalS = 60), form.pendingPatch())

        // Out-of-bounds edits fail client-side with the offending keys named.
        form.edit { it.copy(pingIntervalS = 4, arrivalRadiusM = 6000.0) }
        val errors = form.validationErrors()
        assertEquals(2, errors.size)
        assertTrue(errors.any { it.contains("ping_interval_s") })
        assertTrue(errors.any { it.contains("arrival_radius_m") })

        // Out-of-bounds never reaches the API.
        assertIs<SaveResult.ValidationFailed>(form.save())
    }

    @Test
    fun `saves via the api and is parent-only ANDSET-001`() = runTest {
        val parentForm = ConfigForm(api, Role.PARENT)
        parentForm.load()
        parentForm.edit { it.copy(stopRadiusM = 150.0) }

        val saved = assertIs<SaveResult.Saved>(parentForm.save())
        assertEquals(150.0, saved.config.stopRadiusM)
        assertEquals(150.0, api.config.stopRadiusM) // PUT actually hit the server
        assertFalse(parentForm.isDirty)

        // Kids cannot save at all.
        val kidForm = ConfigForm(api, Role.KID)
        kidForm.load()
        kidForm.edit { it.copy(stopRadiusM = 999.0) }
        assertFalse(kidForm.canSave)
        assertIs<SaveResult.NotAllowed>(kidForm.save())
        assertEquals(150.0, api.config.stopRadiusM) // untouched
    }

    @Test
    fun `open profile creation toggles through the config form ANDSET-006`() = runTest {
        val form = ConfigForm(api, Role.PARENT)
        form.load()
        assertEquals(true, form.values?.openProfileCreation) // server default (CFG-006)

        form.edit { it.copy(openProfileCreation = false) }
        assertTrue(form.isDirty)
        assertTrue(form.validationErrors().isEmpty()) // booleans have no bounds
        assertEquals(ConfigPatch(openProfileCreation = false), form.pendingPatch())

        val saved = assertIs<SaveResult.Saved>(form.save())
        assertEquals(false, saved.config.openProfileCreation)
        assertEquals(false, api.config.openProfileCreation) // PUT actually hit the server
        assertFalse(form.isDirty)
    }

    @Test
    fun `an offline save restores the last known server values with the reason ANDSET-002`() = runTest {
        val form = ConfigForm(api, Role.PARENT)
        form.load()
        form.edit { it.copy(pingIntervalS = 60) }

        api.offline = true
        val failed = assertIs<SaveResult.Failed>(form.save())

        assertTrue(failed.reason.isNotBlank())
        assertEquals(300, form.values?.pingIntervalS) // rolled back to server values
        assertFalse(form.isDirty)
        assertEquals(failed.reason, form.lastError)
    }

    @Test
    fun `a server-side rejection restores values and surfaces the server reason ANDSET-002`() = runTest {
        val form = ConfigForm(api, Role.PARENT)
        form.load()
        form.edit { it.copy(cityRadiusKm = 45.0) }

        api.putConfigHandler = { throw ApiException(400, "out_of_bounds", "city_radius_km rejected by server") }
        val failed = assertIs<SaveResult.Failed>(form.save())

        assertEquals("city_radius_km rejected by server", failed.reason)
        assertEquals(10.0, form.values?.cityRadiusKm) // last known server value restored
    }

    @Test
    fun `client bounds mirror the backend bounds table exactly ANDSET-001`() {
        // roadtrip-backend/docs/spec/05-config.md: 5-3600, 20-1000, 1-240, 100-5000, 1-50.
        assertTrue(ConfigBounds.validate(ConfigPatch(pingIntervalS = 5)).isEmpty())
        assertTrue(ConfigBounds.validate(ConfigPatch(pingIntervalS = 3600)).isEmpty())
        assertFalse(ConfigBounds.validate(ConfigPatch(pingIntervalS = 3601)).isEmpty())
        assertTrue(ConfigBounds.validate(ConfigPatch(stopRadiusM = 20.0)).isEmpty())
        assertFalse(ConfigBounds.validate(ConfigPatch(stopRadiusM = 19.9)).isEmpty())
        assertTrue(ConfigBounds.validate(ConfigPatch(minStopDurationMin = 240.0)).isEmpty())
        assertFalse(ConfigBounds.validate(ConfigPatch(minStopDurationMin = 240.5)).isEmpty())
        assertTrue(ConfigBounds.validate(ConfigPatch(arrivalRadiusM = 100.0)).isEmpty())
        assertFalse(ConfigBounds.validate(ConfigPatch(arrivalRadiusM = 99.0)).isEmpty())
        assertTrue(ConfigBounds.validate(ConfigPatch(cityRadiusKm = 50.0)).isEmpty())
        assertFalse(ConfigBounds.validate(ConfigPatch(cityRadiusKm = 50.1)).isEmpty())
    }
}
