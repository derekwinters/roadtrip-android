package com.roadtrip.app.ui.games

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadtrip.app.di.AppContainer
import com.roadtrip.app.ui.common.OfflineBanner
import com.roadtrip.app.ui.common.SectionHeader
import com.roadtrip.core.api.Game
import com.roadtrip.core.api.GameType
import com.roadtrip.core.api.Profile
import com.roadtrip.core.games.GameLobbyLabeler
import com.roadtrip.core.games.GameTypeChoices
import com.roadtrip.core.games.GameOfflineGate
import com.roadtrip.core.games.GamesLobbyLayout
import com.roadtrip.core.games.LobbyTab
import com.roadtrip.core.games.LobbyTabs
import com.roadtrip.core.sync.SyncTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Games lobby (ANDGAME-001): open games to join, my games with turn indicator, incoming
 * challenges, plus spectate and replay entries. Actions are online-only with an
 * explanatory banner offline (ANDGAME-008); cached replays keep working.
 *
 * Pull-to-refresh and a header refresh button share one reload path (ANDGAME-010); rows attribute
 * the creator/opponent by name (ANDGAME-009); the list clears the FAB with a bottom inset (ANDGAME-011).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    container: AppContainer,
    profile: Profile,
    onOpenBoard: (String) -> Unit,
    onOpenReplay: (String) -> Unit,
    onOpenBingo: () -> Unit,
) {
    val tick by container.refreshTick.collectAsState()
    val online by container.onlineMonitor.online.collectAsState()
    val scope = rememberCoroutineScope()
    var showCreateDialog by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    val games: List<Game> = remember(tick) { container.gamesCache.read()?.value.orEmpty() }
    // profileId -> display name, resolved from the cache for row attribution (ANDGAME-009).
    val names: Map<String, String> = remember(tick) {
        container.profilesCache.read()?.value.orEmpty().associate { it.id to it.name }
    }
    // Tab-bucketing seam (ANDGAME-021): Active (bingo + my games + challenges + open + watch-live)
    // vs Finished (replay archive).
    val tabs = remember(games) { LobbyTabs.bucket(games, profile.id) }
    val active = tabs.active
    val finished = tabs.finished
    // Which tab is showing. Plain local state initialised to the default (Active) so it resets on
    // every entry to the screen — never remembered across navigation or process restart
    // (ANDGAME-021: "start on active games all the time", no rememberSaveable).
    var selectedTab by remember { mutableStateOf(LobbyTabs.DEFAULT) }

    val gate = GameOfflineGate.evaluate(online)

    // Shared reload for both the pull gesture and the header button (ANDGAME-010). Offline is
    // short-circuited inside reloadLobby(), so the indicator dismisses instead of spinning.
    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            try {
                container.reloadLobby()
            } finally {
                refreshing = false
            }
        }
    }

    fun join(game: Game) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { container.api.joinGame(game.id) } }
            result.fold(
                onSuccess = {
                    container.requestSync(SyncTrigger.POST_WRITE)
                    onOpenBoard(game.id)
                },
                onFailure = { actionError = it.message ?: "Could not join the game" },
            )
        }
    }

    Scaffold(
        floatingActionButton = {
            if (gate.enabled) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "New game")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!gate.enabled) OfflineBanner(gate.reason ?: "Offline")

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Games",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { refresh() }, enabled = !refreshing) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }

            actionError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // Active / Finished split (ANDGAME-021): keep active content above the growing replay
            // archive. Selection lives in plain state above, so it always resets to Active.
            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == LobbyTab.ACTIVE,
                    onClick = { selectedTab = LobbyTab.ACTIVE },
                    text = { Text("Active") },
                )
                Tab(
                    selected = selectedTab == LobbyTab.FINISHED,
                    onClick = { selectedTab = LobbyTab.FINISHED },
                    text = { Text("Finished") },
                )
            }

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refresh() },
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Bottom inset so the last row's action clears the FAB in every section (ANDGAME-011).
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = GamesLobbyLayout.FAB_CLEARANCE_DP.dp,
                ),
            ) {
                when (selectedTab) {
                    // Active tab (ANDGAME-021): everything currently playable or joinable.
                    LobbyTab.ACTIVE -> {
                        // License plate bingo pinned to the top: the whole family fills one card
                        // together; spots queue offline, so the entry never needs the online gate
                        // (ANDBNG-001).
                        item { SectionHeader("Family games") }
                        item {
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("License plate bingo", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "Spot all 50 states + DC — works offline",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(onClick = onOpenBingo) { Text("Open") }
                                }
                            }
                        }

                        item { SectionHeader("My games") }
                        if (active.myGames.isEmpty()) {
                            item { Text("No active games", style = MaterialTheme.typography.bodyMedium) }
                        }
                        items(active.myGames, key = { "my-${it.game.id}" }) { myGame ->
                            GameRow(
                                title = GameLobbyLabeler.myGameTitle(
                                    myGame.game, profile.id, gameTypeLabel(myGame.game.gameType), names,
                                ),
                                trailing = if (myGame.myTurn) "Your turn" else "Waiting…",
                                highlight = myGame.myTurn,
                                actionLabel = "Open",
                                actionEnabled = true,
                                onAction = { onOpenBoard(myGame.game.id) },
                            )
                        }

                        item { SectionHeader("Challenges for you") }
                        if (active.incomingChallenges.isEmpty()) {
                            item { Text("No challenges", style = MaterialTheme.typography.bodyMedium) }
                        }
                        items(active.incomingChallenges, key = { "ch-${it.id}" }) { game ->
                            GameRow(
                                title = GameLobbyLabeler.creatorTitle(game, gameTypeLabel(game.gameType), names),
                                trailing = "Challenge",
                                highlight = true,
                                actionLabel = "Accept",
                                actionEnabled = gate.enabled,
                                onAction = { join(game) },
                            )
                        }

                        item { SectionHeader("Open games") }
                        if (active.openGames.isEmpty()) {
                            item { Text("No open games", style = MaterialTheme.typography.bodyMedium) }
                        }
                        items(active.openGames, key = { "open-${it.id}" }) { game ->
                            GameRow(
                                title = GameLobbyLabeler.creatorTitle(game, gameTypeLabel(game.gameType), names),
                                trailing = "Open to anyone",
                                highlight = false,
                                actionLabel = "Join",
                                actionEnabled = gate.enabled,
                                onAction = { join(game) },
                            )
                        }

                        item { SectionHeader("Watch live") }
                        if (active.spectatable.isEmpty()) {
                            item { Text("Nothing to spectate", style = MaterialTheme.typography.bodyMedium) }
                        }
                        items(active.spectatable, key = { "spec-${it.id}" }) { game ->
                            GameRow(
                                title = GameLobbyLabeler.creatorTitle(game, gameTypeLabel(game.gameType), names),
                                trailing = "Live",
                                highlight = false,
                                actionLabel = "Spectate",
                                actionEnabled = gate.enabled,
                                onAction = { onOpenBoard(game.id) },
                            )
                        }
                    }

                    // Finished tab (ANDGAME-021): the replay archive, with its own empty state.
                    LobbyTab.FINISHED -> {
                        if (finished.isEmpty()) {
                            item { Text("No finished games yet", style = MaterialTheme.typography.bodyMedium) }
                        }
                        items(finished, key = { "fin-${it.id}" }) { game ->
                            GameRow(
                                title = GameLobbyLabeler.creatorTitle(game, gameTypeLabel(game.gameType), names),
                                trailing = "Finished",
                                highlight = false,
                                actionLabel = "Replay",
                                // Cached replays work offline too (ANDGAME-008).
                                actionEnabled = true,
                                onAction = { onOpenReplay(game.id) },
                            )
                        }
                    }
                }
            }
            }
        }
    }

    if (showCreateDialog) {
        CreateGameDialog(
            container = container,
            profile = profile,
            onDismiss = { showCreateDialog = false },
            onCreated = { game ->
                showCreateDialog = false
                container.requestSync(SyncTrigger.POST_WRITE)
                onOpenBoard(game.id)
            },
        )
    }
}

/** Display label for a game type, from the shared core choice model (AND-013). */
fun gameTypeLabel(type: GameType): String = GameTypeChoices.labelFor(type)

@Composable
private fun GameRow(
    title: String,
    trailing: String,
    highlight: Boolean,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (highlight) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            TextButton(onClick = onAction, enabled = actionEnabled) {
                Text(actionLabel)
            }
        }
    }
}
