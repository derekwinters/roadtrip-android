package com.roadtrip.core.trips

import java.util.Locale
import kotlin.math.roundToLong

/**
 * The one shared formatter for trip summaries (ANDTRIP-010): every trip duration — whole-trip,
 * per-leg, and trip history — renders as `h:mm` from minutes through [clock].
 */
object TripFormat {
    private const val MINUTES_PER_HOUR = 60L

    /**
     * `h:mm` from [minutes]. Rounds to the nearest whole minute exactly once, before splitting,
     * so the minutes field carries into the hour and `0:60` never appears.
     */
    fun clock(minutes: Double): String {
        val total = minutes.roundToLong()
        val hours = total / MINUTES_PER_HOUR
        val mins = total % MINUTES_PER_HOUR
        return String.format(Locale.US, "%d:%02d", hours, mins)
    }

    /** Whole-trip / trip-history duration label: `<h:mm> total · <h:mm> moving`. */
    fun durationLine(wallMinutes: Double, movingMinutes: Double): String =
        "${clock(wallMinutes)} total · ${clock(movingMinutes)} moving"

    /** Per-leg duration parts (absent while the leg has no summary yet). */
    fun legDurations(wallMinutes: Double?, movingMinutes: Double?): List<String> = buildList {
        wallMinutes?.let { add(clock(it)) }
        movingMinutes?.let { add("${clock(it)} moving") }
    }

    fun miles(value: Double): String = String.format(Locale.US, "%.1f mi", value)
}
