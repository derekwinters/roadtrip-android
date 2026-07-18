package com.roadtrip.core.games

import com.roadtrip.core.api.GameType
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class ReplayEngineTest {

    private fun chessMove(from: String, to: String, promotion: String? = null): JsonObject =
        buildJsonObject {
            put("from", from)
            put("to", to)
            if (promotion != null) put("promotion", promotion)
        }

    private fun cell(index: Int): JsonObject = buildJsonObject { put("cell", index) }

    private fun ultimate(board: Int, cellIndex: Int): JsonObject = buildJsonObject {
        put("board", board)
        put("cell", cellIndex)
    }

    @Test
    fun `chess replay reconstructs board states deterministically from move payloads ANDGAME-006`() {
        val engine = ReplayEngine(GameType.CHESS)
        val moves = listOf(chessMove("e2", "e4"), chessMove("e7", "e5"), chessMove("g1", "f3"))

        val start = assertIs<BoardState.ChessBoard>(engine.stateAt(moves, 0))
        assertEquals("wP", start.squares["e2"])
        assertEquals("bK", start.squares["e8"])

        val afterOne = assertIs<BoardState.ChessBoard>(engine.stateAt(moves, 1))
        assertEquals("wP", afterOne.squares["e4"])
        assertNull(afterOne.squares["e2"])

        val afterThree = assertIs<BoardState.ChessBoard>(engine.stateAt(moves, 3))
        assertEquals("wN", afterThree.squares["f3"])
        assertNull(afterThree.squares["g1"])

        // Replay determinism: folding twice yields identical states.
        assertEquals(engine.fold(moves), ReplayEngine(GameType.CHESS).fold(moves))
    }

    @Test
    fun `chess replay applies promotions and castling rook movement ANDGAME-006`() {
        val engine = ReplayEngine(GameType.CHESS)

        val promoted = assertIs<BoardState.ChessBoard>(
            engine.fold(listOf(chessMove("e2", "e8", promotion = "q"))),
        )
        assertEquals("wQ", promoted.squares["e8"])

        val castled = assertIs<BoardState.ChessBoard>(engine.fold(listOf(chessMove("e1", "g1"))))
        assertEquals("wK", castled.squares["g1"])
        assertEquals("wR", castled.squares["f1"]) // rook follows the king
        assertNull(castled.squares["h1"])
    }

    @Test
    fun `checkers replay applies jumps captures and crowning ANDGAME-006`() {
        val engine = ReplayEngine(GameType.CHECKERS)
        // The engine reconstructs deterministically from payloads; it does not referee
        // (the server already validated every move).
        val moves = listOf(
            buildJsonObject { put("from", "c3"); put("to", "b4") },
            buildJsonObject { put("from", "b6"); put("to", "a5") },
            buildJsonObject {
                put("from", "a5")
                put("to", "c3")
                putJsonArray("captured") { add("b4") }
            },
            buildJsonObject {
                put("from", "c3")
                put("to", "e1")
                putJsonArray("captured") { add("d2") }
            },
        )

        val initial = assertIs<BoardState.CheckersBoard>(engine.stateAt(moves, 0))
        assertEquals("w", initial.squares["a3"])
        assertEquals("r", initial.squares["b6"])

        val afterJump = assertIs<BoardState.CheckersBoard>(engine.stateAt(moves, 3))
        assertEquals("r", afterJump.squares["c3"])
        assertNull(afterJump.squares["b4"]) // captured piece removed

        val crowned = assertIs<BoardState.CheckersBoard>(engine.stateAt(moves, 4))
        assertEquals("R", crowned.squares["e1"]) // red man crowned on the back rank
        assertNull(crowned.squares["d2"]) // second capture removed
    }

    @Test
    fun `tictactoe replay alternates X and O and detects the winner ANDGAME-006`() {
        val engine = ReplayEngine(GameType.TICTACTOE)
        val moves = listOf(cell(4), cell(0), cell(2), cell(1), cell(6))

        val midGame = assertIs<BoardState.TttBoard>(engine.stateAt(moves, 3))
        assertEquals('X', midGame.cells[4])
        assertEquals('O', midGame.cells[0])
        assertEquals('X', midGame.cells[2])
        assertNull(midGame.winner)

        val finished = assertIs<BoardState.TttBoard>(engine.fold(moves))
        assertEquals('X', finished.winner) // diagonal 2-4-6
    }

    @Test
    fun `ultimate replay tracks the dictated sub-board including the free-choice case ANDGAME-006`() {
        val engine = ReplayEngine(GameType.ULTIMATE)
        val moves = listOf(
            ultimate(4, 0), // X → opponent must play board 0
            ultimate(0, 4), // O → back to board 4
            ultimate(4, 1), // X
            ultimate(1, 4), // O
            ultimate(4, 2), // X wins sub-board 4 (cells 0,1,2) → next dictated board is 2
            ultimate(2, 4), // O → dictated board 4 is DECIDED → free choice
        )

        val afterFive = assertIs<BoardState.UltimateBoard>(engine.stateAt(moves, 5))
        assertEquals('X', afterFive.macro[4])
        assertEquals(2, afterFive.nextBoard)

        val afterSix = assertIs<BoardState.UltimateBoard>(engine.stateAt(moves, 6))
        assertNull(afterSix.nextBoard) // decided board ⇒ play anywhere
        assertEquals('O', afterSix.boards[2][4])
    }

    @Test
    fun `hangman replay masks unguessed letters but keeps word boundaries visible ANDGAME-006`() {
        val engine = ReplayEngine(
            GameType.HANGMAN,
            options = buildJsonObject { put("word", "ROAD TRIP") },
        )
        val moves = listOf(
            buildJsonObject { put("letter", "R") },
            buildJsonObject { put("letter", "O") },
            buildJsonObject { put("letter", "X") },
        )

        val fresh = assertIs<BoardState.HangmanBoard>(engine.stateAt(moves, 0))
        assertEquals("____ ____", fresh.masked) // space visible from move zero

        val after = assertIs<BoardState.HangmanBoard>(engine.fold(moves))
        assertEquals("RO__ _R__", after.masked)
        assertEquals(1, after.wrongCount) // X was wrong
        assertEquals(setOf('R', 'O', 'X'), after.guessed)
    }

    @Test
    fun `play pause and step controls rebuild identical board states ANDGAME-006`() {
        val engine = ReplayEngine(GameType.TICTACTOE)
        val moves = listOf(cell(4), cell(0), cell(2), cell(1), cell(6))
        val session = ReplaySession(engine, moves)

        assertEquals(0, session.index)
        session.stepForward()
        session.stepForward()
        assertEquals(engine.stateAt(moves, 2), session.board())

        session.stepBack()
        assertEquals(engine.stateAt(moves, 1), session.board())

        session.play()
        assertTrue(session.playing)
        session.tick()
        session.tick()
        assertEquals(engine.stateAt(moves, 3), session.board())
        session.pause()
        assertFalse(session.playing)

        session.seek(5)
        assertEquals(engine.fold(moves), session.board())
    }

    @Test
    fun `spectate is a replay pinned to the live tail ANDGAME-007`() {
        val engine = ReplayEngine(GameType.TICTACTOE)
        val session = ReplaySession(engine, listOf(cell(4), cell(0)), pinnedToLive = true)
        assertEquals(2, session.index) // opened at the live tail

        // Live moves arrive from the stream follower: the board follows automatically.
        session.applyGameEvent(
            TestData.gameMoveEvent(30, "g-1", 3, cell(2), TestData.kid.id),
        )
        assertEquals(3, session.index)
        assertEquals('X', assertIs<BoardState.TttBoard>(session.board()).cells[2])

        // Scrubbing back unpins: newly arriving moves no longer yank the view.
        session.stepBack()
        assertFalse(session.pinnedToLive)
        session.append(cell(1))
        assertEquals(2, session.index)

        // Re-pinning jumps back to the live tail.
        session.pinToLive()
        assertTrue(session.pinnedToLive)
        assertEquals(4, session.index)
    }

    @Test
    fun `any profile can follow a live game the stream is viewer-independent ANDGAME-007`() {
        // Spectating uses the same event fold as replay: two independent sessions fed the
        // same stream render identical boards (no per-viewer state).
        val engine = ReplayEngine(GameType.TICTACTOE)
        val a = ReplaySession(engine, emptyList(), pinnedToLive = true)
        val b = ReplaySession(engine, emptyList(), pinnedToLive = true)
        listOf(cell(4), cell(0), cell(2)).forEach { move ->
            a.append(move)
            b.append(move)
        }
        assertEquals(a.board(), b.board())
    }
}
