package com.roadtrip.core

/**
 * versionCode derivation from the release-please-managed version.txt (ANDREL-001).
 * Must stay in sync with the formula in app/build.gradle.kts.
 */
object VersionCode {
    fun fromVersionName(versionName: String): Int {
        val parts = versionName.trim().split(".")
        require(parts.size == 3) { "versionName must be MAJOR.MINOR.PATCH, got '$versionName'" }
        val (major, minor, patch) = parts.map { it.toInt() }
        require(minor in 0..99 && patch in 0..99) { "minor/patch must fit two digits for monotonic codes" }
        return major * 10000 + minor * 100 + patch
    }
}
