package com.roadtrip.core.location

import com.roadtrip.core.api.Config
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals

class PingSchedulerTest {
    private val scheduler = PingScheduler()

    private fun config(intervalS: Int) = Config(intervalS, 100.0, 10.0, 800.0, 10.0)

    @Test
    fun `schedules the next sample one ping interval after the previous ANDLOC-002`() {
        val lastSample = TestData.t(0)

        assertEquals(TestData.t(300), scheduler.nextSampleDue(lastSample, config(300)))
    }

    @Test
    fun `follows a server config change on the next cycle ANDLOC-002`() {
        val first = scheduler.nextSampleDue(TestData.t(0), config(300))
        assertEquals(TestData.t(300), first)

        // A parent tightens ping_interval_s to 60 — the very next cycle uses it.
        val second = scheduler.nextSampleDue(first, config(60))
        assertEquals(TestData.t(360), second)
    }
}
