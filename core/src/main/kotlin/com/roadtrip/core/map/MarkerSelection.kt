package com.roadtrip.core.map

/**
 * Identity of a map marker for selection purposes (ANDMAP-015).
 *
 * The current position moves with every ping, so it is identified by its kind alone; every
 * other marker is identified by kind + position, so a refresh that keeps the marker keeps it
 * selected and a marker that is gone (or changed kind) is not matched.
 */
data class MarkerSelectionKey(
    val kind: MarkerKind,
    val lat: Double? = null,
    val lon: Double? = null,
)

/** The selection identity of this marker (ANDMAP-015). */
fun MapMarker.selectionKey(): MarkerSelectionKey = when (kind) {
    MarkerKind.CURRENT -> MarkerSelectionKey(kind)
    else -> MarkerSelectionKey(kind, lat, lon)
}

/**
 * Pure marker select/deselect decision (ANDMAP-015). `null` means nothing is selected (no label
 * open). Styling (ANDMAP-010) and which markers a role sees (ANDMAP-001) are not decided here.
 */
object MarkerSelection {
    /** Tapping the selected marker deselects it; tapping any other marker selects that one. */
    fun onMarkerTap(selected: MarkerSelectionKey?, tapped: MarkerSelectionKey): MarkerSelectionKey? =
        if (selected == tapped) null else tapped

    /** Tapping the empty map background always deselects. */
    @Suppress("UNUSED_PARAMETER")
    fun onBackgroundTap(selected: MarkerSelectionKey?): MarkerSelectionKey? = null

    /** Keeps the selection across a marker refresh only while its marker is still present. */
    fun reconcile(selected: MarkerSelectionKey?, markers: List<MapMarker>): MarkerSelectionKey? =
        selected?.takeIf { key -> markers.any { it.selectionKey() == key } }
}
