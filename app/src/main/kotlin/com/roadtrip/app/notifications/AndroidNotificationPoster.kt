package com.roadtrip.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.roadtrip.app.R
import com.roadtrip.core.journal.NavTarget
import com.roadtrip.core.notifications.LocalNotification
import com.roadtrip.core.notifications.NotificationChannel as CoreChannel
import com.roadtrip.core.notifications.NotificationPoster

/**
 * Posts core [LocalNotification]s to the system tray with deep-link PendingIntents into
 * MainActivity (ANDNOTIF-005). Journal notifications reuse one id so a pass's summary
 * replaces older ones (ANDNOTIF-006); challenge notifications get per-game ids.
 */
class AndroidNotificationPoster(private val context: Context) : NotificationPoster {

    @SuppressLint("MissingPermission")
    override fun post(notification: LocalNotification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val channelId = when (notification.channel) {
            CoreChannel.CHALLENGES -> NotificationChannels.CHALLENGES
            CoreChannel.JOURNAL -> NotificationChannels.JOURNAL
        }
        val intent = NavTargetExtras.intentFor(context, notification.target)
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId(notification),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(notification.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.text))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
        if (notification.count > 1) builder.setNumber(notification.count)

        try {
            manager.notify(notificationId(notification), builder.build())
        } catch (e: SecurityException) {
            // Permission revoked mid-flight; the item resurfaces on the next sync (ANDNOTIF-003).
        }
    }

    private fun notificationId(notification: LocalNotification): Int = when (notification.channel) {
        CoreChannel.JOURNAL -> JOURNAL_ID
        CoreChannel.CHALLENGES -> when (val target = notification.target) {
            is NavTarget.GameBoard -> CHALLENGE_ID_BASE + (target.gameId.hashCode() and 0xFFFF)
            else -> CHALLENGE_ID_BASE
        }
    }

    companion object {
        private const val JOURNAL_ID = 2001
        private const val CHALLENGE_ID_BASE = 3000
    }
}
