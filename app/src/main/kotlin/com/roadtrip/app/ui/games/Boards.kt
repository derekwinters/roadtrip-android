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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
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
import com.roadtrip.app.ui.theme.BoardColors
import com.roadtrip.app.ui.theme.LocalBoardColors
import com.roadtrip.core.games.BoardMetrics
import com.roadtrip.core.games.BoardPieces
import com.roadtrip.core.games.BoardSquareShade
import com.roadtrip.core.games.BoardState
import com.roadtrip.core.games.FourInARow
import com.roadtrip.core.games.LastMoveHighlight
import com.roadtrip.core.games.LastMoveShade
import com.roadtrip.core.games.LegendEntry
import com.roadtrip.core.games.PieceInk
import com.roadtrip.core.games.PlayerLegend
import com.roadtrip.core.games.StagedMove
import com.roadtrip.core.games.StagedMoveHighlight
import com.roadtrip.core.games.StagedMoveShade

/**
 * Board renderers for the six games (ANDGAME-004), driven purely by core [BoardState]
 * snapshots. Input is reported as taps; all rule arbitration stays on the server.
 *
 * Grid boards bound themselves to the smaller of the available width/height so the whole
 * board fits without scrolling on tablet, and piece glyphs scale to a fraction of their
 * square (ANDGAME-012); checkers pieces are drawn discs at half the square (ANDGAME-025). Piece
 * glyph/ink come from the pure core [BoardPieces] mapping
 * (ANDGAME-013); winning tiles are highlighted from the core win-line indices (ANDGAME-014).
 * Board/domain colours (squares, piece inks, outline shadow, origin shade) come from the theme's
 * board palette via [LocalBoardColors] (AND-018) — never raw literals here.
 */

/**
 * The active/selected-square box: a 3.dp primary border (ANDGAME-012). Shared by the chess/
 * checkers selected square, the ultimate dictated sub-board, and the on-turn legend row
 * (ANDGAME-020) so all three read identically.
 */
@Composable
private fun Modifier.activeSquareBorder(): Modifier =
    border(3.dp, MaterialTheme.colorScheme.primary)

/**
 * FILL color for a square shade role, or null for no shade: the ANDGAME-024 last-move roles and
 * the ANDGAME-026 staged-move role. Deliberately a background tint, never a border, so it reads
 * distinctly from the ANDGAME-020 `3.dp` `colorScheme.primary` selected/on-turn border and the
 * ANDGAME-004 dictated-sub-board highlight. MARK_FILL is a solid container swap for mark cells; the
 * piece roles are translucent overlays composited over the wood square so the glyph still reads
 * (origin greyed dark, destination a dim accent, capture a dim error tint). The staged move uses
 * the `tertiary` role, so it differs from every last-move shade and follows the effective theme.
 * Takes the scheme and board palette explicitly so the mapping is testable off-device.
 */
internal fun boardShadeColor(shade: BoardSquareShade?, scheme: ColorScheme, board: BoardColors): Color? =
    when (shade) {
        null -> null
        LastMoveShade.MARK_FILL -> scheme.primaryContainer
        LastMoveShade.PIECE_ORIGIN -> board.lastMoveOriginShade
        LastMoveShade.PIECE_DESTINATION -> scheme.primary.copy(alpha = 0.30f)
        LastMoveShade.PIECE_CAPTURE -> scheme.error.copy(alpha = 0.32f)
        StagedMoveShade.STAGED_MOVE -> scheme.tertiary.copy(alpha = 0.45f)
    }

/** [boardShadeColor] for the current theme's colour scheme and board palette. */
@Composable
private fun currentBoardShadeColor(shade: BoardSquareShade?): Color? =
    boardShadeColor(shade, MaterialTheme.colorScheme, LocalBoardColors.current)

@Composable
fun ChessBoardView(
    board: BoardState.ChessBoard,
    selectedSquare: String?,
    lastMove: LastMoveHighlight.PieceMove?,
    enabled: Boolean,
    staged: StagedMoveHighlight? = null,
    onSquareTap: (String) -> Unit,
) {
    EightByEight(
        piece = { square ->
            val glyph = BoardPieces.chessGlyph(board.squares[square])
            if (glyph != null) PieceGlyph(glyph, BoardPieces.chessInk(board.squares[square]))
        },
        // ANDGAME-026: a staged move's squares take the staged shade; ANDGAME-024 elsewhere.
        shadeAt = { square -> StagedMove.shadeAt(square, staged, lastMove) },
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
    staged: StagedMoveHighlight? = null,
    onSquareTap: (String) -> Unit,
) {
    EightByEight(
        piece = { square ->
            val code = board.squares[square]
            val ink = BoardPieces.checkersInk(code)
            if (ink != null) {
                val crown = if (BoardPieces.checkersIsKing(code)) BoardPieces.checkersGlyph(code) else null
                CheckersDisc(ink, crown)
            }
        },
        // ANDGAME-026: a staged move's squares take the staged shade; ANDGAME-024 elsewhere.
        shadeAt = { square -> StagedMove.shadeAt(square, staged, lastMove) },
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

private fun PieceInk.toStyle(colors: BoardColors): PieceStyle = when (this) {
    // White fill with a dark outline so it reads on the light square (ANDGAME-013).
    PieceInk.WHITE_OUTLINED -> PieceStyle(colors.pieceWhite, outline = true)
    PieceInk.BLACK -> PieceStyle(colors.pieceBlack, outline = false)
    PieceInk.RED -> PieceStyle(colors.pieceRed, outline = false)
    PieceInk.YELLOW -> PieceStyle(colors.pieceYellow, outline = false)
}

/** A piece glyph sized to ~70% of its square (ANDGAME-012), tinted/outlined by ink. */
@Composable
private fun PieceGlyph(glyph: String, ink: PieceInk?) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val fontSize = with(LocalDensity.current) {
            (minOf(maxWidth, maxHeight) * BoardMetrics.PIECE_GLYPH_FRACTION).toSp()
        }
        val boardColors = LocalBoardColors.current
        val style = ink?.toStyle(boardColors)
        val textStyle = if (style?.outline == true) {
            LocalTextStyle.current.copy(
                shadow = Shadow(color = boardColors.pieceOutlineShadow, offset = Offset.Zero, blurRadius = 8f),
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

/**
 * A checkers piece (ANDGAME-025): a drawn circle whose diameter is
 * [BoardMetrics.checkersPieceDiameter] of the square — never sized from font/glyph metrics —
 * filled with the board palette's ink. A king overlays its [crown] glyph on the SAME disc, sized
 * to the disc ([BoardMetrics.checkersCrownGlyphSize]) and inked with the white-piece token so it
 * reads on red and black alike; men and kings therefore read at the same size. The disc sits
 * inside the square, leaving the last-move shade and selected border visible around it.
 */
@Composable
private fun CheckersDisc(ink: PieceInk, crown: String?) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val squareSide = minOf(maxWidth, maxHeight)
        val diameter = BoardMetrics.checkersPieceDiameter(squareSide.value).dp
        val boardColors = LocalBoardColors.current
        Box(
            modifier = Modifier
                .size(diameter)
                .background(ink.toStyle(boardColors).color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (crown != null) {
                val crownSize = with(LocalDensity.current) {
                    BoardMetrics.checkersCrownGlyphSize(squareSide.value).dp.toSp()
                }
                Text(
                    text = crown,
                    fontSize = crownSize,
                    color = boardColors.pieceWhite,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun EightByEight(
    piece: @Composable (String) -> Unit,
    shadeAt: (String) -> BoardSquareShade?,
    selectedSquare: String?,
    enabled: Boolean,
    onSquareTap: (String) -> Unit,
) {
    val boardColors = LocalBoardColors.current
    BoardBox {
        Column(modifier = Modifier.fillMaxSize()) {
            for (rank in 8 downTo 1) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for ((fileIndex, file) in ('a'..'h').withIndex()) {
                        val square = "$file$rank"
                        // a1 dark, h1 light — matches the core checkers piece layout.
                        val darkSquare = (fileIndex + rank) % 2 == 1
                        val background = if (darkSquare) boardColors.darkSquare else boardColors.lightSquare
                        // Last-move (ANDGAME-024) or staged-move (ANDGAME-026) shade: a translucent
                        // overlay composited over the wood square. It sits UNDER the piece glyph
                        // and is distinct from the selected-square border above.
                        val shadeColor = currentBoardShadeColor(shadeAt(square))
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
                            piece(square)
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
 * Four in a Row (ANDGAME-028): a 7×6 grid drawn with row 0 at the bottom. Cells stay square —
 * sized by [BoardMetrics.gridCellSide] so the whole grid fits both dimensions (ANDGAME-012) — and
 * each disc is a drawn circle inked from the board palette via [BoardPieces.fourInARowInk].
 * Tapping anywhere in a column reports that column; a full column is not tappable. The winning
 * four (ANDGAME-014 idiom) and the last drop's landing cell (ANDGAME-024 MARK_FILL) are fills.
 */
@Composable
fun FourInARowBoardView(
    board: BoardState.FourInARowBoard,
    lastMove: LastMoveHighlight.DiscCell?,
    enabled: Boolean,
    onColumnTap: (Int) -> Unit,
) {
    // Finished when someone has won or every cell is filled (draw) — read as clearly over.
    val finished = board.winningLine != null || board.cells.all { it != null }
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // In a scroll (maxHeight = Infinity) the width alone limits the cell size.
        val cellSide = BoardMetrics.gridCellSide(
            availableWidth = maxWidth.value,
            availableHeight = maxHeight.value,
            columns = FourInARow.COLUMNS,
            rows = FourInARow.ROWS,
        )
        val cell = cellSide.dp
        Row(modifier = Modifier.size(width = cell * FourInARow.COLUMNS, height = cell * FourInARow.ROWS)) {
            for (column in 0 until FourInARow.COLUMNS) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = enabled && FourInARow.isColumnPlayable(board, column)) {
                            onColumnTap(column)
                        },
                ) {
                    // Top row first so row 0 sits at the bottom of the grid.
                    for (row in FourInARow.ROWS - 1 downTo 0) {
                        val index = FourInARow.index(row, column)
                        val winning = board.winningLine?.contains(index) == true
                        val lastMoved = lastMove != null && lastMove.row == row && lastMove.column == column
                        // Win line keeps priority so a finished game shows both highlights.
                        val background = when {
                            winning -> MaterialTheme.colorScheme.tertiaryContainer
                            lastMoved -> currentBoardShadeColor(lastMove?.shade)
                                ?: MaterialTheme.colorScheme.primaryContainer
                            finished -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(1.dp)
                                .background(background),
                            contentAlignment = Alignment.Center,
                        ) {
                            FourInARowDisc(board.cells[index], cellSide)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One Four in a Row slot: a drawn circle [BoardMetrics.fourInARowDiscDiameter] of the cell, filled
 * with the disc's palette ink, or an empty hole in the surface colour.
 */
@Composable
private fun FourInARowDisc(disc: Int?, cellSide: Float) {
    val ink = BoardPieces.fourInARowInk(disc)
    val fill = ink?.toStyle(LocalBoardColors.current)?.color ?: MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .size(BoardMetrics.fourInARowDiscDiameter(cellSide).dp)
            .background(fill, CircleShape),
    )
}

/**
 * Places a grid board and, for tic-tac-toe / ultimate / chess / checkers / Four in a Row, its
 * player-identity legend (ANDGAME-020):
 * the legend sits below the board on phone and beside it on tablet/wide, switched on the same
 * width threshold as the ANDGAME-012 board sizing (`PlayerLegend.placeBeside`). An empty legend
 * (hangman) just centres the board unchanged.
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
 * Player-identity legend (ANDGAME-020): each row maps a mark (X/O) or, for chess / checkers /
 * Four in a Row, a
 * colour swatch in that side's piece ink to a player name from the pure
 * [com.roadtrip.core.games.PlayerLegend] seam. The on-turn
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
                val ink = entry.ink
                if (ink != null) {
                    LegendSwatch(ink)
                } else {
                    Text(
                        entry.mark?.toString().orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Text(
                    "  —  ${entry.name}${if (entry.isYou) " (you)" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * A legend colour swatch (ANDGAME-020): a small disc filled with the side's piece ink from the
 * board palette, so it matches the board's pieces in light and dark themes. The white side gets
 * the same dark outline colour its pieces use so the swatch reads on a light background.
 */
@Composable
private fun LegendSwatch(ink: PieceInk) {
    val boardColors = LocalBoardColors.current
    val style = ink.toStyle(boardColors)
    Box(
        modifier = Modifier
            .size(LEGEND_SWATCH_SIZE)
            .then(
                if (style.outline) {
                    Modifier.border(1.dp, boardColors.pieceOutlineShadow, CircleShape)
                } else {
                    Modifier
                },
            )
            .background(style.color, CircleShape),
    )
}

private val LEGEND_SWATCH_SIZE = 18.dp

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
