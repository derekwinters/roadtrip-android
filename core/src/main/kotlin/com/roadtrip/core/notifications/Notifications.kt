package com.roadtrip.core.notifications

import com.roadtrip.core.api.NotificationItem
import com.roadtrip.core.api.NotificationKind
import com.roadtrip.core.api.NotificationsPage
import com.roadtrip.core.journal.DeepLinkRouter
import com.roadtrip.core.journal.NavTarget
import com.roadtrip.core.storage.CursorStore

/** The two Android notification channels (06-notifications.md). */
enum class NotificationChannel { CHALLENGES, JOURNAL }

data class LocalNotification(
    val channel: NotificationChannel,
    val text: String,
    val target: NavTarget,
    val count: Int = 1,
)

enum class Screen { JOURNAL, MAP, GAMES, GAME_BOARD, CHECKLIST, TRIP, SETTINGS }

data class VisibleContext(val screen: Screen, val gameId: String? = null)

/** App-module port that actually posts to NotificationManager. */
interface NotificationPoster {
    fun post(notification: LocalNotification)
}

/** Maps backend feed items to channels, text and deep-link targets (ANDNOTIF-001). */
object NotificationMapper {
    fun map(item: NotificationItem): LocalNotification = when (item.kind) {
        NotificationKind.CHALLENGE_RECEIVED -> LocalNotification(
            channel = NotificationChannel.CHALLENGES,
            text = item.text,
            // A challenge opens the game itself.
            target = (item.gameId ?: item.link?.gameId)
                ?.let { NavTarget.GameBoard(it) }
                ?: NavTarget.Journal,
        )
        NotificationKind.JOURNAL_ACTIVITY -> LocalNotification(
            channel = NotificationChannel.JOURNAL,
            text = item.text,
            target = item.link?.let(DeepLinkRouter::route) ?: NavTarget.Journal,
        )
    }
}

/**
 * One sync pass of the notification feed: filter self-notifications (ANDNOTIF-002),
 * suppress items whose target screen is visible (ANDNOTIF-004), collapse multiple journal
 * items into a single summary (ANDNOTIF-006), post, and only then advance the cursor
 * (ANDNOTIF-003 — a killed app re-surfaces missed items on the next sync).
 */
class NotificationPipeline(
    private val cursors: CursorStore,
    private val poster: NotificationPoster,
    private val visibleContext: () -> VisibleContext?,
    private val cursorKey: String = CursorStore.NOTIFICATIONS,
) {
    /** Returns the notifications actually posted. */
    fun process(page: NotificationsPage, selfProfileId: String?): List<LocalNotification> {
        val visible = visibleContext()

        val relevant = page.items
            // Backend already excludes the actor; guard again in case it misbehaves.
            .filterNot { selfProfileId != null && it.actorId == selfProfileId }
            .filterNot { suppressedByVisibleScreen(it, visible) }

        val journalItems = relevant.filter { it.kind == NotificationKind.JOURNAL_ACTIVITY }
        val challengeItems = relevant.filter { it.kind == NotificationKind.CHALLENGE_RECEIVED }

        val toPost = buildList {
            addAll(challengeItems.map(NotificationMapper::map))
            when {
                journalItems.size == 1 -> add(NotificationMapper.map(journalItems.single()))
                journalItems.size > 1 -> add(
                    // One summary per pass — no storms after a long offline stretch.
                    LocalNotification(
                        channel = NotificationChannel.JOURNAL,
                        text = "${journalItems.size} new journal updates",
                        target = NavTarget.Journal,
                        count = journalItems.size,
                    ),
                )
            }
        }

        // Deliver FIRST; the cursor only advances afterwards, so dying here re-surfaces
        // these items on the next sync instead of dropping them.
        toPost.forEach(poster::post)
        cursors.set(cursorKey, page.nextAfter)
        return toPost
    }

    private fun suppressedByVisibleScreen(item: NotificationItem, visible: VisibleContext?): Boolean {
        visible ?: return false
        return when (item.kind) {
            NotificationKind.JOURNAL_ACTIVITY -> visible.screen == Screen.JOURNAL
            NotificationKind.CHALLENGE_RECEIVED ->
                visible.screen == Screen.GAME_BOARD &&
                    visible.gameId != null &&
                    visible.gameId == (item.gameId ?: item.link?.gameId)
        }
    }
}
