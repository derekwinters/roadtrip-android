package com.roadtrip.core.settings

/**
 * The device's theme preference (ANDSET-007): one value per device, not per profile, shown in
 * Settings to parents and kids alike.
 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** The theme actually applied at the root (AND-019) once [ThemeMode] is resolved. */
enum class EffectiveTheme { LIGHT, DARK }

/**
 * The one place light vs dark is decided (ANDSET-008). The app root feeds it the preference and
 * the system dark-mode flag; no feature screen reads the system flag itself (AND-019).
 */
object ThemeResolver {
    fun resolve(mode: ThemeMode, systemDark: Boolean): EffectiveTheme = when (mode) {
        ThemeMode.LIGHT -> EffectiveTheme.LIGHT
        ThemeMode.DARK -> EffectiveTheme.DARK
        ThemeMode.SYSTEM -> if (systemDark) EffectiveTheme.DARK else EffectiveTheme.LIGHT
    }
}

/**
 * Pure codec for the persisted theme preference (ANDSET-007). The app-module DataStore stores
 * [encode]'s token under [KEY]; [decode] never fails — a missing, unknown or garbled value is
 * [DEFAULT] (System).
 */
object ThemePreference {
    /** Device-wide DataStore key; deliberately not profile-scoped. */
    const val KEY: String = "theme_mode"

    /** A never-set preference follows the system. */
    val DEFAULT: ThemeMode = ThemeMode.SYSTEM

    fun encode(mode: ThemeMode): String = mode.name.lowercase()

    fun decode(stored: String?): ThemeMode =
        ThemeMode.entries.firstOrNull { encode(it) == stored } ?: DEFAULT
}

/** One option of the Settings theme selector. */
data class ThemeChoice(val mode: ThemeMode, val label: String)

/**
 * The ordered theme options with their labels (ANDSET-007), rendered as AND-013 single-choice
 * chips — the single source of truth for the selector's order and wording.
 */
object ThemeChoices {
    val all: List<ThemeChoice> = listOf(
        ThemeChoice(ThemeMode.LIGHT, "Light"),
        ThemeChoice(ThemeMode.DARK, "Dark"),
        ThemeChoice(ThemeMode.SYSTEM, "System"),
    )

    /** Display label for [mode]. */
    fun labelFor(mode: ThemeMode): String = all.first { it.mode == mode }.label
}
