package com.roadtrip.core.settings

import com.roadtrip.core.common.Role
import com.roadtrip.core.profiles.FeatureVisibilityRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// covers: ANDSET-007, ANDSET-008
class ThemeModeTest {

    // ---- ThemeResolver (ANDSET-008) -----------------------------------------------------

    @Test
    fun `System follows the system dark flag ANDSET-008`() {
        assertEquals(EffectiveTheme.DARK, ThemeResolver.resolve(ThemeMode.SYSTEM, systemDark = true))
        assertEquals(EffectiveTheme.LIGHT, ThemeResolver.resolve(ThemeMode.SYSTEM, systemDark = false))
    }

    @Test
    fun `Light is always light ANDSET-008`() {
        assertEquals(EffectiveTheme.LIGHT, ThemeResolver.resolve(ThemeMode.LIGHT, systemDark = true))
        assertEquals(EffectiveTheme.LIGHT, ThemeResolver.resolve(ThemeMode.LIGHT, systemDark = false))
    }

    @Test
    fun `Dark is always dark ANDSET-008`() {
        assertEquals(EffectiveTheme.DARK, ThemeResolver.resolve(ThemeMode.DARK, systemDark = true))
        assertEquals(EffectiveTheme.DARK, ThemeResolver.resolve(ThemeMode.DARK, systemDark = false))
    }

    // ---- ThemePreference (ANDSET-007) ---------------------------------------------------

    @Test
    fun `a never-set preference is System ANDSET-007`() {
        assertEquals(ThemeMode.SYSTEM, ThemePreference.DEFAULT)
        assertEquals(ThemeMode.SYSTEM, ThemePreference.decode(null))
    }

    @Test
    fun `every mode round-trips through the stored token ANDSET-007`() {
        for (mode in ThemeMode.entries) {
            assertEquals(mode, ThemePreference.decode(ThemePreference.encode(mode)))
        }
    }

    @Test
    fun `unknown or garbled stored values fall back to System ANDSET-007`() {
        for (garbled in listOf("", "   ", "purple", "DARKISH", "{\"mode\":1}", "\u0000")) {
            assertEquals(ThemeMode.SYSTEM, ThemePreference.decode(garbled), "stored value '$garbled'")
        }
    }

    @Test
    fun `storage key is the device-wide theme_mode key ANDSET-007`() {
        assertEquals("theme_mode", ThemePreference.KEY)
    }

    @Test
    fun `theme choices are Light, Dark, System in that order ANDSET-007`() {
        assertEquals(listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM), ThemeChoices.all.map { it.mode })
        assertEquals(listOf("Light", "Dark", "System"), ThemeChoices.all.map { it.label })
        assertEquals("System", ThemeChoices.labelFor(ThemeMode.SYSTEM))
    }

    @Test
    fun `theme setting is shown to parents and kids ANDSET-007`() {
        assertTrue(FeatureVisibilityRules.forRole(Role.PARENT).themeSetting)
        assertTrue(FeatureVisibilityRules.forRole(Role.KID).themeSetting)
    }
}
