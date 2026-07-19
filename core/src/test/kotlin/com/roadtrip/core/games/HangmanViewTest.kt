package com.roadtrip.core.games

import com.roadtrip.core.api.GameStatus
import com.roadtrip.core.api.GameType
import com.roadtrip.core.testing.TestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class HangmanViewTest {

    private fun view(
        display: String,
        guessed: List<String> = emptyList(),
        wrong: Int = 0,
        maxWrong: Int = 6,
    ): JsonObject = buildJsonObject {
        put("display", display)
        putJsonArray("guessed") { guessed.forEach { add(it) } }
        put("wrong", wrong)
        put("max_wrong", maxWrong)
    }

    @Test
    fun `builds the hangman board from the server viewer-aware view ANDGAME-016`() {
        // The word 'elephant' with E and L revealed; Z was a wrong guess. The word itself is
        // never present in the view for the guesser — we trust the server's masked display
        // and wrong count rather than folding the (redacted) word locally.
        val board = HangmanView.toBoard(view("EL______", listOf("E", "L", "Z"), wrong = 1))!!
        assertEquals("EL______", board.masked)
        assertEquals(setOf('E', 'L', 'Z'), board.guessed)
        assertEquals(1, board.wrongCount)
        assertEquals(6, board.maxWrong)
    }

    @Test
    fun `hangman display renders letters blanks and word boundaries verbatim ANDGAME-016`() {
        // Regression for issue #80: 'elephant ' (trailing space) is normalized server-side,
        // so the display is one 8-blank run — not two mismatched runs (no phantom boundary).
        val single = HangmanView.toBoard(view("________"))!!
        assertEquals(8, single.masked.count { it == '_' })
        assertFalse(single.masked.contains(' '))

        // A genuine multi-word phrase keeps a single visible boundary space.
        val phrase = HangmanView.toBoard(view("RO__ ____", listOf("R", "O")))!!
        assertEquals("RO__ ____", phrase.masked)
        assertEquals(1, phrase.masked.count { it == ' ' })
    }

    @Test
    fun `toBoard tolerates a missing or non-object view ANDGAME-016`() {
        assertNull(HangmanView.toBoard(null))
        assertNull(HangmanView.toBoard(JsonNull))
        assertNull(HangmanView.toBoard(buildJsonObject { })) // no display field
    }

    @Test
    fun `hangman status reflects guesser turn setter waiting and win-loss never generic ANDGAME-017`() {
        val setter = TestData.parent.id
        val guesser = TestData.kid.id
        val bystander = TestData.otherKid.id

        fun hangman(
            status: GameStatus,
            turn: String? = null,
            winnerId: String? = null,
        ) = TestData.game(
            id = "h", type = GameType.HANGMAN, status = status,
            createdBy = setter,
            opponentId = if (status == GameStatus.OPEN) null else guesser,
            turn = turn,
        ).copy(winnerId = winnerId)

        val active = hangman(GameStatus.ACTIVE, turn = guesser)
        // The guesser is always the one to move.
        assertEquals(HangmanBoardStatus.YOUR_TURN, hangmanBoardStatus(active, guesser))
        // The setter never guesses — they wait, never "your turn".
        assertEquals(HangmanBoardStatus.WAITING_FOR_GUESSER, hangmanBoardStatus(active, setter))
        // A non-participant spectates.
        assertEquals(HangmanBoardStatus.SPECTATING, hangmanBoardStatus(active, bystander))

        // End states are viewer-relative win/loss, never a generic waiting line.
        val guesserWon = hangman(GameStatus.FINISHED, winnerId = guesser)
        assertEquals(HangmanBoardStatus.YOU_WON, hangmanBoardStatus(guesserWon, guesser))
        assertEquals(HangmanBoardStatus.YOU_LOST, hangmanBoardStatus(guesserWon, setter))
        assertEquals(HangmanBoardStatus.FINISHED, hangmanBoardStatus(guesserWon, bystander))

        val setterWon = hangman(GameStatus.FINISHED, winnerId = setter)
        assertEquals(HangmanBoardStatus.YOU_LOST, hangmanBoardStatus(setterWon, guesser))
        assertEquals(HangmanBoardStatus.YOU_WON, hangmanBoardStatus(setterWon, setter))

        assertEquals(
            HangmanBoardStatus.OPEN,
            hangmanBoardStatus(hangman(GameStatus.OPEN), setter),
        )
    }
}
