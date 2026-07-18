package com.roadtrip.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadtrip.core.games.BoardState

/**
 * Board renderers for the five games (ANDGAME-004), driven purely by core [BoardState]
 * snapshots. Input is reported as taps; all rule arbitration stays on the server.
 */

private val LIGHT_SQUARE = Color(0xFFF0D9B5)
private val DARK_SQUARE = Color(0xFFB58863)

private val CHESS_GLYPHS = mapOf(
    "wK" to "♔", "wQ" to "♕", "wR" to "♖", "wB" to "♗", "wN" to "♘", "wP" to "♙",
    "bK" to "♚", "bQ" to "♛", "bR" to "♜", "bB" to "♝", "bN" to "♞", "bP" to "♟",
)

@Composable
fun ChessBoardView(
    board: BoardState.ChessBoard,
    selectedSquare: String?,
    enabled: Boolean,
    onSquareTap: (String) -> Unit,
) {
    EightByEight(
        pieceAt = { square -> CHESS_GLYPHS[board.squares[square]] },
        pieceColorAt = { null },
        selectedSquare = selectedSquare,
        enabled = enabled,
        onSquareTap = onSquareTap,
    )
}

@Composable
fun CheckersBoardView(
    board: BoardState.CheckersBoard,
    selectedSquare: String?,
    enabled: Boolean,
    onSquareTap: (String) -> Unit,
) {
    EightByEight(
        pieceAt = { square ->
            when (board.squares[square]) {
                "w" -> "●"
                "r" -> "●"
                "W" -> "♛"
                "R" -> "♛"
                else -> null
            }
        },
        pieceColorAt = { square ->
            when (board.squares[square]) {
                "w", "W" -> Color.White
                "r", "R" -> Color(0xFFC62828)
                else -> null
            }
        },
        selectedSquare = selectedSquare,
        enabled = enabled,
        onSquareTap = onSquareTap,
    )
}

@Composable
private fun EightByEight(
    pieceAt: (String) -> String?,
    pieceColorAt: (String) -> Color?,
    selectedSquare: String?,
    enabled: Boolean,
    onSquareTap: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        for (rank in 8 downTo 1) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                for ((fileIndex, file) in ('a'..'h').withIndex()) {
                    val square = "$file$rank"
                    // a1 dark, h1 light — matches the core checkers piece layout.
                    val darkSquare = (fileIndex + rank) % 2 == 1
                    val background = if (darkSquare) DARK_SQUARE else LIGHT_SQUARE
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(background)
                            .then(
                                if (square == selectedSquare) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    Modifier
                                },
                            )
                            .clickable(enabled = enabled) { onSquareTap(square) },
                        contentAlignment = Alignment.Center,
                    ) {
                        val glyph = pieceAt(square)
                        if (glyph != null) {
                            val tint = pieceColorAt(square)
                            if (tint != null) {
                                Text(glyph, fontSize = 24.sp, color = tint)
                            } else {
                                Text(glyph, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TttBoardView(
    board: BoardState.TttBoard,
    enabled: Boolean,
    onCellTap: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        for (row in 0..2) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0..2) {
                    val cell = row * 3 + col
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(2.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = enabled && board.cells[cell] == null) { onCellTap(cell) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(board.cells[cell]?.toString() ?: "", fontSize = 40.sp)
                    }
                }
            }
        }
    }
}

/** Ultimate tic-tac-toe with the dictated sub-board highlighted (ANDGAME-004). */
@Composable
fun UltimateBoardView(
    board: BoardState.UltimateBoard,
    enabled: Boolean,
    onCellTap: (subBoard: Int, cell: Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        for (macroRow in 0..2) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                for (macroCol in 0..2) {
                    val subIndex = macroRow * 3 + macroCol
                    val dictated = board.nextBoard == subIndex
                    val playable = enabled && (board.nextBoard == null || dictated) &&
                        board.macro[subIndex] == null
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(2.dp)
                            .border(
                                width = if (dictated) 3.dp else 1.dp,
                                color = if (dictated) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            ),
                    ) {
                        SubBoard(
                            cells = board.boards[subIndex],
                            winner = board.macro[subIndex],
                            enabled = playable,
                            onCellTap = { cell -> onCellTap(subIndex, cell) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubBoard(
    cells: List<Char?>,
    winner: Char?,
    enabled: Boolean,
    onCellTap: (Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0..2) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0..2) {
                        val cell = row * 3 + col
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(1.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = enabled && cells[cell] == null) { onCellTap(cell) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(cells[cell]?.toString() ?: "", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (winner != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(winner.toString(), fontSize = 28.sp)
            }
        }
    }
}

private val GALLOWS_STAGES = listOf(
    "\n\n\n\n ———",
    "  |\n  |\n  |\n  |\n ———",
    "  ———\n  |\n  |\n  |\n ———",
    "  ———\n  |  O\n  |\n  |\n ———",
    "  ———\n  |  O\n  | /|\\\n  |\n ———",
    "  ———\n  |  O\n  | /|\\\n  | /\n ———",
    "  ———\n  |  O\n  | /|\\\n  | / \\\n ———",
)

/** Masked phrase with visible word boundaries, gallows progress, letter keyboard. */
@Composable
fun HangmanBoardView(
    board: BoardState.HangmanBoard,
    enabled: Boolean,
    onGuess: (Char) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            GALLOWS_STAGES[board.wrongCount.coerceIn(0, GALLOWS_STAGES.size - 1)],
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            // Underscores per hidden letter; the double space keeps word boundaries visible.
            board.masked.map { if (it == ' ') " " else it.toString() }.joinToString(" "),
            fontFamily = FontFamily.Monospace,
            fontSize = 24.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Wrong guesses: ${board.wrongCount}/6",
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(8.dp))
        for (rowLetters in ('A'..'Z').chunked(7)) {
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                for (letter in rowLetters) {
                    OutlinedButton(
                        onClick = { onGuess(letter) },
                        enabled = enabled && letter !in board.guessed,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        modifier = Modifier.weight(1f).padding(1.dp),
                    ) {
                        Text(letter.toString())
                    }
                }
                // Pad the last row so buttons keep equal width.
                repeat(7 - rowLetters.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
