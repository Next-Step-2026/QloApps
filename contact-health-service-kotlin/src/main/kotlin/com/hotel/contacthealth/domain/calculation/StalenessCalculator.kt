package com.hotel.contacthealth.domain.calculation

import com.hotel.contacthealth.model.FactorStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object StalenessCalculator {

    data class StalenessResult(
        val daysSince: Long,
        val status: FactorStatus,
        val issue: String? = null
    )

    fun calculate(lastVerifiedAt: String?, refDate: LocalDate): StalenessResult {
        val lastVerifiedDate = parseDate(lastVerifiedAt, "last_verified_at")

        val days = if (lastVerifiedDate != null) {
            (refDate.toEpochDay() - lastVerifiedDate.toEpochDay()).coerceAtLeast(0)
        } else {
            180L
        }

        return when {
            days > 90 -> StalenessResult(days, FactorStatus.STALE, "STALENESS_EXCEEDED_90_DAYS")
            days > 30 -> StalenessResult(days, FactorStatus.AGING, "STALENESS_EXCEEDED_30_DAYS")
            else -> StalenessResult(days, FactorStatus.FRESH, null)
        }
    }

    fun parseDate(raw: String?, fieldName: String = "data"): LocalDate? {
        if (raw.isNullOrBlank()) {
            return null
        }
        val trimmed = raw.trim()

        runCatching {
            return OffsetDateTime.parse(trimmed, DateTimeFormatter.ISO_DATE_TIME).toLocalDate()
        }
        runCatching {
            return Instant.parse(trimmed).atZone(ZoneOffset.UTC).toLocalDate()
        }
        runCatching {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate()
        }
        runCatching {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE)
        }

        throw IllegalArgumentException("Field '$fieldName' contains invalid date/timestamp: $raw")
    }
}
