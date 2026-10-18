package com.roadtrip.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * covers: AND-017 — the app theme is a defined palette with no visual change: every colour role
 * of the app light scheme equals the Material 3 baseline (`lightColorScheme()` defaults) the bare
 * `MaterialTheme {}` rendered with before, and the board palette keeps the board's prior colours.
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
}
