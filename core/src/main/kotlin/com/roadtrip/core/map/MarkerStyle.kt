package com.roadtrip.core.map

/**
 * Presentational style for a map marker, derived purely from its [MarkerKind] (ANDMAP-010).
 *
 * The app module maps each style to a vector drawable + center anchor; keeping the kind→style
 * decision here (plain Kotlin, no Android types) makes the iconography rules unit-testable on
 * the JVM. This is styling only: it does not decide which markers a role sees — that stays in
 * [MapScreenReducer] (ANDMAP-001).
 */
enum class MarkerStyle {
    /** Current position — a car glyph, centered on the point. */
    CAR,

    /** Trip start — a red flat dot. */
    RED_DOT,

    /** The active/next destination — a green flat dot, emphasized (ringed/larger). */
    GREEN_DOT_ACTIVE,

    /** A pending destination — a green flat dot. */
    GREEN_DOT,
}

/** Maps a [MarkerKind] to its presentational [MarkerStyle] (ANDMAP-010). */
fun markerStyleFor(kind: MarkerKind): MarkerStyle = when (kind) {
    MarkerKind.CURRENT -> MarkerStyle.CAR
    MarkerKind.START -> MarkerStyle.RED_DOT
    MarkerKind.ACTIVE_DESTINATION -> MarkerStyle.GREEN_DOT_ACTIVE
    MarkerKind.DESTINATION -> MarkerStyle.GREEN_DOT
}
