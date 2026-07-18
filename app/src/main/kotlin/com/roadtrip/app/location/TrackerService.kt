package com.roadtrip.app.location

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Parent-phone foreground location tracker (ANDLOC-001..007).
 * OWNER: android location feature. Stub until implemented.
 */
class TrackerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
