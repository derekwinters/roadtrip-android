package com.roadtrip.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VersionCodeTest {

    @Test
    fun `derives monotonic versionCode from version name ANDREL-001`() {
        assertEquals(102, VersionCode.fromVersionName("0.1.2"))
        assertEquals(10000, VersionCode.fromVersionName("1.0.0"))
        assertEquals(21534, VersionCode.fromVersionName("2.15.34"))

        // Monotonic across typical release-please bump sequences.
        val sequence = listOf("0.1.0", "0.1.1", "0.2.0", "1.0.0", "1.0.1", "1.1.0", "2.0.0")
        val codes = sequence.map(VersionCode::fromVersionName)
        assertEquals(codes, codes.sorted())
        assertTrue(codes.toSet().size == codes.size)
    }

    @Test
    fun `rejects malformed or out-of-range versions ANDREL-001`() {
        assertFailsWith<IllegalArgumentException> { VersionCode.fromVersionName("1.0") }
        assertFailsWith<IllegalArgumentException> { VersionCode.fromVersionName("1.0.100") }
    }

    @Test
    fun `rejects SemVer prerelease suffix with actionable message ANDREL-001`() {
        // RC automation must never leak a prerelease version into version.txt; when it
        // does, the failure has to name the suffix and point at version.txt/release
        // automation instead of dying with a cryptic NumberFormatException.
        val e = assertFailsWith<IllegalArgumentException> {
            VersionCode.fromVersionName("1.2.1-rc.4")
        }
        val message = e.message.orEmpty()
        assertTrue("1.2.1-rc.4" in message, "message must quote the offending value: $message")
        assertTrue("version.txt" in message, "message must point at version.txt: $message")
        assertTrue("prerelease" in message, "message must name the prerelease suffix: $message")
        assertTrue("MAJOR.MINOR.PATCH" in message, "message must state the accepted shape: $message")
    }

    @Test
    fun `rejects build metadata and v-prefixed versions with the same guidance ANDREL-001`() {
        val buildMeta = assertFailsWith<IllegalArgumentException> {
            VersionCode.fromVersionName("1.2.1+hotfix")
        }
        assertTrue("version.txt" in buildMeta.message.orEmpty(), "got: ${buildMeta.message}")
        assertTrue("1.2.1+hotfix" in buildMeta.message.orEmpty(), "got: ${buildMeta.message}")

        val vPrefixed = assertFailsWith<IllegalArgumentException> {
            VersionCode.fromVersionName("v1.2.1")
        }
        assertTrue("version.txt" in vPrefixed.message.orEmpty(), "got: ${vPrefixed.message}")
        assertTrue("v1.2.1" in vPrefixed.message.orEmpty(), "got: ${vPrefixed.message}")
    }
}
