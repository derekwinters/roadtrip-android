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

/**
 * App-module port around the Doze/OEM battery-optimization exemption prompt
 * (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, ANDLOC-011). Called only from the parent enable
 * flow, so kid profiles never reach it (ANDLOC-005).
 */
interface BatteryOptimizationExempter {
    /** Ask the OS to exempt the app from battery optimization; a no-op if already exempt. */
    fun requestExemptionIfNeeded()
}

/**
 * Shared "should the tracker be running right now?" predicate for every restart path
 * (ANDLOC-009/010): a parent left the tracker enabled on this device AND a trip is active —
 * the exact condition the app uses to restore the service on relaunch. Reused by the
 * task-removed self-restart and the `BOOT_COMPLETED` receiver so all three paths agree
 * without duplicating the rule. `trackerMayRun` stays permissive until trips are known
 * (its caller returns true when the active-trip state is unknown).
 */
object TrackerRestartPolicy {
    fun shouldRun(trackerEnabled: Boolean, trackerMayRun: Boolean): Boolean =
        trackerEnabled && trackerMayRun
}

/**
 * Doze fallback alarm mode (ANDLOC-012). While a trip is active the fallback tick must be
 * exact so it cannot coalesce/drift under Doze (03-location.md), but exact allow-while-idle
 * alarms are permission-gated on API 31+ — so use the exact form only when both a trip is
 * active and the exact-alarm permission is held, and fall back to the inexact form
 * otherwise.
 */
object DozeAlarmPolicy {
    enum class Mode { EXACT, INEXACT }

    fun mode(tripActive: Boolean, canScheduleExact: Boolean): Mode =
        if (tripActive && canScheduleExact) Mode.EXACT else Mode.INEXACT
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
    private val batteryOptimization: BatteryOptimizationExempter? = null,
) {
    suspend fun requestEnable(profile: Profile): EnableResult {
        when (val gate = TrackerGate.evaluate(profile.role)) {
            is TrackerGateResult.NotAvailable -> return EnableResult.NotAvailable(gate.reason)
            TrackerGateResult.Available -> Unit
        }
        // Only a parent ever gets here (ANDLOC-003).
        return if (permissions.requestLocationPermissions()) {
            config?.setEnabledBy(profile.id)
            // Parent-only: keep the foreground service alive under Doze/OEM battery managers
            // (ANDLOC-011). Kids never reach this point (ANDLOC-005).
            batteryOptimization?.requestExemptionIfNeeded()
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
