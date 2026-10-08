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

// Stable release signing (ANDREL-005/006, docs/spec/08-testing.md "Release signing and
// in-place upgrades"). Inputs come from env vars (CI release workflow) or a git-ignored
// keystore.properties at the repo root (local). Without them, assembleRelease falls back to
// debug signing so PR CI and local builds keep working — unless
// ROADTRIP_REQUIRE_RELEASE_SIGNING=true (set by release.yml), which fails the build instead
// of silently producing an APK that can never upgrade in place.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingInput(env: String, property: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(property)?.takeIf { it.isNotBlank() }

val releaseSigningInputs = linkedMapOf(
    "ANDROID_KEYSTORE_PATH" to signingInput("ANDROID_KEYSTORE_PATH", "storeFile"),
    "ANDROID_KEYSTORE_PASSWORD" to signingInput("ANDROID_KEYSTORE_PASSWORD", "storePassword"),
    "ANDROID_KEY_ALIAS" to signingInput("ANDROID_KEY_ALIAS", "keyAlias"),
    "ANDROID_KEY_ALIAS_PASSWORD" to signingInput("ANDROID_KEY_ALIAS_PASSWORD", "keyPassword"),
)
val missingSigningInputs = releaseSigningInputs.filterValues { it == null }.keys
val hasReleaseSigning = missingSigningInputs.isEmpty()
val requireReleaseSigning = System.getenv("ROADTRIP_REQUIRE_RELEASE_SIGNING") == "true"
if (requireReleaseSigning && !hasReleaseSigning) {
    throw GradleException(
        "ROADTRIP_REQUIRE_RELEASE_SIGNING=true but release signing inputs are missing: " +
            "$missingSigningInputs — refusing to fall back to debug signing (ANDREL-005)"
    )
}
if (!hasReleaseSigning) {
    logger.warn(
        "Release signing inputs missing ($missingSigningInputs): assembleRelease will be " +
            "debug-signed and cannot upgrade a stable-signed install (ANDREL-006)."
    )
}

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

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseSigningInputs.getValue("ANDROID_KEYSTORE_PATH")!!)
                storePassword = releaseSigningInputs.getValue("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = releaseSigningInputs.getValue("ANDROID_KEY_ALIAS")
                keyPassword = releaseSigningInputs.getValue("ANDROID_KEY_ALIAS_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // ANDREL-007: the randomly-signed debug APK installs side-by-side with the
            // stable-signed release app instead of colliding with it.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            // Stable release key when signing inputs are present (ANDREL-005); otherwise a
            // debug-signed fallback for PR CI and local builds (ANDREL-006).
            signingConfig = signingConfigs.getByName(if (hasReleaseSigning) "release" else "debug")
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
