import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// versionName comes from version.txt (release-please 'simple' strategy);
// versionCode is derived per ANDREL-001 and must match core VersionCode.kt.
// Guard: version.txt must be a plain release triple — a SemVer prerelease/build suffix
// (e.g. "1.2.1-rc.4" leaked by release automation) fails here with an actionable error
// instead of a cryptic NumberFormatException (ANDREL-001/ANDREL-004).
val versionText = rootProject.file("version.txt").readText().trim()
require(Regex("""^\d+\.\d+\.\d+$""").matches(versionText)) {
    "version.txt must be a plain MAJOR.MINOR.PATCH release version, got '$versionText'; " +
        "prerelease identifiers belong only in RC release names — ANDREL-001/ANDREL-004"
}
val (vMajor, vMinor, vPatch) = versionText.split(".").map { it.toInt() }

android {
    namespace = "com.roadtrip.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.roadtrip.app"
        minSdk = 26
        targetSdk = 35
        versionCode = vMajor * 10000 + vMinor * 100 + vPatch
        versionName = versionText
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug-signed release builds: releases attach to GitHub release notes only,
            // never a store (docs/spec/08-testing.md release engineering).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation("com.roadtrip:core")

    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.adaptive.navigation.suite)
    implementation(libs.compose.adaptive)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.osmdroid)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.work.testing)
    testImplementation(libs.okhttp.mockwebserver)
}
