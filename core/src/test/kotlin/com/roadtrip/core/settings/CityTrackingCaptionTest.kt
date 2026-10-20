package com.roadtrip.core.settings

import com.roadtrip.core.api.Config
import com.roadtrip.core.storage.InMemoryCacheStore
import com.roadtrip.core.testing.FakeRoadtripApi
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class CityTrackingCaptionTest {
    private val base = Config(300, 100.0, 10.0, 800.0, 15.0)

    private fun caption(stopped: Boolean, through: Boolean): String? =
        CityTrackingCaption.forConfig(base.copy(trackCitiesStopped = stopped, trackCitiesThrough = through))

    @Test
    fun `no caption while both toggles are on ANDSET-019`() {
        assertNull(caption(stopped = true, through = true))
    }

    @Test
    fun `caption names what is not being tracked for every off combination ANDSET-019`() {
        assertEquals("Cities we stop at aren't being tracked", caption(stopped = false, through = true))
        assertEquals("Cities we travel through aren't being tracked", caption(stopped = true, through = false))
        assertEquals(
            "Cities we stop at and cities we travel through aren't being tracked",
            caption(stopped = false, through = false),
        )
    }

    @Test
    fun `no cached config means no caption ANDSET-019`() {
        assertNull(CityTrackingCaption.forConfig(null))
    }

    @Test
    fun `caption comes from the cached config while offline ANDSET-019`() = runTest {
        val api = FakeRoadtripApi().apply {
            config = base.copy(trackCitiesThrough = false)
        }
        val cache = InMemoryCacheStore<Config>()
        cache.write(api.getConfig(), TestData.t(0)) // last online refresh

        api.offline = true
        assertFailsWith<Exception> { api.getConfig() } // the server is unreachable now

        assertEquals(
            "Cities we travel through aren't being tracked",
            CityTrackingCaption.forConfig(cache.read()?.value),
        )
    }
}
