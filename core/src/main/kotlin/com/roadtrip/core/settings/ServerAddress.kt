package com.roadtrip.core.settings

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Pure logic for the first-run server-address setup gate (AND-014/AND-015).
 *
 * The app ships with **no** baked-in server address: a fresh install has nothing stored, so
 * [needsSetup] gates the whole UI behind an explicit "Enter server address" prompt before the
 * profile picker (AND-001) or any API call. [validate] is the single seam every entry point
 * (setup screen, pre-sign-in editor AND-008, Settings) persists through, so a malformed
 * address can never be stored.
 */
object ServerAddress {

    /**
     * True when no server address has been configured yet — a fresh install with no stored
     * `server_url` (null or blank). The app must show the setup prompt, never assume a
     * default host (AND-014).
     */
    fun needsSetup(stored: String?): Boolean = stored.isNullOrBlank()

    /**
     * Validates and normalizes a typed server address (AND-015): trims the input and accepts
     * only a well-formed absolute `http(s)` URL — exactly what the OkHttp client will accept
     * ([toHttpUrlOrNull]). Returns the normalized (trimmed) value to persist, or a
     * human-readable rejection reason. Nothing about a working `10.x` host is assumed here.
     */
    fun validate(input: String): ServerAddressResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return ServerAddressResult.Invalid("Enter the server address.")
        }
        // Parse only to validate; the persisted form is the trimmed input, matching how the
        // app already normalizes elsewhere (no trailing-slash rewriting surprises).
        trimmed.toHttpUrlOrNull()
            ?: return ServerAddressResult.Invalid(
                "That doesn't look like a valid address — include http:// or https:// and a host.",
            )
        return ServerAddressResult.Valid(trimmed)
    }
}

/** Outcome of [ServerAddress.validate]. */
sealed interface ServerAddressResult {
    /** A well-formed address; [normalizedUrl] is what to persist and hand to the API client. */
    data class Valid(val normalizedUrl: String) : ServerAddressResult

    /** A rejected address; [reason] is a human-readable message to show the user. */
    data class Invalid(val reason: String) : ServerAddressResult
}
