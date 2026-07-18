package com.roadtrip.core

/**
 * versionCode derivation from the release-please-managed version.txt (ANDREL-001).
 * Must stay in sync with the formula and the version.txt guard in app/build.gradle.kts.
 */
object VersionCode {
    /** The only accepted shape: a plain MAJOR.MINOR.PATCH release triple (ANDREL-001). */
    private val RELEASE_VERSION = Regex("""^(\d+)\.(\d+)\.(\d+)$""")

    /**
     * SemVer with a prerelease/build suffix or a leading `v` (e.g. `1.2.1-rc.4`,
     * `1.2.1+hotfix`, `v1.2.1`): valid SemVer-ish input that must never reach
     * version.txt — RC versions live only in release titles/artifact names (ANDREL-004).
     */
    private val SEMVER_WITH_SUFFIX = Regex("""^v?\d+\.\d+\.\d+([-+].*)?$""")

    fun fromVersionName(versionName: String): Int {
        val trimmed = versionName.trim()
        val match = RELEASE_VERSION.matchEntire(trimmed)
        if (match == null) {
            require(!SEMVER_WITH_SUFFIX.matches(trimmed)) {
                "version.txt must be a plain MAJOR.MINOR.PATCH release version, got '$trimmed'; " +
                    "prerelease identifiers belong only in RC release names — ANDREL-001/ANDREL-004"
            }
            throw IllegalArgumentException("versionName must be MAJOR.MINOR.PATCH, got '$trimmed'")
        }
        val (major, minor, patch) = match.destructured.toList().map { it.toInt() }
        require(minor in 0..99 && patch in 0..99) { "minor/patch must fit two digits for monotonic codes" }
        return major * 10000 + minor * 100 + patch
    }
}
