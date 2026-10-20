package com.roadtrip.core.settings

import com.roadtrip.core.api.Config

/**
 * The checklist caption naming which kinds of city are not being tracked (ANDSET-019).
 * Decided here over the **cached** config so it shows offline; collected cities are never
 * hidden — the caption is the only thing the toggles change on the checklist.
 */
object CityTrackingCaption {
    const val STOPPED_OFF = "Cities we stop at aren't being tracked"
    const val THROUGH_OFF = "Cities we travel through aren't being tracked"
    const val BOTH_OFF = "Cities we stop at and cities we travel through aren't being tracked"

    /** Null when both toggles are on, or when no config has been cached yet. */
    fun forConfig(config: Config?): String? {
        if (config == null) return null
        return when {
            !config.trackCitiesStopped && !config.trackCitiesThrough -> BOTH_OFF
            !config.trackCitiesStopped -> STOPPED_OFF
            !config.trackCitiesThrough -> THROUGH_OFF
            else -> null
        }
    }
}
