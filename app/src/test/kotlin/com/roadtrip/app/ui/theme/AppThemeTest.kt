package com.roadtrip.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.roadtrip.core.settings.EffectiveTheme
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

/**
 * covers: AND-018 — the app theme is a defined palette with no visual change: every colour role
 * of the app light scheme equals the Material 3 baseline (`lightColorScheme()` defaults) the bare
 * `MaterialTheme {}` rendered with before, and the board palette keeps the board's prior colours.
 *
 * covers: AND-019 — the dark counterpart: the app dark scheme equals the Material 3 baseline dark
 * scheme (`darkColorScheme()` defaults), the dark board palette has its own values, and the root
 * picks scheme + palette from the resolved [EffectiveTheme].
 */
class AppThemeTest {

    /** Every Color-valued role getter on [ColorScheme] (Color is an inline class → `long`). */
    private fun roles(scheme: ColorScheme): Map<String, Long> =
        ColorScheme::class.java.methods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == Long::class.javaPrimitiveType }
            .associate { it.name.substringBefore('-') to (it.invoke(scheme) as Long) }

    /** ARGB hex of a packed sRGB [Color] value (its upper 32 bits). */
    private fun hex(packed: Long): String = "#%08X".format((packed ushr 32).toInt())

    @Test
    fun appLightSchemeEqualsMaterialBaseline() {
        val baseline = roles(lightColorScheme())
        val app = roles(AppLightColorScheme)
        assertTrue(baseline.size >= 25, "expected the full set of colour roles, got ${baseline.keys}")
        assertEquals(baseline.keys, app.keys)
        val changed = baseline.keys.filter { baseline[it] != app[it] }.map { role ->
            "$role: baseline ${hex(baseline.getValue(role))} vs app ${hex(app.getValue(role))}"
        }
        assertTrue(changed.isEmpty(), "app colour roles differ from the baseline:\n" + changed.joinToString("\n"))
    }

    @Test
    fun lightBoardColorsKeepPreviousBoardValues() {
        assertEquals(Color(0xFFF0D9B5), LightBoardColors.lightSquare)
        assertEquals(Color(0xFFB58863), LightBoardColors.darkSquare)
        assertEquals(Color.White, LightBoardColors.pieceWhite)
        assertEquals(Color(0xFF1A1A1A), LightBoardColors.pieceBlack)
        assertEquals(Color(0xFFC62828), LightBoardColors.pieceRed)
        assertEquals(Color(0xFF2B2B2B), LightBoardColors.pieceOutlineShadow)
        assertEquals(Color.Black.copy(alpha = 0.38f), LightBoardColors.lastMoveOriginShade)
    }

    @Test
    fun appDarkSchemeEqualsMaterialDarkBaseline() {
        val baseline = roles(darkColorScheme())
        val app = roles(AppDarkColorScheme)
        assertTrue(baseline.size >= 25, "expected the full set of colour roles, got ${baseline.keys}")
        assertEquals(baseline.keys, app.keys)
        val changed = baseline.keys.filter { baseline[it] != app[it] }.map { role ->
            "$role: baseline ${hex(baseline.getValue(role))} vs app ${hex(app.getValue(role))}"
        }
        assertTrue(changed.isEmpty(), "app dark colour roles differ from the dark baseline:\n" + changed.joinToString("\n"))
    }

    @Test
    fun darkBoardColorsAreTheirOwnPaletteAndKeepSquaresDistinct() {
        // Squares get dark-appropriate values, not the light board's.
        assertNotEquals(LightBoardColors.lightSquare, DarkBoardColors.lightSquare)
        assertNotEquals(LightBoardColors.darkSquare, DarkBoardColors.darkSquare)
        // The two squares still read as light vs dark on the dark board.
        assertTrue(DarkBoardColors.lightSquare.luminance() > DarkBoardColors.darkSquare.luminance())
        // White pieces stay lighter than black pieces.
        assertTrue(DarkBoardColors.pieceWhite.luminance() > DarkBoardColors.pieceBlack.luminance())
    }

    @Test
    fun rootPicksSchemeAndBoardPaletteFromEffectiveTheme() {
        assertSame(AppLightColorScheme, colorSchemeFor(EffectiveTheme.LIGHT))
        assertSame(AppDarkColorScheme, colorSchemeFor(EffectiveTheme.DARK))
        assertSame(LightBoardColors, boardColorsFor(EffectiveTheme.LIGHT))
        assertSame(DarkBoardColors, boardColorsFor(EffectiveTheme.DARK))
    }
}
