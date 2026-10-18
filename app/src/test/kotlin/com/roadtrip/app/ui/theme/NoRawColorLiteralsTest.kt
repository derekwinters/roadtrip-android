package com.roadtrip.app.ui.theme

import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

/**
 * covers: AND-017 — no feature UI source hardcodes a raw `Color(0x…)` literal; raw colour values
 * live only in the theme package, and the activity wraps the UI in the app theme rather than a
 * bare `MaterialTheme`. Reads the sources directly (Gradle runs unit tests from the module dir).
 */
class NoRawColorLiteralsTest {

    private val sourceRoot = File("src/main/kotlin")
    private val themeDir = File(sourceRoot, "com/roadtrip/app/ui/theme")
    private val rawColorLiteral = Regex("""\bColor\(\s*0[xX]""")

    @Test
    fun noRawColorLiteralsOutsideThemePackage() {
        val sources = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty(), "no sources found under ${sourceRoot.absolutePath}")
        val offenders = sources
            .filterNot { it.canonicalPath.startsWith(themeDir.canonicalPath + File.separator) }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    if (rawColorLiteral.containsMatchIn(line)) "${file.relativeTo(sourceRoot)}:${i + 1}: ${line.trim()}" else null
                }
            }
        assertTrue(offenders.isEmpty(), "raw Color(0x…) literals outside the theme package:\n" + offenders.joinToString("\n"))
    }

    @Test
    fun activityUsesAppTheme() {
        val activity = File(sourceRoot, "com/roadtrip/app/MainActivity.kt").readText()
        assertTrue("RoadTripTheme {" in activity, "MainActivity must wrap its content in RoadTripTheme")
        assertTrue(!Regex("""\bMaterialTheme\s*\{""").containsMatchIn(activity), "MainActivity must not use a bare MaterialTheme {}")
    }
}
