package com.roadtrip.app.ui.theme

import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

/**
 * covers: AND-018 — no feature UI source hardcodes a raw `Color(0x…)` literal; raw colour values
 * live only in the theme package, and the activity wraps the UI in the app theme rather than a
 * bare `MaterialTheme`. Reads the sources directly (Gradle runs unit tests from the module dir).
 *
 * covers: AND-019 — no feature screen picks light or dark itself: `isSystemInDarkTheme` is read
 * only in the theme package, and the activity hands the device theme preference to the root theme.
 */
class NoRawColorLiteralsTest {

    private val sourceRoot = File("src/main/kotlin")
    private val themeDir = File(sourceRoot, "com/roadtrip/app/ui/theme")
    private val rawColorLiteral = Regex("""\bColor\(\s*0[xX]""")

    private val systemDarkRead = Regex("""\bisSystemInDarkTheme\b""")

    /** Lines matching [pattern] in sources outside the theme package, as `file:line: text`. */
    private fun offendersOutsideTheme(pattern: Regex): List<String> {
        val sources = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty(), "no sources found under ${sourceRoot.absolutePath}")
        return sources
            .filterNot { it.canonicalPath.startsWith(themeDir.canonicalPath + File.separator) }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    if (pattern.containsMatchIn(line)) "${file.relativeTo(sourceRoot)}:${i + 1}: ${line.trim()}" else null
                }
            }
    }

    @Test
    fun noRawColorLiteralsOutsideThemePackage() {
        val offenders = offendersOutsideTheme(rawColorLiteral)
        assertTrue(offenders.isEmpty(), "raw Color(0x…) literals outside the theme package:\n" + offenders.joinToString("\n"))
    }

    @Test
    fun systemDarkFlagIsReadOnlyInThemePackage() {
        val offenders = offendersOutsideTheme(systemDarkRead)
        assertTrue(offenders.isEmpty(), "isSystemInDarkTheme outside the theme package (use RoadTripTheme / ThemeResolver):\n" + offenders.joinToString("\n"))
        val theme = File(themeDir, "Theme.kt").readText()
        assertTrue("ThemeResolver.resolve(" in theme, "RoadTripTheme must decide light vs dark through ThemeResolver")
    }

    @Test
    fun activityUsesAppTheme() {
        val activity = File(sourceRoot, "com/roadtrip/app/MainActivity.kt").readText()
        assertTrue(Regex("""\bRoadTripTheme\s*\(\s*themeMode\s*=""").containsMatchIn(activity), "MainActivity must wrap its content in RoadTripTheme(themeMode = …)")
        assertTrue("settings.themeMode" in activity, "MainActivity must pass the device theme preference (ANDSET-007)")
        assertTrue(!Regex("""\bMaterialTheme\s*\{""").containsMatchIn(activity), "MainActivity must not use a bare MaterialTheme {}")
    }
}
