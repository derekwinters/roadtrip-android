package com.roadtrip.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerAddressTest {

    // ---- needsSetup gate (AND-014) --------------------------------------------------------

    @Test
    fun `fresh install with no stored url needs setup AND-014`() {
        // covers: AND-014 — a fresh install has no baked-in default, so the gate must trip.
        assertTrue(ServerAddress.needsSetup(null))
    }

    @Test
    fun `blank stored url needs setup AND-014`() {
        assertTrue(ServerAddress.needsSetup(""))
        assertTrue(ServerAddress.needsSetup("   "))
    }

    @Test
    fun `stored url skips setup on later launches AND-014`() {
        // covers: AND-014 — once configured the gate never reappears.
        assertFalse(ServerAddress.needsSetup("http://10.0.2.2:8080"))
    }

    // ---- validate / normalize (AND-015) ---------------------------------------------------

    @Test
    fun `blank input is rejected AND-015`() {
        // covers: AND-015 — the app must not proceed until a real address is entered.
        assertTrue(ServerAddress.validate("") is ServerAddressResult.Invalid)
        assertTrue(ServerAddress.validate("    ") is ServerAddressResult.Invalid)
    }

    @Test
    fun `address without a scheme is rejected AND-015`() {
        assertTrue(ServerAddress.validate("10.0.0.2:8080") is ServerAddressResult.Invalid)
    }

    @Test
    fun `garbage input is rejected AND-015`() {
        assertTrue(ServerAddress.validate("not a url") is ServerAddressResult.Invalid)
        assertTrue(ServerAddress.validate("ftp://host") is ServerAddressResult.Invalid)
    }

    @Test
    fun `well-formed http url is accepted and normalized AND-015`() {
        val result = ServerAddress.validate("http://10.0.2.2:8080")
        assertTrue(result is ServerAddressResult.Valid)
        assertEquals("http://10.0.2.2:8080", result.normalizedUrl)
    }

    @Test
    fun `https url is accepted AND-015`() {
        assertTrue(ServerAddress.validate("https://trip.example.com") is ServerAddressResult.Valid)
    }

    @Test
    fun `surrounding whitespace is trimmed when normalizing AND-015`() {
        // covers: AND-015 — normalize as the app already does (trim), no other rewriting.
        val result = ServerAddress.validate("  http://host:8080  ")
        assertTrue(result is ServerAddressResult.Valid)
        assertEquals("http://host:8080", result.normalizedUrl)
    }
}
