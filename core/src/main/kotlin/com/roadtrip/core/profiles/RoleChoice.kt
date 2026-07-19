package com.roadtrip.core.profiles

import com.roadtrip.core.common.Role

/** One Kid/Parent option for the single-choice role selectors, with its display label (AND-013). */
data class RoleChoice(val role: Role, val label: String)

/**
 * The ordered role options shared by every single-choice role selector (profile picker,
 * profile admin) so they render the same Kid/Parent set with the same labels, presented with
 * the unmistakable filled + check `SegmentedButton` treatment (AND-013).
 */
object RoleChoices {
    val all: List<RoleChoice> = listOf(
        RoleChoice(Role.KID, "Kid"),
        RoleChoice(Role.PARENT, "Parent"),
    )

    /** Display label for [role]. */
    fun labelFor(role: Role): String = all.first { it.role == role }.label
}
