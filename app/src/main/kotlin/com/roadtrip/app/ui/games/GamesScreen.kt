package com.roadtrip.app.ui.games

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.roadtrip.core.api.GameStatus
import com.roadtrip.core.api.GameType
import com.roadtrip.core.api.Profile
import com.roadtrip.core.games.GameOfflineGate
import com.roadtrip.core.games.LobbyReducer
import com.roadtrip.core.sync.SyncTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Games lobby (ANDGAME-001): open games to join, my games with turn indicator, incoming
 * challenges, plus spectate and replay entries. Actions are online-only with an
 * explanatory banner offline (ANDGAME-008); cached replays keep working.
 */
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

    val games: List<Game> = remember(tick) { container.gamesCache.read()?.value.orEmpty() }
    val lobby = remember(games) { LobbyReducer.reduce(games, profile.id) }
    val spectatable = remember(games) {
        games.filter {
            it.status == GameStatus.ACTIVE && it.createdBy != profile.id && it.opponentId != profile.id
        }
    }
    val finished = remember(games) { games.filter { it.status == GameStatus.FINISHED } }

    val gate = GameOfflineGate.evaluate(online)

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
            actionError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            ) {
                // License plate bingo: the whole family fills one card together; spots
                // queue offline, so the entry never needs the online gate (ANDBNG-001).
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
                if (lobby.myGames.isEmpty()) {
                    item { Text("No active games", style = MaterialTheme.typography.bodyMedium) }
                }
                items(lobby.myGames, key = { "my-${it.game.id}" }) { myGame ->
                    GameRow(
                        game = myGame.game,
                        trailing = if (myGame.myTurn) "Your turn" else "Waiting…",
                        highlight = myGame.myTurn,
                        actionLabel = "Open",
                        actionEnabled = true,
                        onAction = { onOpenBoard(myGame.game.id) },
                    )
                }

                item { SectionHeader("Challenges for you") }
                if (lobby.incomingChallenges.isEmpty()) {
                    item { Text("No challenges", style = MaterialTheme.typography.bodyMedium) }
                }
                items(lobby.incomingChallenges, key = { "ch-${it.id}" }) { game ->
                    GameRow(
                        game = game,
                        trailing = "Challenge",
                        highlight = true,
                        actionLabel = "Accept",
                        actionEnabled = gate.enabled,
                        onAction = { join(game) },
                    )
                }

                item { SectionHeader("Open games") }
                if (lobby.openGames.isEmpty()) {
                    item { Text("No open games", style = MaterialTheme.typography.bodyMedium) }
                }
                items(lobby.openGames, key = { "open-${it.id}" }) { game ->
                    GameRow(
                        game = game,
                        trailing = "Open to anyone",
                        highlight = false,
                        actionLabel = "Join",
                        actionEnabled = gate.enabled,
                        onAction = { join(game) },
                    )
                }

                item { SectionHeader("Watch live") }
                if (spectatable.isEmpty()) {
                    item { Text("Nothing to spectate", style = MaterialTheme.typography.bodyMedium) }
                }
                items(spectatable, key = { "spec-${it.id}" }) { game ->
                    GameRow(
                        game = game,
                        trailing = "Live",
                        highlight = false,
                        actionLabel = "Spectate",
                        actionEnabled = gate.enabled,
                        onAction = { onOpenBoard(game.id) },
                    )
                }

                item { SectionHeader("Finished games") }
                if (finished.isEmpty()) {
                    item { Text("No finished games yet", style = MaterialTheme.typography.bodyMedium) }
                }
                items(finished, key = { "fin-${it.id}" }) { game ->
                    GameRow(
                        game = game,
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

fun gameTypeLabel(type: GameType): String = when (type) {
    GameType.CHESS -> "Chess"
    GameType.CHECKERS -> "Checkers"
    GameType.TICTACTOE -> "Tic-tac-toe"
    GameType.ULTIMATE -> "Ultimate TTT"
    GameType.HANGMAN -> "Hangman"
}

@Composable
private fun GameRow(
    game: Game,
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
                Text(gameTypeLabel(game.gameType), style = MaterialTheme.typography.titleSmall)
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
