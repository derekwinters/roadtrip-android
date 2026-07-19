package com.roadtrip.app.ui.games

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadtrip.app.di.AppContainer
import com.roadtrip.app.ui.common.OfflineBanner
import com.roadtrip.core.api.Game
import com.roadtrip.core.api.GameStatus
import com.roadtrip.core.api.GameType
import com.roadtrip.core.api.Profile
import com.roadtrip.core.common.SystemClock
import com.roadtrip.core.games.BoardState
import com.roadtrip.core.games.ConfirmMovePreference
import com.roadtrip.core.games.EndControl
import com.roadtrip.core.games.GameEndControls
import com.roadtrip.core.games.GameOfflineGate
import com.roadtrip.core.games.GameStreamFollower
import com.roadtrip.core.games.HangmanBoardStatus
import com.roadtrip.core.games.HangmanView
import com.roadtrip.core.games.LobbyReducer
import com.roadtrip.core.games.hangmanBoardStatus
import com.roadtrip.core.games.MoveSubmitter
import com.roadtrip.core.games.MoveOutcome
import com.roadtrip.core.games.PlayerLegend
import com.roadtrip.core.games.ReplayEngine
import com.roadtrip.core.games.ReplaySession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Live game board (ANDGAME-003/004/005). The board rebuilds from the recorded move stream
 * via the core ReplayEngine and follows the game event stream by long-poll so the
 * opponent's move appears without manual refresh; a non-participant gets the same screen
 * pinned to live as a spectator (ANDGAME-007). Moves submit through the core MoveSubmitter,
 * whose rejected outcomes restore the pre-move state with the server's reason.
 */
@Composable
fun BoardScreen(
    container: AppContainer,
    profile: Profile,
    gameId: String,
) {
    val online by container.onlineMonitor.online.collectAsState()
    val scope = rememberCoroutineScope()

    var game by remember { mutableStateOf<Game?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var moveError by remember { mutableStateOf<String?>(null) }
    var boardVersion by remember { mutableIntStateOf(0) }
    var selectedSquare by remember { mutableStateOf<String?>(null) }

    // Per-(profile, gameType) "confirm move" toggle (ANDGAME-022), settable on the board mid-game.
    // Resolved from the local ConfirmMoveStore with the ON default the first time this profile plays
    // this game type; re-initialised only when the game type is known (stable for a game's lifetime).
    var confirmMoves by remember(game?.gameType) {
        mutableStateOf(
            game?.let { ConfirmMovePreference.resolve(container.settings.get(profile.id, it.gameType)) }
                ?: ConfirmMovePreference.DEFAULT,
        )
    }
    // The move awaiting an explicit Confirm when the toggle is on (null == nothing staged).
    var stagedMove by remember { mutableStateOf<JsonElement?>(null) }

    // profileId -> display name for the player-identity legend (ANDGAME-020); same profiles-cache
    // read the lobby uses (ANDGAME-009), with a "Someone" fallback resolved inside PlayerLegend.
    val profileNames = remember {
        container.profilesCache.read()?.value.orEmpty().associate { it.id to it.name }
    }

    val sessionHolder = remember { mutableStateOf<ReplaySession?>(null) }
    val followerHolder = remember { mutableStateOf<GameStreamFollower?>(null) }

    // Load the game, its recorded moves, and start following the stream.
    LaunchedEffect(gameId) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
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
                Triple(g, moves.toList(), options) to after
            }
        }
        loaded.fold(
            onSuccess = { (data, cursor) ->
                val (g, moves, options) = data
                game = g
                // Cache for offline replays (ANDGAME-008).
                container.gameCache(gameId).write(g, SystemClock.now())
                container.gameMovesCache(gameId).write(moves, SystemClock.now())

                val session = ReplaySession(
                    engine = ReplayEngine(g.gameType, options),
                    initialMoves = moves,
                    pinnedToLive = true,
                )
                sessionHolder.value = session
                boardVersion++

                followerHolder.value = GameStreamFollower(
                    api = container.api,
                    gameId = gameId,
                    onEvent = { event ->
                        session.applyGameEvent(event)
                        game = game?.let { LobbyReducer.applyEvent(listOf(it), event).firstOrNull() }
                        boardVersion++
                    },
                    startAfter = cursor,
                )
            },
            onFailure = { loadError = it.message ?: "Could not load the game" },
        )
    }

    // Long-poll loop while the screen is open (ANDGAME-005); errors back off and retry.
    LaunchedEffect(gameId, followerHolder.value) {
        val follower = followerHolder.value ?: return@LaunchedEffect
        while (isActive) {
            try {
                val applied = follower.pollOnce()
                // Hangman renders from the server `view`, which the event fold cannot
                // reconstruct (redacted word); re-fetch the game so the mask, guessed set and
                // wrong count refresh when the opponent's move arrives (ANDGAME-016).
                if (applied > 0 && game?.gameType == GameType.HANGMAN) {
                    val refreshed = withContext(Dispatchers.IO) {
                        runCatching { container.api.getGame(gameId) }.getOrNull()
                    }
                    if (refreshed != null) {
                        game = refreshed
                        boardVersion++
                    }
                }
            } catch (e: Exception) {
                delay(3_000)
            }
        }
    }

    val session = sessionHolder.value
    val currentGame = game
    val gate = GameOfflineGate.evaluate(online)
    val isParticipant = currentGame != null &&
        (currentGame.createdBy == profile.id || currentGame.opponentId == profile.id)
    val myTurn = currentGame?.turn == profile.id
    val canMove = gate.enabled && isParticipant && myTurn &&
        currentGame?.status == GameStatus.ACTIVE
    // While a move is staged for confirmation the board is inert so a second move can't be started
    // before the first is confirmed or cancelled (ANDGAME-022).
    val canInteract = canMove && stagedMove == null

    // Hangman is driven by the backend's viewer-aware `view` (ANDGAME-016): the word is
    // redacted in the events feed for ongoing games, so it cannot be re-folded locally like
    // the other four games. The remaining games rebuild from the recorded move stream.
    val board: BoardState? = remember(boardVersion, session, currentGame) {
        if (currentGame?.gameType == GameType.HANGMAN) {
            HangmanView.toBoard(currentGame.view)
        } else {
            session?.board()
        }
    }

    fun submitMove(move: JsonElement) {
        val g = currentGame ?: return
        moveError = null
        scope.launch {
            val submitter = MoveSubmitter(container.api, g)
            val outcome = withContext(Dispatchers.IO) {
                try {
                    submitter.submit(move) { it }
                } catch (e: Exception) {
                    null
                }
            }
            when (outcome) {
                is MoveOutcome.Applied -> game = outcome.game
                is MoveOutcome.Rejected -> moveError = outcome.reason
                is MoveOutcome.NetworkFailure -> moveError = GameOfflineGate.OFFLINE_REASON
                null -> moveError = "Move failed — try again"
            }
        }
    }

    // ANDGAME-022: the confirm gate sits BEFORE the optimistic submit (mirrors the pure
    // MoveConfirmGate seam). Toggle off -> submit immediately as before; on -> stage the move and
    // wait for an explicit Confirm. Cancel discards the staged move and restores the pre-move board.
    fun requestMove(move: JsonElement) {
        if (confirmMoves) stagedMove = move else submitMove(move)
    }

    fun confirmStagedMove() {
        val move = stagedMove ?: return
        stagedMove = null
        submitMove(move)
    }

    fun cancelStagedMove() {
        stagedMove = null
        selectedSquare = null
    }

    fun tapSquare(square: String, occupied: Boolean) {
        val from = selectedSquare
        when {
            from == null -> if (occupied) selectedSquare = square
            from == square -> selectedSquare = null
            else -> {
                val move = buildJsonObject {
                    put("from", from)
                    put("to", square)
                }
                selectedSquare = null
                requestMove(move)
            }
        }
    }

    // The board is kept out of any vertical scroll region so it can bound itself to the
    // viewport (ANDGAME-012); only the status line and controls above/below it are fixed.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!gate.enabled) OfflineBanner(gate.reason ?: "Offline")

        when {
            loadError != null -> Text(loadError.orEmpty(), color = MaterialTheme.colorScheme.error)
            currentGame == null || board == null -> CircularProgressIndicator(
                modifier = Modifier.padding(32.dp),
            )
            else -> {
                Text(gameTypeLabel(currentGame.gameType), style = MaterialTheme.typography.titleLarge)
                Text(
                    statusLine(currentGame, isParticipant, myTurn, profile.id),
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Per-(profile, gameType) confirm-move toggle, on the board itself (ANDGAME-022).
                // Available to an active-game participant mid-game; flipping it saves the sticky
                // last-write-wins value for this profile+type and discards any pending stage when
                // switched off.
                if (isParticipant && currentGame.status == GameStatus.ACTIVE) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Confirm move before making it",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = confirmMoves,
                            onCheckedChange = { checked ->
                                confirmMoves = checked
                                container.settings.set(profile.id, currentGame.gameType, checked)
                                if (!checked) stagedMove = null
                            },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Board occupies the remaining height; grid boards bound to a square that
                // fits, hangman (a tall non-grid layout) scrolls within its own slot. Tic-tac-toe
                // and ultimate carry a player-identity legend below (phone) / beside (tablet) the
                // board (ANDGAME-020); other game types get an empty legend and lay out unchanged.
                val legend = PlayerLegend.forGame(currentGame, profile.id, profileNames)
                BoardWithLegend(
                    legend = legend,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    when (board) {
                        is BoardState.ChessBoard -> ChessBoardView(
                            board = board,
                            selectedSquare = selectedSquare,
                            enabled = canInteract,
                            onSquareTap = { square -> tapSquare(square, board.squares.containsKey(square)) },
                        )
                        is BoardState.CheckersBoard -> CheckersBoardView(
                            board = board,
                            selectedSquare = selectedSquare,
                            enabled = canInteract,
                            onSquareTap = { square -> tapSquare(square, board.squares.containsKey(square)) },
                        )
                        is BoardState.TttBoard -> TttBoardView(
                            board = board,
                            enabled = canInteract,
                            onCellTap = { cell -> requestMove(buildJsonObject { put("cell", cell) }) },
                        )
                        is BoardState.UltimateBoard -> UltimateBoardView(
                            board = board,
                            enabled = canInteract,
                            onCellTap = { subBoard, cell ->
                                requestMove(
                                    buildJsonObject {
                                        put("board", subBoard)
                                        put("cell", cell)
                                    },
                                )
                            },
                        )
                        is BoardState.HangmanBoard -> Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            HangmanBoardView(
                                board = board,
                                enabled = canInteract,
                                onGuess = { letter ->
                                    requestMove(buildJsonObject { put("letter", letter.toString()) })
                                },
                            )
                        }
                    }
                }

                moveError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                // Staged-move confirmation controls (ANDGAME-022): shown only while a move waits
                // for confirmation. Confirm submits it exactly once through the same optimistic
                // path; Cancel discards it and restores the pre-move board (clears the selection).
                if (stagedMove != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Confirm your move?", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { confirmStagedMove() }) { Text("Confirm") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { cancelStagedMove() }) { Text("Cancel") }
                    }
                }

                // Role- and type-aware end control (ANDGAME-018, backend GAME-015): a hangman
                // guesser must not be able to end the game — only the creator/setter can.
                val endControl = GameEndControls.gameEndControl(
                    gameType = currentGame.gameType,
                    isCreator = currentGame.createdBy == profile.id,
                    isParticipant = isParticipant,
                    isActive = currentGame.status == GameStatus.ACTIVE,
                )
                if (endControl != EndControl.NONE && gate.enabled) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                runCatching { container.api.resign(gameId) }
                            }.onSuccess { game = it }
                        }
                    }) {
                        Text(if (endControl == EndControl.END_GAME) "End game" else "Resign")
                    }
                }
            }
        }
    }
}

private fun statusLine(game: Game, isParticipant: Boolean, myTurn: Boolean, profileId: String): String {
    // Hangman has a single guesser and never alternates, so it must not fall through to the
    // generic "waiting for the other player" line (ANDGAME-017).
    if (game.gameType == GameType.HANGMAN) return hangmanStatusLine(game, profileId)
    return when {
        game.status == GameStatus.OPEN -> "Waiting for an opponent…"
        game.status == GameStatus.FINISHED -> "Finished"
        game.status == GameStatus.ABANDONED -> "Abandoned"
        !isParticipant -> "Spectating — live"
        myTurn -> "Your turn"
        else -> "Waiting for the other player…"
    }
}

private fun hangmanStatusLine(game: Game, profileId: String): String =
    when (hangmanBoardStatus(game, profileId)) {
        HangmanBoardStatus.OPEN -> "Waiting for an opponent…"
        HangmanBoardStatus.YOUR_TURN -> "Your turn — guess a letter"
        HangmanBoardStatus.WAITING_FOR_GUESSER -> "You set the word — waiting for the guess…"
        HangmanBoardStatus.YOU_WON -> "You won"
        HangmanBoardStatus.YOU_LOST -> "You lost"
        HangmanBoardStatus.SPECTATING -> "Spectating — live"
        HangmanBoardStatus.FINISHED -> "Finished"
        HangmanBoardStatus.ABANDONED -> "Abandoned"
    }
