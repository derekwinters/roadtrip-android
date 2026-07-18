package com.roadtrip.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.roadtrip.app.RoadtripApplication
import com.roadtrip.core.sync.SyncScheduler
import java.util.concurrent.TimeUnit

/**
 * Background sync pass (ANDSYNC-007): a 15-minute periodic schedule plus one-shot requests
 * as a durable backstop for in-process triggers. All passes funnel through the container's
 * single [SyncScheduler], so concurrent triggers never interleave flushes.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? RoadtripApplication ?: return Result.failure()
        return try {
            app.container.syncScheduler.onPeriodic()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

object SyncWork {
    private const val PERIODIC_NAME = "roadtrip-sync-periodic"
    private const val ONESHOT_NAME = "roadtrip-sync-now"

    private val onlineConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            SyncScheduler.DEFAULT_PERIOD_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(onlineConstraint)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** One-shot pass that survives process death (e.g. after a tracker write in the background). */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(onlineConstraint)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONESHOT_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
