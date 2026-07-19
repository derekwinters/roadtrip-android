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

    /** Checkers is red vs black (never white): w/W → [PieceInk.BLACK], r/R → [PieceInk.RED]. */
    fun checkersInk(code: String?): PieceInk? = when (code) {
        "w", "W" -> PieceInk.BLACK
        "r", "R" -> PieceInk.RED
        else -> null
    }
}

/**
 * Board sizing so every grid board fits the viewport without scrolling and pieces scale with
 * their square (ANDGAME-012). Pure math; the Compose layer converts the result to a bounded
 * square and a glyph font size.
 */
object BoardMetrics {
    /** Fraction of a square's side a piece glyph should occupy. */
    const val PIECE_GLYPH_FRACTION = 0.7f

    /** The largest square that fits both available dimensions (never negative). */
    fun squareSide(availableWidth: Float, availableHeight: Float): Float =
        minOf(availableWidth, availableHeight).coerceAtLeast(0f)

    /** Piece glyph size for a given square side. */
    fun pieceGlyphSize(squareSide: Float): Float = squareSide * PIECE_GLYPH_FRACTION
}
