package com.roadtrip.app.variant

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * covers: ANDREL-007 — the debug build carries `applicationIdSuffix = ".debug"`, so the
 * randomly-signed debug APK attached to releases installs side-by-side with the stable-signed
 * release app instead of colliding with it (docs/spec/08-testing.md release signing).
 * `testDebugUnitTest` runs against the debug variant's merged manifest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DebugApplicationIdTest {

    @Test
    fun debugVariantInstallsUnderTheDotDebugApplicationId() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.roadtrip.app.debug", context.packageName)
    }
}
