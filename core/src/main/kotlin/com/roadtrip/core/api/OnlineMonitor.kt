package com.roadtrip.core.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Online/offline indicator state driven by GET /api/health reachability, re-checked on
 * connectivity changes and sync attempts (AND-006).
 */
class OnlineMonitor(private val api: RoadtripApi) {
    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    /** Probes /api/health; success flips online, any failure flips offline. */
    suspend fun check(): Boolean {
        val reachable = try {
            api.health().status == "ok"
        } catch (e: Exception) {
            false
        }
        _online.value = reachable
        return reachable
    }

    /** Connectivity-change hook: re-probe. */
    suspend fun onConnectivityChanged(): Boolean = check()

    /** Sync attempts double as reachability signals. */
    fun noteSyncResult(reachedServer: Boolean) {
        _online.value = reachedServer
    }
}
