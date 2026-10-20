package com.roadtrip.core.settings

import com.roadtrip.core.api.ApiException
import com.roadtrip.core.api.Config
import com.roadtrip.core.api.ConfigPatch
import com.roadtrip.core.api.RoadtripJson
import com.roadtrip.core.common.Role
import com.roadtrip.core.profiles.FeatureVisibilityRules
import com.roadtrip.core.testing.FakeRoadtripApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

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

    // ---- city tracking (ANDSET-017/018) -----------------------------------------------------

    @Test
    fun `config parses with and without the city tracking keys ANDSET-017 ANDSET-018`() {
        val base = """{"ping_interval_s":300,"stop_radius_m":100,"min_stop_duration_min":10,""" +
            """"arrival_radius_m":800,"city_radius_km":15"""

        // A server predating the keys: backend defaults (CFG-009), never an error.
        val old = RoadtripJson.decodeFromString(Config.serializer(), "$base}")
        assertEquals(true, old.trackCitiesStopped)
        assertEquals(true, old.trackCitiesThrough)
        assertEquals(5000, old.cityMinPopulation)

        val current = RoadtripJson.decodeFromString(
            Config.serializer(),
            "$base,\"track_cities_stopped\":false,\"track_cities_through\":true,\"city_min_population\":20000}",
        )
        assertEquals(false, current.trackCitiesStopped)
        assertEquals(true, current.trackCitiesThrough)
        assertEquals(20000, current.cityMinPopulation)
    }

    @Test
    fun `each city tracking toggle edit sends only its own key ANDSET-017`() = runTest {
        val form = ConfigForm(api, Role.PARENT)
        form.load()

        form.edit { it.copy(trackCitiesStopped = false) }
        assertTrue(form.validationErrors().isEmpty()) // booleans have no bounds
        assertEquals(ConfigPatch(trackCitiesStopped = false), form.pendingPatch())
        assertEquals(setOf("track_cities_stopped"), putBody(form.pendingPatch()).keys)

        form.edit { it.copy(trackCitiesStopped = true, trackCitiesThrough = false) }
        assertEquals(ConfigPatch(trackCitiesThrough = false), form.pendingPatch())
        assertEquals(setOf("track_cities_through"), putBody(form.pendingPatch()).keys)

        val saved = assertIs<SaveResult.Saved>(form.save())
        assertEquals(false, saved.config.trackCitiesThrough)
        assertEquals(false, api.config.trackCitiesThrough) // PUT actually hit the server
        assertEquals(true, api.config.trackCitiesStopped)
        assertFalse(form.isDirty)
    }

    @Test
    fun `a failed save restores both city tracking toggles and surfaces the reason ANDSET-017`() = runTest {
        api.config = api.config.copy(trackCitiesStopped = true, trackCitiesThrough = false)
        val form = ConfigForm(api, Role.PARENT)
        form.load()
        form.edit { it.copy(trackCitiesStopped = false, trackCitiesThrough = true) }

        api.putConfigHandler = { throw ApiException(400, "out_of_bounds", "track_cities_through rejected by server") }
        val failed = assertIs<SaveResult.Failed>(form.save())

        assertEquals("track_cities_through rejected by server", failed.reason)
        assertEquals(failed.reason, form.lastError)
        assertEquals(true, form.values?.trackCitiesStopped) // last known server values
        assertEquals(false, form.values?.trackCitiesThrough)
        assertFalse(form.isDirty)

        // Offline is the same: both toggles roll back, with a reason.
        api.putConfigHandler = null
        api.offline = true
        form.edit { it.copy(trackCitiesStopped = false, trackCitiesThrough = true) }
        val offline = assertIs<SaveResult.Failed>(form.save())
        assertTrue(offline.reason.isNotBlank())
        assertEquals(true, form.values?.trackCitiesStopped)
        assertEquals(false, form.values?.trackCitiesThrough)
    }

    @Test
    fun `minimum city population is bounds-checked client-side ANDSET-018`() = runTest {
        // Backend CFG-009 bounds: 0-10,000,000.
        assertTrue(ConfigBounds.validate(ConfigPatch(cityMinPopulation = 0)).isEmpty())
        assertTrue(ConfigBounds.validate(ConfigPatch(cityMinPopulation = 10_000_000)).isEmpty())
        assertFalse(ConfigBounds.validate(ConfigPatch(cityMinPopulation = -1)).isEmpty())
        assertFalse(ConfigBounds.validate(ConfigPatch(cityMinPopulation = 10_000_001)).isEmpty())
        // city_radius_km keeps its 1-50 bounds.
        assertTrue(ConfigBounds.validate(ConfigPatch(cityRadiusKm = 1.0)).isEmpty())
        assertFalse(ConfigBounds.validate(ConfigPatch(cityRadiusKm = 0.9)).isEmpty())

        val form = ConfigForm(api, Role.PARENT)
        form.load()

        // Out of bounds: named, and never reaches the API.
        form.edit { it.copy(cityMinPopulation = 10_000_001) }
        assertTrue(form.validationErrors().single().contains("city_min_population"))
        assertIs<SaveResult.ValidationFailed>(form.save())
        assertEquals(5000, api.config.cityMinPopulation)

        // In bounds: sent as the only key.
        form.edit { it.copy(cityMinPopulation = 25_000) }
        assertEquals(setOf("city_min_population"), putBody(form.pendingPatch()).keys)
        assertIs<SaveResult.Saved>(form.save())
        assertEquals(25_000, api.config.cityMinPopulation)
    }

    @Test
    fun `kids see neither the city tracking switches nor the population field ANDSET-017 ANDSET-018`() = runTest {
        // Both live in the tuning form, which kid profiles never get (AND-003).
        assertFalse(FeatureVisibilityRules.forRole(Role.KID).settingsTuning)
        assertTrue(FeatureVisibilityRules.forRole(Role.PARENT).settingsTuning)

        val kidForm = ConfigForm(api, Role.KID)
        kidForm.load()
        kidForm.edit { it.copy(trackCitiesStopped = false, cityMinPopulation = 100) }
        assertIs<SaveResult.NotAllowed>(kidForm.save())
        assertEquals(true, api.config.trackCitiesStopped) // untouched
        assertEquals(5000, api.config.cityMinPopulation)
    }

    private fun putBody(patch: ConfigPatch): JsonObject =
        Json.parseToJsonElement(RoadtripJson.encodeToString(ConfigPatch.serializer(), patch)).jsonObject
}
