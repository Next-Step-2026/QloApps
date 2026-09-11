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

        if (lastVerifiedDate != null) {
            require(!lastVerifiedDate.isAfter(refDate)) {
                "Field 'last_verified_at' cannot be in the future relative to 'reference_date'."
            }
        }

        val days = if (lastVerifiedDate != null) {
            refDate.toEpochDay() - lastVerifiedDate.toEpochDay()
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
        val normalized = trimmed.replace(Regex("\\s+"), "T")

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
