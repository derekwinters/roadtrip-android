package com.roadtrip.core.settings

import com.roadtrip.core.api.ApiException
import com.roadtrip.core.api.Config
import com.roadtrip.core.api.ConfigPatch
import com.roadtrip.core.api.RoadtripApi
import com.roadtrip.core.common.Role
import java.io.IOException

/**
 * Client-side bounds identical to the backend bounds table
 * (roadtrip-backend/docs/spec/05-config.md) — ANDSET-001.
 */
object ConfigBounds {
    val PING_INTERVAL_S = 5..3600
    val STOP_RADIUS_M = 20.0..1000.0
    val MIN_STOP_DURATION_MIN = 1.0..240.0
    val ARRIVAL_RADIUS_M = 100.0..5000.0
    val CITY_RADIUS_KM = 1.0..50.0

    /** Returns "key out of bounds" style errors for every violated key. */
    fun validate(patch: ConfigPatch): List<String> = buildList {
        patch.pingIntervalS?.let {
            if (it !in PING_INTERVAL_S) add(outOfBounds("ping_interval_s", PING_INTERVAL_S.first, PING_INTERVAL_S.last))
        }
        patch.stopRadiusM?.let {
            if (it !in STOP_RADIUS_M) add(outOfBounds("stop_radius_m", STOP_RADIUS_M.start, STOP_RADIUS_M.endInclusive))
        }
        patch.minStopDurationMin?.let {
            if (it !in MIN_STOP_DURATION_MIN) {
                add(outOfBounds("min_stop_duration_min", MIN_STOP_DURATION_MIN.start, MIN_STOP_DURATION_MIN.endInclusive))
            }
        }
        patch.arrivalRadiusM?.let {
            if (it !in ARRIVAL_RADIUS_M) {
                add(outOfBounds("arrival_radius_m", ARRIVAL_RADIUS_M.start, ARRIVAL_RADIUS_M.endInclusive))
            }
        }
        patch.cityRadiusKm?.let {
            if (it !in CITY_RADIUS_KM) add(outOfBounds("city_radius_km", CITY_RADIUS_KM.start, CITY_RADIUS_KM.endInclusive))
        }
    }

    private fun outOfBounds(key: String, min: Number, max: Number): String =
        "$key must be between $min and $max"
}

sealed class SaveResult {
    data class Saved(val config: Config) : SaveResult()
    data class ValidationFailed(val errors: List<String>) : SaveResult()

    /** API failure — form values were restored to the last known server values. */
    data class Failed(val reason: String) : SaveResult()
    object NotAllowed : SaveResult()
}

/**
 * Detection-tuning form: loads GET /api/config, validates edits client-side against the
 * backend bounds, tracks dirtiness, saves parent-only via PUT /api/config (ANDSET-001).
 * API save failures restore the last known server values and expose the reason (ANDSET-002).
 */
class ConfigForm(
    private val api: RoadtripApi,
    private val role: Role,
) {
    var values: Config? = null
        private set

    var lastServerValues: Config? = null
        private set

    var lastError: String? = null
        private set

    val isDirty: Boolean get() = values != lastServerValues
    val canSave: Boolean get() = role == Role.PARENT

    suspend fun load(): Config {
        val config = api.getConfig()
        lastServerValues = config
        values = config
        lastError = null
        return config
    }

    fun edit(transform: (Config) -> Config) {
        val current = checkNotNull(values) { "load() the form before editing" }
        values = transform(current)
    }

    /** Live validation of the currently edited values. */
    fun validationErrors(): List<String> = ConfigBounds.validate(pendingPatch())

    /** The partial PUT body: only keys that differ from the server values. */
    fun pendingPatch(): ConfigPatch {
        val edited = values ?: return ConfigPatch()
        val server = lastServerValues ?: return ConfigPatch()
        return ConfigPatch(
            pingIntervalS = edited.pingIntervalS.takeIf { it != server.pingIntervalS },
            stopRadiusM = edited.stopRadiusM.takeIf { it != server.stopRadiusM },
            minStopDurationMin = edited.minStopDurationMin.takeIf { it != server.minStopDurationMin },
            arrivalRadiusM = edited.arrivalRadiusM.takeIf { it != server.arrivalRadiusM },
            cityRadiusKm = edited.cityRadiusKm.takeIf { it != server.cityRadiusKm },
        )
    }

    suspend fun save(): SaveResult {
        if (!canSave) return SaveResult.NotAllowed

        val errors = validationErrors()
        if (errors.isNotEmpty()) return SaveResult.ValidationFailed(errors)

        return try {
            val updated = api.putConfig(pendingPatch())
            lastServerValues = updated
            values = updated
            lastError = null
            SaveResult.Saved(updated)
        } catch (e: ApiException) {
            restore(e.message ?: "save rejected")
        } catch (e: IOException) {
            restore("offline: ${e.message ?: "server unreachable"}")
        }
    }

    private fun restore(reason: String): SaveResult.Failed {
        values = lastServerValues // roll the form back to the last known server values
        lastError = reason
        return SaveResult.Failed(reason)
    }
}
