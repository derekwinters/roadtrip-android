package com.roadtrip.core.games

import com.roadtrip.core.api.EventDto
import com.roadtrip.core.api.GameType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Board snapshots reconstructed client-side by folding recorded move streams
 * (ANDGAME-006). Reconstruction is deterministic from move payloads alone; it does not
 * re-validate rules (the server already did).
 *
 * Assumed move payload shapes (backend 08-games.md):
 *  - chess:    {from, to, promotion?}          e.g. {"from":"e2","to":"e4"}
 *  - checkers: {from, to, captured?}           squares in algebraic form ("b6")
 *  - tictactoe:{cell}                          0..8, X = creator, alternating
 *  - ultimate: {board, cell}                   both 0..8
 *  - hangman:  {letter}                        guesser's letter; word from game options
 */
sealed class BoardState {
    /** squares: "e4" -> piece code like "wP", "bK". */
    data class ChessBoard(val squares: Map<String, String>) : BoardState()

    /** squares: "b6" -> "w"/"r" (men) or "W"/"R" (kings). */
    data class CheckersBoard(val squares: Map<String, String>) : BoardState()

    /**
     * cells: index 0..8, 'X'/'O'/null. winningLine: the three cell indices forming the win
     * (null while the game is undecided) so the view can highlight exactly those tiles
     * (ANDGAME-014).
     */
    data class TttBoard(
        val cells: List<Char?>,
        val winner: Char?,
        val winningLine: List<Int>? = null,
    ) : BoardState()

    /**
     * boards: 9 sub-boards of 9 cells; macro: sub-board winners; nextBoard: the dictated
     * sub-board for the next move, null = free choice (decided/full board). macroWinner /
     * macroWinningLine expose the overall win and its macro-cell indices; boardWinningLines
     * exposes the deciding three cells inside each captured sub-board (ANDGAME-014).
     */
    data class UltimateBoard(
        val boards: List<List<Char?>>,
        val macro: List<Char?>,
        val nextBoard: Int?,
        val macroWinner: Char? = null,
        val macroWinningLine: List<Int>? = null,
        val boardWinningLines: List<List<Int>?> = List(9) { null },
    ) : BoardState()

    /** masked: unguessed letters as '_', spaces kept visible (word boundaries). */
    data class HangmanBoard(
        val masked: String,
        val guessed: Set<Char>,
        val wrongCount: Int,
    ) : BoardState()
}

/** Pure fold of a move list into a board snapshot, per game type. */
class ReplayEngine(
    private val gameType: GameType,
    private val options: JsonObject? = null,
) {
    /** Board after applying the first [index] moves (0 = initial position). */
    fun stateAt(moves: List<JsonObject>, index: Int): BoardState {
        val slice = moves.take(index.coerceIn(0, moves.size))
        return when (gameType) {
            GameType.CHESS -> foldChess(slice)
            GameType.CHECKERS -> foldCheckers(slice)
            GameType.TICTACTOE -> foldTtt(slice)
            GameType.ULTIMATE -> foldUltimate(slice)
            GameType.HANGMAN -> foldHangman(slice)
        }
    }

    fun fold(moves: List<JsonObject>): BoardState = stateAt(moves, moves.size)

    // ---- chess -------------------------------------------------------------------------

    private fun foldChess(moves: List<JsonObject>): BoardState.ChessBoard {
        val squares = chessInitial()
        for (move in moves) {
            val from = move.str("from") ?: continue
            val to = move.str("to") ?: continue
            val piece = squares.remove(from) ?: continue
            val promotion = move.str("promotion")
            squares[to] = if (promotion != null) {
                piece.take(1) + promotion.uppercase()
            } else {
                piece
            }
            // Castling is encoded as the king moving two files: bring the rook along.
            if (piece.endsWith("K") && from[1] == to[1]) {
                val fileDelta = to[0] - from[0]
                val rank = from[1]
                if (fileDelta == 2) { // kingside
                    squares.remove("h$rank")?.let { squares["f$rank"] = it }
                } else if (fileDelta == -2) { // queenside
                    squares.remove("a$rank")?.let { squares["d$rank"] = it }
                }
            }
        }
        return BoardState.ChessBoard(squares)
    }

    private fun chessInitial(): MutableMap<String, String> {
        val squares = mutableMapOf<String, String>()
        val backRank = listOf("R", "N", "B", "Q", "K", "B", "N", "R")
        for ((index, file) in ('a'..'h').withIndex()) {
            squares["${file}1"] = "w${backRank[index]}"
            squares["${file}2"] = "wP"
            squares["${file}7"] = "bP"
            squares["${file}8"] = "b${backRank[index]}"
        }
        return squares
    }

    // ---- checkers ----------------------------------------------------------------------

    private fun foldCheckers(moves: List<JsonObject>): BoardState.CheckersBoard {
        val squares = checkersInitial()
        for (move in moves) {
            val from = move.str("from") ?: continue
            val to = move.str("to") ?: continue
            (move["captured"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?.forEach { squares.remove(it) }
            val piece = squares.remove(from) ?: continue
            val toRank = to[1]
            squares[to] = when {
                piece == "r" && toRank == '1' -> "R" // crowned on the far rank
                piece == "w" && toRank == '8' -> "W"
                else -> piece
            }
        }
        return BoardState.CheckersBoard(squares)
    }

    /** Dark playing squares: (file + rank) even, zero-based. w on ranks 1-3, r on 6-8. */
    private fun checkersInitial(): MutableMap<String, String> {
        val squares = mutableMapOf<String, String>()
        for (rank in 1..8) {
            for ((fileIndex, file) in ('a'..'h').withIndex()) {
                if ((fileIndex + rank - 1) % 2 != 0) continue
                when (rank) {
                    in 1..3 -> squares["$file$rank"] = "w"
                    in 6..8 -> squares["$file$rank"] = "r"
                }
            }
        }
        return squares
    }

    // ---- tic-tac-toe ---------------------------------------------------------------------

    private fun foldTtt(moves: List<JsonObject>): BoardState.TttBoard {
        val cells = arrayOfNulls<Char>(9)
        moves.forEachIndexed { moveIndex, move ->
            val cell = move.int("cell") ?: return@forEachIndexed
            cells[cell] = symbolFor(moveIndex)
        }
        val list = cells.toList()
        return BoardState.TttBoard(list, tttWinner(list), tttWinningLine(list))
    }

    // ---- ultimate tic-tac-toe ---------------------------------------------------------------

    private fun foldUltimate(moves: List<JsonObject>): BoardState.UltimateBoard {
        val boards = List(9) { arrayOfNulls<Char>(9) }
        var lastCell: Int? = null
        moves.forEachIndexed { moveIndex, move ->
            val board = move.int("board") ?: return@forEachIndexed
            val cell = move.int("cell") ?: return@forEachIndexed
            boards[board][cell] = symbolFor(moveIndex)
            lastCell = cell
        }
        val boardLists = boards.map { it.toList() }
        val macro = boardLists.map { tttWinner(it) }
        val boardWinningLines = boardLists.map { tttWinningLine(it) }
        // The last move's cell dictates the next sub-board — unless that board is decided
        // or full, in which case the next player has free choice.
        val nextBoard = lastCell?.takeIf { dictated ->
            macro[dictated] == null && boardLists[dictated].any { it == null }
        }
        val macroWinningLine = tttWinningLine(macro)
        val macroWinner = macroWinningLine?.let { macro[it[0]] }
        return BoardState.UltimateBoard(
            boards = boardLists,
            macro = macro,
            nextBoard = nextBoard,
            macroWinner = macroWinner,
            macroWinningLine = macroWinningLine,
            boardWinningLines = boardWinningLines,
        )
    }

    // ---- hangman -----------------------------------------------------------------------

    private fun foldHangman(moves: List<JsonObject>): BoardState.HangmanBoard {
        val word = options?.str("word").orEmpty().uppercase()
        val wordLetters = word.filter { it != ' ' }.toSet()
        val guessed = linkedSetOf<Char>()
        var wrong = 0
        for (move in moves) {
            val letter = move.str("letter")?.firstOrNull()?.uppercaseChar() ?: continue
            if (!guessed.add(letter)) continue // repeated guess changes nothing
            if (letter !in wordLetters) wrong++
        }
        val masked = word.map { c ->
            when {
                c == ' ' -> ' ' // word boundaries stay visible (GAME-014)
                c in guessed -> c
                else -> '_'
            }
        }.joinToString("")
        return BoardState.HangmanBoard(masked, guessed, wrong)
    }

    // ---- shared ------------------------------------------------------------------------

    /** X/creator moves first; symbols alternate deterministically by move index. */
    private fun symbolFor(moveIndex: Int): Char = if (moveIndex % 2 == 0) 'X' else 'O'

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
}

/** The eight winning triples of a 3x3 board. */
val TTT_LINES: List<List<Int>> = listOf(
    listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
    listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
    listOf(0, 4, 8), listOf(2, 4, 6),
)

/**
 * The three cell indices forming a completed line, or null if undecided (ANDGAME-014).
 * Pure/JVM-testable so the Compose views can highlight exactly the winning tiles.
 */
fun tttWinningLine(cells: List<Char?>): List<Int>? {
    for (line in TTT_LINES) {
        val first = cells[line[0]] ?: continue
        if (line.all { cells[it] == first }) return line
    }
    return null
}

/** Winner symbol for a 3x3 board, derived from [tttWinningLine]. */
fun tttWinner(cells: List<Char?>): Char? = tttWinningLine(cells)?.let { cells[it[0]] }

/**
 * Replay/spectate session with play/pause/step controls over a move stream (ANDGAME-006).
 * Spectate = the same session pinned to the live tail: appended moves auto-advance the
 * index while pinned; stepping back unpins (ANDGAME-007).
 */
class ReplaySession(
    private val engine: ReplayEngine,
    initialMoves: List<JsonObject> = emptyList(),
    pinnedToLive: Boolean = false,
) {
    private val moves = initialMoves.toMutableList()

    var index: Int = if (pinnedToLive) initialMoves.size else 0
        private set

    var playing: Boolean = false
        private set

    var pinnedToLive: Boolean = pinnedToLive
        private set

    val moveCount: Int get() = moves.size

    fun board(): BoardState = engine.stateAt(moves, index)

    fun play() {
        playing = true
    }

    fun pause() {
        playing = false
    }

    /** Advances one move while playing; used by the UI's playback timer. */
    fun tick() {
        if (!playing) return
        if (index < moves.size) index++
        if (index >= moves.size) playing = false
    }

    fun stepForward() {
        if (index < moves.size) index++
    }

    fun stepBack() {
        if (index > 0) index--
        pinnedToLive = false // scrubbing back leaves the live tail
    }

    fun seek(newIndex: Int) {
        index = newIndex.coerceIn(0, moves.size)
        if (index < moves.size) pinnedToLive = false
    }

    fun pinToLive() {
        pinnedToLive = true
        index = moves.size
    }

    /** Appends a live move; auto-advances when pinned to the live tail. */
    fun append(move: JsonObject) {
        moves += move
        if (pinnedToLive) index = moves.size
    }

    /** Convenience for spectate wiring: folds a `game.move` feed event into the session. */
    fun applyGameEvent(event: EventDto) {
        if (event.type != "game.move") return
        val move = event.payload["move"] as? JsonObject ?: return
        append(move)
    }
}
