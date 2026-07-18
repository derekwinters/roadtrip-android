package com.roadtrip.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.roadtrip.app.MainActivity
import com.roadtrip.app.R
import com.roadtrip.app.RoadtripApplication
import com.roadtrip.app.notifications.NotificationChannels
import com.roadtrip.app.sync.SyncWork
import com.roadtrip.core.api.Config
import com.roadtrip.core.location.PingScheduler
import com.roadtrip.core.location.TrackerController
import com.roadtrip.core.sync.SyncTrigger
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Parent-device foreground location tracker (docs/spec/03-location.md) — available on any
 * device class a parent enabled it on; every ping is attributed to that enabling parent
 * (ANDLOC-003/008).
 *
 * Runs as a foregroundServiceType="location" service with the persistent "Trip tracking
 * active" notification (ANDLOC-006), samples GPS once per ping_interval_s from server
 * config (ANDLOC-002 via the core PingScheduler), and enqueues each sample as a
 * location.ping outbox event immediately so tracking works offline (ANDLOC-001/004).
 * A single failed fix skips the cycle; three consecutive failures raise the quiet in-app
 * warning (ANDLOC-007 via the core TrackerController). An inexact allow-while-idle alarm
 * re-delivers a tick as a Doze fallback for the coroutine loop. The loop idles while no
 * trip is active (docs/spec/09-trips.md).
 */
class TrackerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    private val container get() = (application as RoadtripApplication).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // Disabling clears the enabling-parent record (ANDLOC-003/008).
                container.settings.setEnabledBy(null)
                stopTracking()
                return START_NOT_STICKY
            }
            else -> {
                // ACTION_START, ACTION_TICK and system restarts all (re)ensure the loop.
                goForeground()
                startLoop()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        cancelFallbackAlarm()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= 29) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun buildNotification(): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TrackerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationChannels.TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.tracking_stop), stopIntent)
            .build()
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        // Pings are attributed to the parent who enabled the tracker, regardless of the
        // signed-in profile (ANDLOC-008).
        val controller = TrackerController(
            container.outboxQueue,
            enabledBy = { container.settings.enabledBy() },
        )
        val scheduler = PingScheduler()
        loopJob = serviceScope.launch {
            while (isActive) {
                // The tracker never runs between trips (docs/spec/09-trips.md): keep the
                // service alive but skip sampling until a trip is active again.
                if (!container.trackerMayRun()) {
                    delay(BETWEEN_TRIPS_POLL_MS)
                    continue
                }
                val config = container.configCache.read()?.value ?: DEFAULT_CONFIG
                val cycleStart = Instant.now()

                val location = sampleLocation()
                if (location != null) {
                    controller.onSample(
                        lat = location.latitude,
                        lon = location.longitude,
                        accuracyM = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                        sampleTs = Instant.ofEpochMilli(location.time),
                    )
                    container.requestSync(SyncTrigger.POST_WRITE)
                    SyncWork.syncNow(applicationContext)
                } else {
                    controller.onSampleFailure()
                }
                container.trackerGpsWarning.value = controller.gpsWarning

                scheduleFallbackAlarm(config.pingIntervalS)
                val nextDue = scheduler.nextSampleDue(cycleStart, config)
                val waitMs = Duration.between(Instant.now(), nextDue).toMillis()
                delay(waitMs.coerceAtLeast(MIN_CYCLE_MS))
            }
        }
    }

    private fun stopTracking() {
        loopJob?.cancel()
        loopJob = null
        cancelFallbackAlarm()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** One GPS fix with a timeout; null = no fix this cycle (ANDLOC-007). */
    @SuppressLint("MissingPermission")
    private suspend fun sampleLocation(): Location? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }
        return withTimeoutOrNull(SAMPLE_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                // Explicit object (not a SAM lambda): the provider callbacks are still
                // abstract on API 26-29 devices, so all of them must be implemented.
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (continuation.isActive) continuation.resume(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

                    override fun onProviderEnabled(provider: String) {}

                    override fun onProviderDisabled(provider: String) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                try {
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(provider, listener, mainLooper)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
            }
        }
    }

    private fun fallbackAlarmIntent(): PendingIntent = PendingIntent.getForegroundService(
        this,
        2,
        Intent(this, TrackerService::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Doze fallback: an allow-while-idle tick shortly after the next sample is due. */
    private fun scheduleFallbackAlarm(intervalS: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + (intervalS + FALLBACK_SLACK_S) * 1000L,
            fallbackAlarmIntent(),
        )
    }

    private fun cancelFallbackAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(fallbackAlarmIntent())
    }

    companion object {
        private const val ACTION_START = "com.roadtrip.app.tracker.START"
        private const val ACTION_STOP = "com.roadtrip.app.tracker.STOP"
        private const val ACTION_TICK = "com.roadtrip.app.tracker.TICK"

        private const val NOTIFICATION_ID = 1001
        private const val SAMPLE_TIMEOUT_MS = 60_000L
        private const val MIN_CYCLE_MS = 5_000L
        private const val BETWEEN_TRIPS_POLL_MS = 60_000L
        private const val FALLBACK_SLACK_S = 60

        /** Used only until the first server config lands in the cache. */
        private val DEFAULT_CONFIG = Config(
            pingIntervalS = 300,
            stopRadiusM = 150.0,
            minStopDurationMin = 5.0,
            arrivalRadiusM = 500.0,
            cityRadiusKm = 10.0,
        )

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackerService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TrackerService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
