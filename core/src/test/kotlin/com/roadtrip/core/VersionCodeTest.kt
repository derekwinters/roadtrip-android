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
}
