package com.roadtrip.core.bingo

import com.roadtrip.core.api.JournalEntry
import com.roadtrip.core.api.NotificationsPage
import com.roadtrip.core.notifications.LocalNotification
import com.roadtrip.core.notifications.NotificationPipeline
import com.roadtrip.core.notifications.NotificationPoster
import com.roadtrip.core.storage.CursorStore
import com.roadtrip.core.storage.InMemoryCacheStore
import com.roadtrip.core.storage.InMemoryCursorStore
import com.roadtrip.core.sync.InboxPuller
import com.roadtrip.core.sync.JournalCacheApplier
import com.roadtrip.core.testing.FakeClock
import com.roadtrip.core.testing.FakeRoadtripApi
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * ANDBNG-005: bingo is quiet. Plate events fold into the bingo card but never become
 * journal entries or local notifications anywhere in the client pipeline.
 */
class BingoNotificationExclusionTest {
    private class RecordingPoster : NotificationPoster {
        val posted = mutableListOf<LocalNotification>()

        override fun post(notification: LocalNotification) {
            posted += notification
        }
    }

    @Test
    fun `plate events produce no journal entries and no local notifications ANDBNG-005`() = runTest {
        val api = FakeRoadtripApi()
        api.feed += listOf(
            TestData.plateSpottedEvent(1, "CO", TestData.kid.id),
            TestData.plateSpottedEvent(2, "UT", TestData.otherKid.id),
            TestData.plateUnspottedEvent(3, "UT", TestData.otherKid.id),
            // Control: a real journal post still lands in the journal cache.
            TestData.journalPostEvent(4, TestData.parent.id, "lunch stop!"),
        )

        val cursors = InMemoryCursorStore()
        val journalCache = InMemoryCacheStore<List<JournalEntry>>()
        val journalApplier = JournalCacheApplier(journalCache, FakeClock())

        // The sync pass: pull the feed into the read models...
        var bingo = com.roadtrip.core.api.BingoCard()
        val bingoApplier = com.roadtrip.core.sync.EventApplier { events ->
            events.forEach { bingo = BingoReducer.applyEvent(bingo, it) }
        }
        val puller = InboxPuller(api, cursors, appliers = listOf(journalApplier, bingoApplier))
        assertEquals(4, puller.pullOnce())

        // ...the plate events reached the bingo card (they are consumed, not dropped)...
        assertEquals("CO", bingo.cells.single().stateCode)

        // ...but the journal cache ignores every plate.* event (no journal entries for bingo).
        assertEquals(listOf("lunch stop!"), journalCache.read()?.value.orEmpty().map { it.text })

        // The backend emits no notification items for bingo (contract); the pipeline pass
        // that follows a plate-heavy sync therefore posts nothing — and advances its
        // cursor without inventing any bingo notification client-side.
        val poster = RecordingPoster()
        val pipeline = NotificationPipeline(cursors, poster, { null })
        val posted = pipeline.process(NotificationsPage(items = emptyList(), nextAfter = 4), TestData.kid.id)

        assertTrue(posted.isEmpty())
        assertTrue(poster.posted.isEmpty())
        assertEquals(4L, cursors.get(CursorStore.NOTIFICATIONS))
    }
}
