package com.roadtrip.app.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.roadtrip.core.settings.ThemeMode
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * covers: ANDSET-007 — the theme preference is one device-wide value in the local settings store:
 * it reads System before anything is set, a set is visible at once on the StateFlow the root theme
 * observes (so the app re-themes without restart), and it survives a restart (a fresh
 * [AppSettings] over the same DataStore reads it back).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppSettingsThemeTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** Wait for the async persists [AppSettings] launched on [scope]. */
    private fun awaitPersisted() = runBlocking { scope.coroutineContext.job.children.toList().joinAll() }

    @Test
    fun themePreferenceDefaultsToSystemAppliesAtOnceAndSurvivesRestart() {
        val settings = AppSettings(context, scope)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode.value)

        settings.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, settings.themeMode.value)
        awaitPersisted()
        assertEquals(ThemeMode.DARK, AppSettings(context, scope).themeMode.value)

        settings.setThemeMode(ThemeMode.LIGHT)
        awaitPersisted()
        assertEquals(ThemeMode.LIGHT, AppSettings(context, scope).themeMode.value)
    }
}
