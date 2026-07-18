package com.roadtrip.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.roadtrip.app.R

/**
 * The app's notification channels (docs/spec/06-notifications.md): challenges are
 * high-importance, journal activity default, plus a low-importance channel for the
 * persistent tracking notification (ANDLOC-006).
 */
object NotificationChannels {
    const val CHALLENGES = "challenges"
    const val JOURNAL = "journal"
    const val TRACKING = "tracking"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHALLENGES,
                context.getString(R.string.channel_challenges_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                JOURNAL,
                context.getString(R.string.channel_journal_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                TRACKING,
                context.getString(R.string.channel_tracking_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
}
