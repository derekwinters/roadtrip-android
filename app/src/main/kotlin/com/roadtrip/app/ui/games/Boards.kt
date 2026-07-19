package com.roadtrip.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadtrip.core.games.BoardMetrics
import com.roadtrip.core.games.BoardPieces
import com.roadtrip.core.games.BoardState
import com.roadtrip.core.games.LastMoveHighlight
import com.roadtrip.core.games.LastMoveShade
import com.roadtrip.core.games.LegendEntry
import com.roadtrip.core.games.PieceInk
import com.roadtrip.core.games.PlayerLegend

/**
 * Board renderers for the five games (ANDGAME-004), driven purely by core [BoardState]
 * snapshots. Input is reported as taps; all rule arbitration stays on the server.
 *
 * Grid boards bound themselves to the smaller of the available width/height so the whole
 * board fits without scrolling on tablet, and piece glyphs scale to a fraction of their
 * square (ANDGAME-012). Piece glyph/ink come from the pure core [BoardPieces] mapping
 * (ANDGAME-013); winning tiles are highlighted from the core win-line indices (ANDGAME-014).
 */

private val LIGHT_SQUARE = Color(0xFFF0D9B5)
private val DARK_SQUARE = Color(0xFFB58863)

/**
 * The active/selected-square box: a 3.dp primary border (ANDGAME-012). Shared by the chess/
 * checkers selected square, the ultimate dictated sub-board, and the on-turn legend row
 * (ANDGAME-020) so all three read identically.
 */
@Composable
private fun Modifier.activeSquareBorder(): Modifier =
    border(3.dp, MaterialTheme.colorScheme.primary)

/**
 * FILL color for a last-move shade role (ANDGAME-024), or null for no shade. Deliberately a
 * background tint, never a border, so it reads distinctly from the ANDGAME-020 `3.dp`
 * `colorScheme.primary` selected/on-turn border and the ANDGAME-004 dictated-sub-board highlight.
 * MARK_FILL is a solid container swap for mark cells; the piece roles are translucent overlays
 * composited over the wood square so the glyph still reads (origin greyed dark, destination a dim
 * accent, capture a dim error tint).
 */
@Composable
private fun lastMoveShadeColor(shade: LastMoveShade?): Color? = when (shade) {
    null -> null
    LastMoveShade.MARK_FILL -> MaterialTheme.colorScheme.primaryContainer
    LastMoveShade.PIECE_ORIGIN -> Color.Black.copy(alpha = 0.38f)
    LastMoveShade.PIECE_DESTINATION -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    LastMoveShade.PIECE_CAPTURE -> MaterialTheme.colorScheme.error.copy(alpha = 0.32f)
}

@Composable
fun ChessBoardView(
    board: BoardState.ChessBoard,
    selectedSquare: String?,
    lastMove: LastMoveHighlight.PieceMove?,
    enabled: Boolean,
    onSquareTap: (String) -> Unit,
) {
    EightByEight(
        pieceAt = { square -> BoardPieces.chessGlyph(board.squares[square]) },
        inkAt = { square -> BoardPieces.chessInk(board.squares[square]) },
        shadeAt = { square -> lastMove?.shadeFor(square) },
        selectedSquare = selectedSquare,
        enabled = enabled,
        onSquareTap = onSquareTap,
    )
}

@Composable
fun CheckersBoardView(
    board: BoardState.CheckersBoard,
    selectedSquare: String?,
    lastMove: LastMoveHighlight.PieceMove?,
    enabled: Boolean,
    onSquareTap: (String) -> Unit,
) {
    EightByEight(
        pieceAt = { square -> BoardPieces.checkersGlyph(board.squares[square]) },
        inkAt = { square -> BoardPieces.checkersInk(board.squares[square]) },
        shadeAt = { square -> lastMove?.shadeFor(square) },
        selectedSquare = selectedSquare,
        enabled = enabled,
        onSquareTap = onSquareTap,
    )
}

/** A board slot bounded to the largest square that fits both dimensions (ANDGAME-012). */
@Composable
private fun BoardBox(content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // In a bounded slot maxHeight caps the board; in a scroll (maxHeight = Infinity)
        // it falls back to the available width, i.e. today's fill-width behavior.
        val side = minOf(maxWidth, maxHeight)
        Box(modifier = Modifier.size(side), contentAlignment = Alignment.Center, content = content)
    }
}

private data class PieceStyle(val color: Color, val outline: Boolean)

private fun PieceInk.toStyle(): PieceStyle = when (this) {
    // White fill with a dark outline so it reads on the light square (ANDGAME-013).
    PieceInk.WHITE_OUTLINED -> PieceStyle(Color.White, outline = true)
    PieceInk.BLACK -> PieceStyle(Color(0xFF1A1A1A), outline = false)
    PieceInk.RED -> PieceStyle(Color(0xFFC62828), outline = false)
}

/** A piece glyph sized to ~70% of its square (ANDGAME-012), tinted/outlined by ink. */
@Composable
private fun PieceGlyph(glyph: String, ink: PieceInk?) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val fontSize = with(LocalDensity.current) {
            (minOf(maxWidth, maxHeight) * BoardMetrics.PIECE_GLYPH_FRACTION).toSp()
        }
        val style = ink?.toStyle()
        val textStyle = if (style?.outline == true) {
            LocalTextStyle.current.copy(
                shadow = Shadow(color = Color(0xFF2B2B2B), offset = Offset.Zero, blurRadius = 8f),
            )
        } else {
            LocalTextStyle.current
        }
        Text(
            text = glyph,
            fontSize = fontSize,
            color = style?.color ?: Color.Unspecified,
            style = textStyle,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EightByEight(
    pieceAt: (String) -> String?,
    inkAt: (String) -> PieceInk?,
    shadeAt: (String) -> LastMoveShade?,
    selectedSquare: String?,
    enabled: Boolean,
    onSquareTap: (String) -> Unit,
) {
    BoardBox {
        Column(modifier = Modifier.fillMaxSize()) {
            for (rank in 8 downTo 1) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for ((fileIndex, file) in ('a'..'h').withIndex()) {
                        val square = "$file$rank"
                        // a1 dark, h1 light — matches the core checkers piece layout.
                        val darkSquare = (fileIndex + rank) % 2 == 1
                        val background = if (darkSquare) DARK_SQUARE else LIGHT_SQUARE
                        // Last-move shade (ANDGAME-024): a translucent overlay composited over the
                        // wood square (origin greyed dark, destination dim accent, capture dim
                        // error). It sits UNDER the piece glyph and is distinct from the selected-
                        // square border above.
                        val shadeColor = lastMoveShadeColor(shadeAt(square))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(background)
                                .then(
                                    if (shadeColor != null) Modifier.background(shadeColor) else Modifier,
                                )
                                .then(
                                    if (square == selectedSquare) {
                                        Modifier.activeSquareBorder()
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable(enabled = enabled) { onSquareTap(square) },
                            contentAlignment = Alignment.Center,
                        ) {
                            val glyph = pieceAt(square)
                            if (glyph != null) {
                                PieceGlyph(glyph, inkAt(square))
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
    lastMove: LastMoveHighlight.MarkCell?,
    enabled: Boolean,
    onCellTap: (Int) -> Unit,
) {
    // Finished when someone has won or every cell is filled (draw) — read as clearly over.
    val finished = board.winningLine != null || board.cells.all { it != null }
    BoardBox {
        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0..2) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0..2) {
                        val cell = row * 3 + col
                        val winning = board.winningLine?.contains(cell) == true
                        // ANDGAME-024: shade the last-placed cell. The win line keeps priority so a
                        // finished game shows BOTH the win-line and the final-move highlight.
                        val lastMoved = lastMove?.cellIndex == cell
                        val background = when {
                            winning -> MaterialTheme.colorScheme.tertiaryContainer
                            lastMoved -> MaterialTheme.colorScheme.primaryContainer
                            finished -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(2.dp)
                                .background(background)
                                .clickable(enabled = enabled && board.cells[cell] == null) {
                                    onCellTap(cell)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            board.cells[cell]?.let { mark ->
                                CellMark(mark.toString(), fraction = 0.7f)
                            }
                        }
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
    lastMove: LastMoveHighlight.UltimateCell?,
    enabled: Boolean,
    onCellTap: (subBoard: Int, cell: Int) -> Unit,
) {
    BoardBox {
        Column(modifier = Modifier.fillMaxSize()) {
            for (macroRow in 0..2) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (macroCol in 0..2) {
                        val subIndex = macroRow * 3 + macroCol
                        val dictated = board.nextBoard == subIndex
                        val macroWinning = board.macroWinningLine?.contains(subIndex) == true
                        val playable = enabled && (board.nextBoard == null || dictated) &&
                            board.macro[subIndex] == null
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(2.dp)
                                .border(
                                    width = if (dictated || macroWinning) 3.dp else 1.dp,
                                    color = when {
                                        macroWinning -> MaterialTheme.colorScheme.tertiary
                                        dictated -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outline
                                    },
                                ),
                        ) {
                            SubBoard(
                                cells = board.boards[subIndex],
                                winner = board.macro[subIndex],
                                winningCells = board.boardWinningLines[subIndex],
                                macroWinning = macroWinning,
                                // ANDGAME-024: the last-placed cell only when it lives in this sub-board.
                                lastMoveCell = lastMove?.takeIf { it.board == subIndex }?.cell,
                                enabled = playable,
                                onCellTap = { cell -> onCellTap(subIndex, cell) },
                            )
                        }
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
    winningCells: List<Int>?,
    macroWinning: Boolean,
    lastMoveCell: Int?,
    enabled: Boolean,
    onCellTap: (Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0..2) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (col in 0..2) {
                        val cell = row * 3 + col
                        val winning = winningCells?.contains(cell) == true
                        // ANDGAME-024: win line keeps priority so both highlights coexist.
                        val lastMoved = lastMoveCell == cell
                        val background = when {
                            winning -> MaterialTheme.colorScheme.tertiaryContainer
                            lastMoved -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(1.dp)
                                .background(background)
                                .clickable(enabled = enabled && cells[cell] == null) {
                                    onCellTap(cell)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            cells[cell]?.let { CellMark(it.toString(), fraction = 0.7f) }
                        }
                    }
                }
            }
        }
        if (winner != null) {
            val overlay = if (macroWinning) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlay.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                CellMark(winner.toString(), fraction = 0.8f)
            }
        }
    }
}

/**
 * Places a grid board and, for tic-tac-toe / ultimate, its player-identity legend (ANDGAME-020):
 * the legend sits below the board on phone and beside it on tablet/wide, switched on the same
 * width threshold as the ANDGAME-012 board sizing (`PlayerLegend.placeBeside`). An empty legend
 * (other game types) just centres the board unchanged.
 */
@Composable
fun BoardWithLegend(
    legend: List<LegendEntry>,
    modifier: Modifier = Modifier,
    board: @Composable () -> Unit,
) {
    if (legend.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { board() }
        return
    }
    BoxWithConstraints(modifier = modifier) {
        if (PlayerLegend.placeBeside(maxWidth.value.toInt())) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) { board() }
                PlayerLegendView(legend, modifier = Modifier.padding(start = 16.dp))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) { board() }
                Spacer(Modifier.height(12.dp))
                PlayerLegendView(legend)
            }
        }
    }
}

/**
 * Player-identity legend for tic-tac-toe / ultimate boards (ANDGAME-020): each row maps a mark
 * (X/O) to a player name from the pure [com.roadtrip.core.games.PlayerLegend] seam. The on-turn
 * player's row is boxed with the same [activeSquareBorder] the board draws for the active square;
 * the row also names "(you)" for the viewing profile. Placement (below on phone, beside on
 * tablet) is decided by the caller — this composable just stacks the rows.
 */
@Composable
fun PlayerLegendView(entries: List<LegendEntry>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (entry in entries) {
            Row(
                modifier = Modifier
                    .then(if (entry.isTurn) Modifier.activeSquareBorder() else Modifier)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.mark.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "  —  ${entry.name}${if (entry.isYou) " (you)" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** A text mark (X/O or captured winner) sized to a fraction of its cell (ANDGAME-012). */
@Composable
private fun CellMark(text: String, fraction: Float) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val fontSize = with(LocalDensity.current) {
            (minOf(maxWidth, maxHeight) * fraction).toSp()
        }
        Text(text, fontSize = fontSize, textAlign = TextAlign.Center)
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
    lastGuess: LastMoveHighlight.HangmanGuess?,
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
            "Wrong guesses: ${board.wrongCount}/${board.maxWrong}",
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(8.dp))
        for (rowLetters in ('A'..'Z').chunked(7)) {
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                for (letter in rowLetters) {
                    // ANDGAME-024: shade the most-recently guessed key (fill, not border). A guessed
                    // key is disabled, so the disabled container color must carry the highlight too.
                    val lastGuessed = lastGuess?.letter == letter
                    OutlinedButton(
                        onClick = { onGuess(letter) },
                        enabled = enabled && letter !in board.guessed,
                        colors = if (lastGuessed) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
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
