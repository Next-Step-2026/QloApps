package com.hotel.contacthealth

import java.time.LocalDate

object StalenessCalculator {

    data class StalenessResult(
        val daysSince: Long,
        val status: FactorStatus,
        val issue: String? = null
    )

    fun calculate(lastVerifiedAt: String?, refDate: LocalDate): StalenessResult {
        val lastVerifiedDate = parseDate(lastVerifiedAt)

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

    fun parseDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return runCatching {
            LocalDate.parse(raw.trim().take(10))
        }.getOrNull()
    }
}