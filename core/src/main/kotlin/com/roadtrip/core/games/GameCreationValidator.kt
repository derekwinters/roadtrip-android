package com.roadtrip.core.games

import com.roadtrip.core.api.GameMode
import com.roadtrip.core.api.GameType

data class GameCreation(
    val gameType: GameType,
    val mode: GameMode,
    val invitedProfileId: String? = null,
    val hangmanWord: String? = null,
    val ignoreDictionary: Boolean = false,
)

/**
 * Client-side live validation for game creation (ANDGAME-002), mirroring backend GAME-013
 * hangman word rules: letters A–Z plus spaces, per-word length ≤ 15, ≤ 3 words,
 * ≤ 30 letters total; the dictionary applies to single words unless `ignore_dictionary`.
 */
object GameCreationValidator {
    const val MAX_WORD_LENGTH = 15
    const val MAX_WORDS = 3
    const val MAX_TOTAL_LETTERS = 30

    const val ERR_INVITE_REQUIRED = "invited_profile_required"
    const val ERR_WORD_REQUIRED = "word_required"
    const val ERR_LETTERS_ONLY = "letters_only"
    const val ERR_WORD_TOO_LONG = "word_too_long"
    const val ERR_TOO_MANY_WORDS = "too_many_words"
    const val ERR_TOO_MANY_LETTERS = "too_many_letters"
    const val ERR_SINGLE_WORD_REQUIRED = "single_word_required"
    const val ERR_NOT_IN_DICTIONARY = "not_in_dictionary"

    private val LETTERS_AND_SPACES = Regex("^[A-Za-z ]+$")

    /** Returns error codes; empty list ⇒ valid. */
    fun validate(creation: GameCreation, dictionary: ((String) -> Boolean)? = null): List<String> {
        val errors = mutableListOf<String>()
        if (creation.mode == GameMode.CHALLENGE && creation.invitedProfileId.isNullOrBlank()) {
            errors += ERR_INVITE_REQUIRED
        }
        if (creation.gameType == GameType.HANGMAN) {
            errors += hangmanWordErrors(creation.hangmanWord.orEmpty(), creation.ignoreDictionary, dictionary)
        }
        return errors
    }

    /** Live per-keystroke validation of the hangman word/phrase field. */
    fun hangmanWordErrors(
        word: String,
        ignoreDictionary: Boolean,
        dictionary: ((String) -> Boolean)? = null,
    ): List<String> {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return listOf(ERR_WORD_REQUIRED)
        if (!LETTERS_AND_SPACES.matches(trimmed)) return listOf(ERR_LETTERS_ONLY)

        val words = trimmed.split(Regex("\\s+"))
        val errors = mutableListOf<String>()

        // Caps are ALWAYS enforced, dictionary on or off (GAME-013).
        if (words.any { it.length > MAX_WORD_LENGTH }) errors += ERR_WORD_TOO_LONG
        if (words.size > MAX_WORDS) errors += ERR_TOO_MANY_WORDS
        if (words.sumOf { it.length } > MAX_TOTAL_LETTERS) errors += ERR_TOO_MANY_LETTERS

        if (!ignoreDictionary && errors.isEmpty()) {
            if (words.size > 1) {
                errors += ERR_SINGLE_WORD_REQUIRED
            } else if (dictionary != null && !dictionary(words.single())) {
                errors += ERR_NOT_IN_DICTIONARY
            }
        }
        return errors
    }
}
