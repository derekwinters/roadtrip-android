// The core module is a STANDALONE Gradle build, composite-included by the root build.
// Rationale: it is pure JVM and resolves everything from Maven Central / Plugin Portal,
// so its TDD loop (./gradlew -p core test) works even where Google's Android repos are
// unreachable; the :app module needs them and builds in CI.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "core"
