package com.roadtrip.core.notifications

import com.roadtrip.core.api.DeepLink
import com.roadtrip.core.api.DeepLinkKind
import com.roadtrip.core.api.NotificationItem
import com.roadtrip.core.api.NotificationKind
import com.roadtrip.core.api.NotificationsPage
import com.roadtrip.core.journal.NavTarget
import com.roadtrip.core.storage.CursorStore
import com.roadtrip.core.storage.InMemoryCursorStore
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class RecordingPoster : NotificationPoster {
    val posted = mutableListOf<LocalNotification>()
    var failNext = false

    override fun post(notification: LocalNotification) {
        if (failNext) throw IllegalStateException("simulated process death before display")
        posted += notification
    }
}

class NotificationPipelineTest {
    private val cursors = InMemoryCursorStore()
    private val poster = RecordingPoster()
    private var visible: VisibleContext? = null
    private val pipeline = NotificationPipeline(cursors, poster, { visible })
    private val self = TestData.kid.id

    private fun challenge(seq: Long, actor: String = TestData.parent.id) = NotificationItem(
        seq = seq,
        kind = NotificationKind.CHALLENGE_RECEIVED,
        text = "Derek challenged you to chess",
        gameId = "g-42",
        link = DeepLink(DeepLinkKind.GAME_REPLAY, gameId = "g-42"),
        actorId = actor,
    )

    private fun journalItem(seq: Long, text: String, actor: String = TestData.parent.id) = NotificationItem(
        seq = seq,
        kind = NotificationKind.JOURNAL_ACTIVITY,
        text = text,
        actorId = actor,
    )

    @Test
    fun `maps feed items to the challenge and journal channels with deep links ANDNOTIF-001`() {
        val posted = pipeline.process(NotificationsPage(listOf(challenge(1), journalItem(2, "New post")), 2), self)

        assertEquals(2, posted.size)
        val challengeNotif = posted.single { it.channel == NotificationChannel.CHALLENGES }
        assertEquals("Derek challenged you to chess", challengeNotif.text)
        assertEquals(NavTarget.GameBoard("g-42"), challengeNotif.target) // opens the game

        val journalNotif = posted.single { it.channel == NotificationChannel.JOURNAL }
        assertEquals(NavTarget.Journal, journalNotif.target)
    }

    @Test
    fun `journal items with a deep link open the linked target ANDNOTIF-001`() {
        val stop = journalItem(1, "Stopped at Scenic Overlook").copy(
            link = DeepLink(DeepLinkKind.MAP_PIN, lat = 39.7, lon = -105.2),
        )

        val posted = pipeline.process(NotificationsPage(listOf(stop), 1), self)

        assertEquals(NavTarget.MapPin(39.7, -105.2), posted.single().target)
    }

    @Test
    fun `never notifies for the signed-in profile's own actions ANDNOTIF-002`() {
        // Backend misbehaving: it sent the actor their own activity anyway.
        val page = NotificationsPage(
            listOf(
                journalItem(1, "my own post", actor = self),
                challenge(2, actor = self),
                journalItem(3, "someone else's post", actor = TestData.parent.id),
            ),
            3,
        )

        val posted = pipeline.process(page, self)

        assertEquals(1, posted.size)
        assertEquals("someone else's post", posted.single().text)
        assertEquals(3, cursors.get(CursorStore.NOTIFICATIONS)) // guarded items are still consumed
    }

    @Test
    fun `the cursor advances only after items are delivered to the poster ANDNOTIF-003`() {
        val page = NotificationsPage(listOf(challenge(5)), 5)

        // App killed before the notification is displayed.
        poster.failNext = true
        assertFailsWith<IllegalStateException> { pipeline.process(page, self) }
        assertEquals(0, cursors.get(CursorStore.NOTIFICATIONS))

        // Next sync re-pulls from the old cursor: the missed item surfaces instead of dropping.
        poster.failNext = false
        val posted = pipeline.process(page, self)
        assertEquals(1, posted.size)
        assertEquals(5, cursors.get(CursorStore.NOTIFICATIONS))
    }

    @Test
    fun `suppresses notifications whose target screen is visible ANDNOTIF-004`() {
        visible = VisibleContext(Screen.JOURNAL)
        val posted = pipeline.process(NotificationsPage(listOf(journalItem(1, "New post"), challenge(2)), 2), self)

        // No self-noise: the journal item is dropped while the journal is on screen,
        // but the challenge still fires.
        assertEquals(listOf(NotificationChannel.CHALLENGES), posted.map { it.channel })
        assertEquals(2, cursors.get(CursorStore.NOTIFICATIONS))

        // Watching that very game board: its challenge is suppressed too.
        visible = VisibleContext(Screen.GAME_BOARD, gameId = "g-42")
        assertTrue(pipeline.process(NotificationsPage(listOf(challenge(3)), 3), self).isEmpty())
    }

    @Test
    fun `collapses multiple journal items into one summary per pass ANDNOTIF-006`() {
        val page = NotificationsPage(
            listOf(
                journalItem(1, "Crossed into Colorado"),
                journalItem(2, "Maya posted"),
                journalItem(3, "Arrived at Rocky Mountain NP"),
                challenge(4),
            ),
            4,
        )

        val posted = pipeline.process(page, self)

        assertEquals(2, posted.size) // one journal summary + one challenge, never a storm
        val summary = posted.single { it.channel == NotificationChannel.JOURNAL }
        assertEquals(3, summary.count)
        assertEquals("3 new journal updates", summary.text)
        assertEquals(NavTarget.Journal, summary.target)
    }

    @Test
    fun `a single journal item keeps its own text instead of a summary ANDNOTIF-006`() {
        val posted = pipeline.process(NotificationsPage(listOf(journalItem(1, "Maya posted")), 1), self)

        assertEquals("Maya posted", posted.single().text)
        assertEquals(1, posted.single().count)
    }
}
