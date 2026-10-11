package com.roadtrip.app.branding

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import org.junit.Test
import org.w3c.dom.Element

/**
 * covers: AND-016 — the launcher icon uses the palette shared with chores-web-android:
 * white adaptive background, primary-blue map pin, grey center dot.
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
}
