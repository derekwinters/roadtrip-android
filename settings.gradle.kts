pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "roadtrip-android"
include(":app")

// core is a standalone JVM build (see core/settings.gradle.kts for why);
// the app consumes it through composite-build substitution.
includeBuild("core") {
    dependencySubstitution {
        substitute(module("com.roadtrip:core")).using(project(":"))
    }
}
