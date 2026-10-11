package com.roadtrip.app.branding

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * covers: AND-016 — the launcher icon uses the palette shared with chores-web-android:
 * white adaptive background, primary-blue map pin, grey center dot, and a thin transparent
 * ring cut out of the pin around the dot so the white background shows between them.
 * Reads the source resources directly (Gradle runs unit tests from the module directory).
 */
class LauncherIconPaletteTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun parse(path: String) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File(path))

    @Test
    fun launcherBackgroundIsWhite() {
        val colors = parse("src/main/res/values/colors.xml").getElementsByTagName("color")
        val background = (0 until colors.length).map { colors.item(it) as Element }
            .single { it.getAttribute("name") == "ic_launcher_background" }
        assertEquals("#FFFFFF", background.textContent.trim().uppercase())
    }

    @Test
    fun mapPinIsPrimaryBlueWithGreyCenterDot() {
        val paths = parse("src/main/res/drawable/ic_launcher_foreground.xml").getElementsByTagName("path")
        val fills = (0 until paths.length).map {
            (paths.item(it) as Element).getAttributeNS(androidNs, "fillColor").uppercase()
        }
        // Path order is the glyph's paint order: the pin body, then the center dot on top.
        assertEquals(listOf("#1361A7", "#818589"), fills)
    }

    /** Radii of the circular arcs (`a r,r ...`) drawn by a path, in viewport units. */
    private fun arcRadii(pathData: String): List<Double> =
        Regex("""[aA]\s*([0-9.]+)\s*,\s*([0-9.]+)""").findAll(pathData)
            .map { it.groupValues[1].toDouble() }
            .toList()

    @Test
    fun pinHasTransparentRingAroundCenterDot() {
        val paths = parse("src/main/res/drawable/ic_launcher_foreground.xml").getElementsByTagName("path")
        val pin = paths.item(0) as Element
        val dot = paths.item(1) as Element

        // The ring is a cut-out (evenOdd hole in the pin), never a painted white path, so the
        // monochrome layer — which tints every path one color — still shows the gap.
        assertEquals("evenOdd", pin.getAttributeNS(androidNs, "fillType"))
        assertEquals(2, paths.length)

        val dotRadius = arcRadii(dot.getAttributeNS(androidNs, "pathData")).single()
        assertEquals(6.5, dotRadius, "the center dot keeps its size")
        val holeRadius = arcRadii(pin.getAttributeNS(androidNs, "pathData")).maxOrNull()
        assertEquals(8.5, holeRadius, "pin needs a round hole ~2 units wider than the dot")
        assertTrue(holeRadius!! - dotRadius in 1.5..2.5, "the white ring stays thin")
    }
}
