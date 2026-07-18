package com.roadtrip.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import com.roadtrip.app.di.AppContainer
import com.roadtrip.app.notifications.NotificationChannels
import com.roadtrip.app.sync.SyncWork
import com.roadtrip.core.sync.SyncTrigger
import kotlinx.coroutines.launch

class RoadtripApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.ensureCreated(this)
        SyncWork.schedulePeriodic(this)
        registerConnectivityListener()
    }

    /** Connectivity regained is a sync trigger (ANDSYNC-007) and re-probes health (AND-006). */
    private fun registerConnectivityListener() {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return
        try {
            connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    container.requestSync(SyncTrigger.CONNECTIVITY_REGAINED)
                }

                override fun onLost(network: Network) {
                    container.appScope.launch { container.onlineMonitor.onConnectivityChanged() }
                }
            })
        } catch (e: Exception) {
            // Callback registration can fail on exotic devices; periodic sync still runs.
        }
    }
}
