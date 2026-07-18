package com.roadtrip.core.location

import com.roadtrip.core.api.Config
import com.roadtrip.core.api.Profile
import com.roadtrip.core.common.Role
import com.roadtrip.core.storage.TrackerConfigStore
import com.roadtrip.core.sync.OutboxEntry
import com.roadtrip.core.sync.OutboxQueue
import java.time.Instant

/**
 * Pure sample-cadence logic: next sample is due `ping_interval_s` after the previous one;
 * a config change takes effect on the next cycle (ANDLOC-002).
 */
class PingScheduler {
    fun nextSampleDue(lastSampleAt: Instant, config: Config): Instant =
        lastSampleAt.plusSeconds(config.pingIntervalS.toLong())
}

sealed class TrackerGateResult {
    object Available : TrackerGateResult()
    data class NotAvailable(val reason: String) : TrackerGateResult()
}

/**
 * Only parent profiles may enable tracking — on any device class, phones and tablets
 * alike; kid profiles never see the toggle (ANDLOC-003/005).
 */
object TrackerGate {
    const val REASON_PARENT_REQUIRED = "parent_role_required"

    fun evaluate(role: Role): TrackerGateResult =
        if (role != Role.PARENT) {
            TrackerGateResult.NotAvailable(REASON_PARENT_REQUIRED)
        } else {
            TrackerGateResult.Available
        }
}

/** App-module port around the Android runtime-permission flow. */
interface PermissionRequester {
    suspend fun requestLocationPermissions(): Boolean
}

sealed class EnableResult {
    object Enabled : EnableResult()
    object PermissionDenied : EnableResult()
    data class NotAvailable(val reason: String) : EnableResult()
}

/**
 * Tracker enable flow: the gate is checked BEFORE any permission request, so kid profiles
 * never reach the runtime-permission path (ANDLOC-005). A successful enable records the
 * enabling parent's profile id in [config] — the attribution source for every ping the
 * device reports from then on (ANDLOC-003/008).
 */
class TrackerEnabler(
    private val permissions: PermissionRequester,
    private val config: TrackerConfigStore? = null,
) {
    suspend fun requestEnable(profile: Profile): EnableResult {
        when (val gate = TrackerGate.evaluate(profile.role)) {
            is TrackerGateResult.NotAvailable -> return EnableResult.NotAvailable(gate.reason)
            TrackerGateResult.Available -> Unit
        }
        // Only a parent ever gets here (ANDLOC-003).
        return if (permissions.requestLocationPermissions()) {
            config?.setEnabledBy(profile.id)
            EnableResult.Enabled
        } else {
            EnableResult.PermissionDenied
        }
    }
}

/**
 * Turns GPS samples into `location.ping` outbox events (sample timestamp as client_ts,
 * ANDLOC-001), attributed to the parent who enabled the tracker regardless of the
 * signed-in profile (ANDLOC-008), and tracks consecutive sample failures: single failures
 * skip silently, [failureWarningThreshold] consecutive failures raise a quiet parent
 * warning (ANDLOC-007).
 */
class TrackerController(
    private val queue: OutboxQueue,
    private val failureWarningThreshold: Int = 3,
    private val enabledBy: () -> String? = { null },
) {
    var consecutiveFailures: Int = 0
        private set

    val gpsWarning: Boolean get() = consecutiveFailures >= failureWarningThreshold

    fun onSample(lat: Double, lon: Double, accuracyM: Double?, sampleTs: Instant): OutboxEntry {
        consecutiveFailures = 0
        return queue.enqueueLocationPing(lat, lon, accuracyM, sampleTs, actorProfileId = enabledBy())
    }

    fun onSampleFailure() {
        consecutiveFailures++
    }
}
