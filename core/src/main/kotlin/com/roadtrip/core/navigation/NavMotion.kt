package com.roadtrip.core.navigation

/** The Material motion used for a navigation route change (AND-011). */
enum class NavMotion { FADE_THROUGH, SHARED_AXIS_X }

/**
 * Classifies a navigation-compose route change into a Material motion (AND-011). Switching
 * between the five top-level destinations (AND-004) is a fade-through; every other change —
 * drilling into a detail/hierarchical route or popping back out — is a shared-axis X slide.
 *
 * Routes are navigation-compose patterns (e.g. `map?lat={lat}&lon={lon}`, `board/{gameId}`);
 * only the base segment before any argument or nested path matters for the decision, so the
 * classifier is independent of the concrete argument values.
 */
object NavMotionClassifier {
    /** Base segments of the five NavigationSuiteScaffold destinations (Routes.*). */
    val topLevelRoutes: Set<String> = setOf("journal", "map", "games", "checklist", "trip")

    /** True when [route]'s base segment is one of the five top-level tabs. */
    fun isTopLevel(route: String?): Boolean = baseSegment(route) in topLevelRoutes

    /**
     * Fade-through only when both ends of the change are top-level tabs; every other change
     * (a push into or a pop out of a detail route) is a shared-axis X slide.
     */
    fun motionFor(fromRoute: String?, toRoute: String?): NavMotion =
        if (isTopLevel(fromRoute) && isTopLevel(toRoute)) NavMotion.FADE_THROUGH
        else NavMotion.SHARED_AXIS_X

    /** `board/42` → `board`, `map?lat=1&lon=2` → `map`, `trip?dest=x` → `trip`, `trips?..` → `trips`. */
    private fun baseSegment(route: String?): String? =
        route?.substringBefore('?')?.substringBefore('/')
}
