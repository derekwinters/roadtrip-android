package com.roadtrip.core.games

import com.roadtrip.core.api.GameMode
import com.roadtrip.core.api.GameType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameCreationValidatorTest {
    private val dictionary: (String) -> Boolean = { it.lowercase() in setOf("canyon", "mountain", "prairie") }

    private fun hangman(
        word: String,
        ignoreDictionary: Boolean = false,
        mode: GameMode = GameMode.OPEN,
        invited: String? = null,
    ) = GameCreation(GameType.HANGMAN, mode, invited, word, ignoreDictionary)

    @Test
    fun `challenge mode requires an invited profile ANDGAME-002`() {
        val creation = GameCreation(GameType.CHESS, GameMode.CHALLENGE)
        assertEquals(listOf(GameCreationValidator.ERR_INVITE_REQUIRED), GameCreationValidator.validate(creation))

        val ok = GameCreation(GameType.CHESS, GameMode.CHALLENGE, invitedProfileId = "p-kid")
        assertTrue(GameCreationValidator.validate(ok).isEmpty())
    }

    @Test
    fun `hangman dictionary mode accepts a single known word ANDGAME-002`() {
        assertTrue(GameCreationValidator.validate(hangman("canyon"), dictionary).isEmpty())

        assertEquals(
            listOf(GameCreationValidator.ERR_NOT_IN_DICTIONARY),
            GameCreationValidator.validate(hangman("xylophone"), dictionary),
        )
        assertEquals(
            listOf(GameCreationValidator.ERR_SINGLE_WORD_REQUIRED),
            GameCreationValidator.validate(hangman("grand canyon"), dictionary),
        )
    }

    @Test
    fun `ignore_dictionary allows phrases but keeps the caps mirroring GAME-013 ANDGAME-002`() {
        // Wheel-of-fortune style phrase: fine with the dictionary off.
        assertTrue(
            GameCreationValidator.validate(hangman("grand canyon", ignoreDictionary = true), dictionary).isEmpty(),
        )

        // Caps are ALWAYS enforced, dictionary on or off.
        assertEquals(
            listOf(GameCreationValidator.ERR_WORD_TOO_LONG),
            GameCreationValidator.hangmanWordErrors("floccinaucinihil", ignoreDictionary = true), // 16 letters
        )
        assertEquals(
            listOf(GameCreationValidator.ERR_TOO_MANY_WORDS),
            GameCreationValidator.hangmanWordErrors("one two three four", ignoreDictionary = true),
        )
        assertEquals(
            listOf(GameCreationValidator.ERR_TOO_MANY_LETTERS),
            GameCreationValidator.hangmanWordErrors("abcdefghijk lmnopqrstuv wxyzabcde", ignoreDictionary = true),
        )
    }

    @Test
    fun `hangman words are letters A to Z plus spaces only ANDGAME-002`() {
        assertEquals(
            listOf(GameCreationValidator.ERR_LETTERS_ONLY),
            GameCreationValidator.hangmanWordErrors("route 66", ignoreDictionary = true),
        )
        assertEquals(
            listOf(GameCreationValidator.ERR_LETTERS_ONLY),
            GameCreationValidator.hangmanWordErrors("café", ignoreDictionary = true),
        )
        assertEquals(
            listOf(GameCreationValidator.ERR_WORD_REQUIRED),
            GameCreationValidator.hangmanWordErrors("   ", ignoreDictionary = true),
        )
        assertTrue(GameCreationValidator.hangmanWordErrors("Mesa Verde", ignoreDictionary = true).isEmpty())
    }
}
