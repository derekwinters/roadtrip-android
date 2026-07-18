package com.roadtrip.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class SyncSchedulerTest {

    @Test
    fun `runs a pass for every trigger with a 15 minute default period ANDSYNC-007`() = runTest {
        val triggers = mutableListOf<SyncTrigger>()
        val scheduler = SyncScheduler({ triggers += it })

        scheduler.onForeground()
        scheduler.onConnectivityRegained()
        scheduler.onPeriodic()
        scheduler.onLocalWrite()

        assertEquals(
            listOf(
                SyncTrigger.FOREGROUND,
                SyncTrigger.CONNECTIVITY_REGAINED,
                SyncTrigger.PERIODIC,
                SyncTrigger.POST_WRITE,
            ),
            triggers,
        )
        assertEquals(15L, scheduler.periodicIntervalMinutes)
    }

    @Test
    fun `serializes concurrent sync passes so flushes never interleave ANDSYNC-007`() = runTest {
        val log = mutableListOf<String>()
        val scheduler = SyncScheduler({ trigger ->
            log += "start:$trigger"
            delay(50)
            log += "end:$trigger"
        })

        val a = launch { scheduler.onForeground() }
        val b = launch { scheduler.onLocalWrite() }
        a.join()
        b.join()

        assertEquals(
            listOf(
                "start:FOREGROUND",
                "end:FOREGROUND",
                "start:POST_WRITE",
                "end:POST_WRITE",
            ),
            log,
        )
    }
}
