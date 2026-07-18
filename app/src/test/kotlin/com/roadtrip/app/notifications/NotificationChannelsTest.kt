package com.roadtrip.app.notifications

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * covers: ANDNOTIF-001 (the two feed channels exist with the documented importances;
 * docs/spec/06-notifications.md channel table) and the tracking channel for ANDLOC-006.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotificationChannelsTest {

    @Test
    fun createsChallengesJournalAndTrackingChannelsWithDocumentedImportance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationChannels.ensureCreated(context)

        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))

        val challenges = requireNotNull(manager.getNotificationChannel(NotificationChannels.CHALLENGES))
        assertEquals(NotificationManager.IMPORTANCE_HIGH, challenges.importance)

        val journal = requireNotNull(manager.getNotificationChannel(NotificationChannels.JOURNAL))
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, journal.importance)

        val tracking = requireNotNull(manager.getNotificationChannel(NotificationChannels.TRACKING))
        assertEquals(NotificationManager.IMPORTANCE_LOW, tracking.importance)
    }

    @Test
    fun ensureCreatedIsIdempotent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationChannels.ensureCreated(context)
        NotificationChannels.ensureCreated(context)

        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        assertNotNull(manager.getNotificationChannel(NotificationChannels.CHALLENGES))
    }
}
