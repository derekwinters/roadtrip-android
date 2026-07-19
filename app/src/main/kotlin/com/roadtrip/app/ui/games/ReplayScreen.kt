package com.roadtrip.app.ui.games

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadtrip.app.di.AppContainer
import com.roadtrip.core.api.Game
import com.roadtrip.core.common.SystemClock
import com.roadtrip.core.games.BoardState
import com.roadtrip.core.games.ReplayEngine
import com.roadtrip.core.games.ReplaySession
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Replay of a finished game (ANDGAME-006): play/pause/step/slider over the recorded move
 * stream, boards rebuilt locally by the core ReplayEngine. Loads from the server when
 * reachable and falls back to the local cache so cached replays work offline (ANDGAME-008).
 */
@Composable
fun ReplayScreen(
    container: AppContainer,
    gameId: String,
) {
    var game by remember { mutableStateOf<Game?>(null) }
    var session by remember { mutableStateOf<ReplaySession?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }

    LaunchedEffect(gameId) {
        val result = withContext(Dispatchers.IO) {
            // Server first, cache as the offline fallback.
            val fromServer = runCatching {
                val g = container.api.getGame(gameId)
                var after = 0L
                val moves = mutableListOf<JsonObject>()
                var options: JsonObject? = null
                while (true) {
                    val page = container.api.getGameEvents(gameId, after)
                    if (page.events.isEmpty()) break
                    for (event in page.events) {
                        when (event.type) {
                            "game.move" -> (event.payload["move"] as? JsonObject)?.let { moves.add(it) }
                            "game.created" -> options = event.payload["options"] as? JsonObject
                        }
                    }
                    after = page.nextAfter
                }
                container.gameCache(gameId).write(g, SystemClock.now())
                container.gameMovesCache(gameId).write(moves, SystemClock.now())
                Triple(g, moves.toList(), options)
            }.getOrNull()

            fromServer ?: run {
                val cachedGame = container.gameCache(gameId).read()?.value
                val cachedMoves = container.gameMovesCache(gameId).read()?.value
                if (cachedGame != null && cachedMoves != null) {
                    Triple(cachedGame, cachedMoves, null as JsonObject?)
                } else {
                    null
                }
            }
        }
        if (result == null) {
            loadError = "Replay unavailable — it was never cached and the server is unreachable."
        } else {
            val (g, moves, options) = result
            game = g
            session = ReplaySession(
                engine = ReplayEngine(g.gameType, options),
                initialMoves = moves,
                pinnedToLive = false,
            )
            version++
        }
    }

    // Playback timer: one move per tick while playing.
    LaunchedEffect(playing, session) {
        val s = session ?: return@LaunchedEffect
        while (playing) {
            delay(800)
            s.play()
            s.tick()
            version++
            if (!s.playing) {
                playing = false
            }
        }
    }

    val currentSession = session
    val currentGame = game
    val board: BoardState? = remember(version, currentSession) { currentSession?.board() }

    // Board stays out of the scroll region so it can bound itself to the viewport
    // (ANDGAME-012); the header and playback controls flank it.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            loadError != null -> Text(loadError.orEmpty(), color = MaterialTheme.colorScheme.error)
            currentSession == null || currentGame == null || board == null ->
                CircularProgressIndicator(modifier = Modifier.padding(32.dp))
            else -> {
                Text(
                    "${gameTypeLabel(currentGame.gameType)} replay",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Move ${currentSession.index} of ${currentSession.moveCount}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    ReplayBoard(board)
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        playing = false
                        currentSession.pause()
                        currentSession.stepBack()
                        version++
                    }) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Step back")
                    }
                    IconButton(onClick = { playing = !playing }) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                        )
                    }
                    IconButton(onClick = {
                        playing = false
                        currentSession.pause()
                        currentSession.stepForward()
                        version++
                    }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Step forward")
                    }
                }

                if (currentSession.moveCount > 0) {
                    Slider(
                        value = currentSession.index.toFloat(),
                        onValueChange = { value ->
                            playing = false
                            currentSession.pause()
                            currentSession.seek(value.roundToInt())
                            version++
                        },
                        valueRange = 0f..currentSession.moveCount.toFloat(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplayBoard(board: BoardState) {
    when (board) {
        is BoardState.ChessBoard -> ChessBoardView(board, selectedSquare = null, enabled = false) {}
        is BoardState.CheckersBoard -> CheckersBoardView(board, selectedSquare = null, enabled = false) {}
        is BoardState.TttBoard -> TttBoardView(board, enabled = false) {}
        is BoardState.UltimateBoard -> UltimateBoardView(board, enabled = false) { _, _ -> }
        is BoardState.HangmanBoard -> Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            HangmanBoardView(board, enabled = false) {}
        }
    }
}
