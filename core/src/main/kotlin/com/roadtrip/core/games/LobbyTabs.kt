package com.roadtrip.core.games

import com.roadtrip.core.api.Game
import com.roadtrip.core.api.GameStatus

/** The two lobby tabs: currently-playable/joinable content vs the finished-game replay archive. */
enum class LobbyTab { ACTIVE, FINISHED }

/**
 * Content of the Active tab (ANDGAME-021). Everything currently playable or joinable, plus the
 * always-available family bingo entry that pins to the top. Bingo is a static, offline-capable
 * entry rather than a [Game] (ANDBNG-001), so it's a flag here rather than a list member.
 */
data class ActiveTabContent(
    /** The License-plate-bingo family entry pinned to the top of Active — always present. */
    val includesBingo: Boolean,
    val myGames: List<MyGame>,
    val incomingChallenges: List<Game>,
    val openGames: List<Game>,
    val spectatable: List<Game>,
)

/** Per-tab bucketing of the lobby (ANDGAME-021). */
data class LobbyTabContent(
    val active: ActiveTabContent,
    /** The finished-game replay archive shown on the Finished tab. */
    val finished: List<Game>,
)

/**
 * Pure tab-bucketing seam for the games lobby (ANDGAME-021). The lobby is split into an Active
 * tab (family bingo + my games + challenges for you + open games + watch-live) and a Finished
 * tab (the replay archive). A game belongs to Finished iff its status is [GameStatus.FINISHED];
 * everything else — plus the static bingo entry — is Active.
 *
 * The selected tab is never persisted: the screen always opens on [DEFAULT] (Active) on every
 * entry and after process restart. This object owns the classification so the composable stays
 * thin and the bucketing is JVM-testable, exactly like [LobbyReducer]/[GameLobbyLabeler].
 */
object LobbyTabs {
    /** The tab the lobby always opens on; selection is not remembered across entries. */
    val DEFAULT: LobbyTab = LobbyTab.ACTIVE

    /** Which tab a single game belongs to: FINISHED for finished games, ACTIVE otherwise. */
    fun tabFor(game: Game): LobbyTab =
        if (game.status == GameStatus.FINISHED) LobbyTab.FINISHED else LobbyTab.ACTIVE

    /**
     * Buckets the raw games list (plus the viewing profile) into the two tabs' content. Uses
     * [LobbyReducer] for the Active-tab sub-sections so their rules stay in one place, computes
     * the watch-live (spectatable) set — active games the viewer neither created nor joined — and
     * partitions the finished replay archive by [tabFor].
     */
    fun bucket(games: List<Game>, myProfileId: String): LobbyTabContent {
        val lobby = LobbyReducer.reduce(games, myProfileId)
        val spectatable = games.filter {
            it.status == GameStatus.ACTIVE && it.createdBy != myProfileId && it.opponentId != myProfileId
        }
        val finished = games.filter { tabFor(it) == LobbyTab.FINISHED }
        return LobbyTabContent(
            active = ActiveTabContent(
                includesBingo = true,
                myGames = lobby.myGames,
                incomingChallenges = lobby.incomingChallenges,
                openGames = lobby.openGames,
                spectatable = spectatable,
            ),
            finished = finished,
        )
    }
}
