package com.roadtrip.core.games

/**
 * Presentation-layer ink for a board piece (ANDGAME-013). The thin Compose UI maps each value
 * to a concrete color (and, for [WHITE_OUTLINED], a dark outline/shadow so a white fill reads
 * on the light square). Keeping the mapping here makes piece styling JVM-testable.
 */
enum class PieceInk {
    /** White side: a white fill that needs a dark outline to stay legible on light squares. */
    WHITE_OUTLINED,
    BLACK,
    RED,

    /** Four in a Row's joiner disc (ANDGAME-028). */
    YELLOW,
}

/**
 * Pure glyph/ink mappings shared by the chess and checkers boards (ANDGAME-013).
 *
 * Chess uses the SOLID Unicode glyphs (`♚♛♜♝♞♟`) for BOTH colors, because the outline glyphs
 * (`♔♕♖♗♘♙`) render unfilled on light squares; the side is conveyed by [PieceInk], not the glyph.
 * Checkers is red vs black — never white.
 */
object BoardPieces {
    private val CHESS_SOLID = mapOf(
        'K' to "♚", 'Q' to "♛", 'R' to "♜", 'B' to "♝", 'N' to "♞", 'P' to "♟",
    )

    /** Solid glyph for a chess piece code like "wK"/"bP"; null for an empty square. */
    fun chessGlyph(code: String?): String? = code?.getOrNull(1)?.let { CHESS_SOLID[it] }

    /** White pieces read as [PieceInk.WHITE_OUTLINED], black as [PieceInk.BLACK]. */
    fun chessInk(code: String?): PieceInk? = when (code?.getOrNull(0)) {
        'w' -> PieceInk.WHITE_OUTLINED
        'b' -> PieceInk.BLACK
        else -> null
    }

    /** Checkers glyph: men are discs, kings crowned; null for an empty square. */
    fun checkersGlyph(code: String?): String? = when (code) {
        "w", "r" -> "●"
        "W", "R" -> "♛"
        else -> null
    }

    /**
     * True for a crowned checkers piece (W/R). Kings draw the same disc as men with a crown
     * glyph ([checkersGlyph]) overlaid on it (ANDGAME-025).
     */
    fun checkersIsKing(code: String?): Boolean = code == "W" || code == "R"

    /** Checkers is red vs black (never white): w/W → [PieceInk.BLACK], r/R → [PieceInk.RED]. */
    fun checkersInk(code: String?): PieceInk? = when (code) {
        "w", "W" -> PieceInk.BLACK
        "r", "R" -> PieceInk.RED
        else -> null
    }

    /**
     * Four in a Row disc ink (ANDGAME-028): cell `0` (the creator, `players[0]`, moves first) is
     * [PieceInk.RED], cell `1` (the joiner) is [PieceInk.YELLOW]; null for an empty cell.
     */
    fun fourInARowInk(disc: Int?): PieceInk? = when (disc) {
        0 -> PieceInk.RED
        1 -> PieceInk.YELLOW
        else -> null
    }
}

/**
 * Board sizing so every grid board fits the viewport without scrolling and pieces scale with
 * their square (ANDGAME-012); checkers discs are sized separately (ANDGAME-025). Pure math; the
 * Compose layer converts the result to a bounded square, a glyph font size, or a disc diameter.
 */
object BoardMetrics {
    /** Fraction of a square's side a piece glyph should occupy. */
    const val PIECE_GLYPH_FRACTION = 0.7f

    /** The largest square that fits both available dimensions (never negative). */
    fun squareSide(availableWidth: Float, availableHeight: Float): Float =
        minOf(availableWidth, availableHeight).coerceAtLeast(0f)

    /**
     * Side of one square cell for a non-square [columns]×[rows] grid (ANDGAME-012/028): the
     * largest cell for which the whole grid fits both available dimensions, so cells stay square
     * instead of stretching. Never negative; a degenerate grid yields zero.
     */
    fun gridCellSide(availableWidth: Float, availableHeight: Float, columns: Int, rows: Int): Float {
        if (columns <= 0 || rows <= 0) return 0f
        return minOf(availableWidth / columns, availableHeight / rows).coerceAtLeast(0f)
    }

    /** Fraction of a Four in a Row cell's side its drawn disc's DIAMETER occupies (ANDGAME-028). */
    const val FOUR_IN_A_ROW_DISC_FRACTION = 0.8f

    /** Four in a Row disc diameter for a given cell side (ANDGAME-028). */
    fun fourInARowDiscDiameter(cellSide: Float): Float = cellSide * FOUR_IN_A_ROW_DISC_FRACTION

    /** Piece glyph size for a given square side. */
    fun pieceGlyphSize(squareSide: Float): Float = squareSide * PIECE_GLYPH_FRACTION

    /**
     * Fraction of a square's side a checkers disc's DIAMETER occupies (ANDGAME-025). A drawn
     * circle, never a glyph, and independent of [PIECE_GLYPH_FRACTION].
     */
    const val CHECKERS_PIECE_DIAMETER_FRACTION = 0.5f

    /** Checkers disc diameter for a given square side (ANDGAME-025). */
    fun checkersPieceDiameter(squareSide: Float): Float = squareSide * CHECKERS_PIECE_DIAMETER_FRACTION

    /** A king's crown glyph size: [PIECE_GLYPH_FRACTION] of the disc it sits on (ANDGAME-025). */
    fun checkersCrownGlyphSize(squareSide: Float): Float =
        checkersPieceDiameter(squareSide) * PIECE_GLYPH_FRACTION
}
