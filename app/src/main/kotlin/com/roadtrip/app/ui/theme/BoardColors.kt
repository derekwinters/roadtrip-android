package com.roadtrip.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Board/domain colours (AND-017) as named tokens in one palette object. They are not forced into
 * `colorScheme` roles: a wood board square or a chess piece ink has no Material role. Boards read
 * them through [LocalBoardColors], which [RoadTripTheme] provides, so a dark variant is a new
 * [BoardColors] instance here — `Boards.kt` does not change.
 */
@Immutable
data class BoardColors(
    /** Light square of the 8×8 chess/checkers board. */
    val lightSquare: Color,
    /** Dark square of the 8×8 chess/checkers board. */
    val darkSquare: Color,
    /** Fill of white pieces (`PieceInk.WHITE_OUTLINED`, ANDGAME-013). */
    val pieceWhite: Color,
    /** Fill of black pieces (`PieceInk.BLACK`). */
    val pieceBlack: Color,
    /** Fill of red checkers pieces (`PieceInk.RED`). */
    val pieceRed: Color,
    /** Dark outline shadow that lets white pieces read on the light square (ANDGAME-013). */
    val pieceOutlineShadow: Color,
    /** Translucent overlay greying the last move's origin square (ANDGAME-024). */
    val lastMoveOriginShade: Color,
)

/** The board palette for the light theme — the board's colours before AND-017, unchanged. */
val LightBoardColors = BoardColors(
    lightSquare = BoardLightSquare,
    darkSquare = BoardDarkSquare,
    pieceWhite = PieceInkWhite,
    pieceBlack = PieceInkBlack,
    pieceRed = PieceInkRed,
    pieceOutlineShadow = PieceOutlineShadow,
    lastMoveOriginShade = LastMoveOriginShade,
)

/** Theme-aware holder for the board palette; [RoadTripTheme] provides the current one. */
val LocalBoardColors = staticCompositionLocalOf { LightBoardColors }
