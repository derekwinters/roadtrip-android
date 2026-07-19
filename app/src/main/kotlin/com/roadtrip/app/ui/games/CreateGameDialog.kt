package com.roadtrip.app.ui.games

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadtrip.app.di.AppContainer
import com.roadtrip.app.ui.common.Avatar
import com.roadtrip.core.api.CreateGameRequest
import com.roadtrip.core.api.Game
import com.roadtrip.core.api.GameMode
import com.roadtrip.core.api.GameType
import com.roadtrip.core.api.Profile
import com.roadtrip.core.games.GameCreation
import com.roadtrip.core.games.GameCreationValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Game creation (ANDGAME-002): mode open/challenge with an invited-profile picker, and
 * hangman word entry with the dictionary toggle and live cap validation via the core
 * GameCreationValidator (mirrors backend GAME-013 rules).
 */
@Composable
fun CreateGameDialog(
    container: AppContainer,
    profile: Profile,
    onDismiss: () -> Unit,
    onCreated: (Game) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var gameType by remember { mutableStateOf(GameType.TICTACTOE) }
    var mode by remember { mutableStateOf(GameMode.OPEN) }
    var invitedProfileId by remember { mutableStateOf<String?>(null) }
    var hangmanWord by remember { mutableStateOf("") }
    var ignoreDictionary by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }

    val otherProfiles = remember {
        container.profilesCache.read()?.value.orEmpty().filter { it.id != profile.id }
    }

    val creation = GameCreation(
        gameType = gameType,
        mode = mode,
        invitedProfileId = invitedProfileId,
        hangmanWord = hangmanWord,
        ignoreDictionary = ignoreDictionary,
    )
    val errors = GameCreationValidator.validate(creation)

    fun create() {
        submitting = true
        serverError = null
        val options = if (gameType == GameType.HANGMAN) {
            buildJsonObject {
                put("word", hangmanWord.trim())
                put("ignore_dictionary", ignoreDictionary)
            }
        } else {
            null
        }
        val request = CreateGameRequest(
            gameType = gameType,
            mode = mode,
            invitedProfileId = if (mode == GameMode.CHALLENGE) invitedProfileId else null,
            options = options,
        )
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { container.api.createGame(request) }
            }
            submitting = false
            result.fold(
                onSuccess = { onCreated(it) },
                onFailure = { serverError = it.message ?: "Could not create the game" },
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New game") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Single-choice selectors use the unmistakable filled + check SegmentedButton
                // treatment (AND-013) instead of the low-contrast FilterChip selected state.
                Text("Game", style = MaterialTheme.typography.labelLarge)
                val gameTypes = listOf(
                    GameType.CHESS,
                    GameType.CHECKERS,
                    GameType.TICTACTOE,
                    GameType.ULTIMATE,
                    GameType.HANGMAN,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    gameTypes.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = gameType == type,
                            onClick = { gameType = type },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = gameTypes.size),
                            label = { Text(gameTypeLabel(type)) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Mode", style = MaterialTheme.typography.labelLarge)
                val modes = listOf(
                    GameMode.OPEN to "Open to anyone",
                    GameMode.CHALLENGE to "Challenge",
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = mode == value,
                            onClick = { mode = value },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            label = { Text(label) },
                        )
                    }
                }

                if (mode == GameMode.CHALLENGE) {
                    Spacer(Modifier.height(8.dp))
                    Text("Challenge who?", style = MaterialTheme.typography.labelLarge)
                    for (other in otherProfiles) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RadioButton(
                                selected = invitedProfileId == other.id,
                                onClick = { invitedProfileId = other.id },
                            )
                            Avatar(other.avatar, other.name, size = 28)
                            Spacer(Modifier.width(8.dp))
                            Text(other.name)
                        }
                    }
                    if (otherProfiles.isEmpty()) {
                        Text("No other profiles known yet — sync first.")
                    }
                }

                if (gameType == GameType.HANGMAN) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = hangmanWord,
                        onValueChange = { hangmanWord = it },
                        label = { Text("Word or phrase") },
                        supportingText = {
                            val wordErrors = GameCreationValidator.hangmanWordErrors(
                                hangmanWord,
                                ignoreDictionary,
                            )
                            if (wordErrors.isEmpty()) {
                                Text("Looks good")
                            } else {
                                Text(wordErrors.joinToString { errorText(it) })
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = ignoreDictionary,
                            onCheckedChange = { ignoreDictionary = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Allow any word or phrase (skip dictionary)")
                    }
                }

                serverError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { create() },
                enabled = errors.isEmpty() && !submitting,
            ) {
                Text(if (submitting) "Creating…" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun errorText(code: String): String = when (code) {
    GameCreationValidator.ERR_INVITE_REQUIRED -> "Pick someone to challenge"
    GameCreationValidator.ERR_WORD_REQUIRED -> "Enter a word"
    GameCreationValidator.ERR_LETTERS_ONLY -> "Letters and spaces only"
    GameCreationValidator.ERR_WORD_TOO_LONG -> "Words must be ≤ ${GameCreationValidator.MAX_WORD_LENGTH} letters"
    GameCreationValidator.ERR_TOO_MANY_WORDS -> "At most ${GameCreationValidator.MAX_WORDS} words"
    GameCreationValidator.ERR_TOO_MANY_LETTERS -> "At most ${GameCreationValidator.MAX_TOTAL_LETTERS} letters total"
    GameCreationValidator.ERR_SINGLE_WORD_REQUIRED -> "Phrases need the dictionary toggle off"
    GameCreationValidator.ERR_NOT_IN_DICTIONARY -> "Not in the dictionary"
    else -> code
}
