package com.roadtrip.core.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SyncTrigger { FOREGROUND, CONNECTIVITY_REGAINED, PERIODIC, POST_WRITE }

/**
 * Funnels every sync trigger (foreground entry, connectivity regained, periodic schedule,
 * post-write) through one serialized pass — concurrent triggers never interleave flushes
 * (ANDSYNC-007). The app module maps these to lifecycle callbacks, a connectivity listener,
 * and a WorkManager period of [periodicIntervalMinutes].
 */
class SyncScheduler(
    private val syncPass: suspend (SyncTrigger) -> Unit,
    val periodicIntervalMinutes: Long = DEFAULT_PERIOD_MINUTES,
) {
    private val mutex = Mutex()

    suspend fun onForeground() = run(SyncTrigger.FOREGROUND)
    suspend fun onConnectivityRegained() = run(SyncTrigger.CONNECTIVITY_REGAINED)
    suspend fun onPeriodic() = run(SyncTrigger.PERIODIC)
    suspend fun onLocalWrite() = run(SyncTrigger.POST_WRITE)

    private suspend fun run(trigger: SyncTrigger) {
        mutex.withLock { syncPass(trigger) }
    }

    companion object {
        const val DEFAULT_PERIOD_MINUTES = 15L
    }
}
