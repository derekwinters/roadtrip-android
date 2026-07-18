package com.roadtrip.core.common

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Injectable clock so all time-dependent logic is deterministic under test. */
interface Clock {
    fun now(): Instant
}

object SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}

/** Injectable id source; production uses random UUIDs (sync idempotency keys). */
interface IdGenerator {
    fun newId(): String
}

object UuidIdGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}

/** Profile role straight from the profile record (AND-003: never inferred from names). */
@Serializable
enum class Role {
    @SerialName("parent") PARENT,
    @SerialName("kid") KID,
}

/**
 * Device form factor. Layout adaptation keys off WindowSizeClass (AND-004); since the
 * tracker rework the tracker is available on every device class (ANDLOC-003).
 */
enum class DeviceClass { PHONE, TABLET }

/** ISO-8601 helpers for the wire format (`date-time` strings in the contract). */
object Timestamps {
    private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

    fun parse(value: String): Instant =
        try {
            Instant.parse(value)
        } catch (e: DateTimeParseException) {
            OffsetDateTime.parse(value).toInstant()
        }

    fun format(instant: Instant): String = instant.toString()

    /** "14:32"-style local time used for freshness labels (ANDMAP-004). */
    fun hhmm(instant: Instant, zone: ZoneId): String = HHMM.withZone(zone).format(instant)
}
