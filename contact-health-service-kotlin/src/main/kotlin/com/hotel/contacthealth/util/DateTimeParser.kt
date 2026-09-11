package com.hotel.contacthealth.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object DateTimeParser {
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun parseDate(raw: String?, fieldName: String = "data"): LocalDate? {
        if (raw.isNullOrBlank()) {
            return null
        }
        val trimmed = raw.trim()
        val normalized = trimmed.replace(WHITESPACE_REGEX, "T")

        runCatching {
            return OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_DATE_TIME).toLocalDate()
        }
        runCatching {
            return Instant.parse(normalized).atZone(ZoneOffset.UTC).toLocalDate()
        }
        runCatching {
            return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate()
        }
        runCatching {
            return LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE)
        }

        throw IllegalArgumentException("Field '$fieldName' contains invalid date/timestamp: $raw")
    }
}
