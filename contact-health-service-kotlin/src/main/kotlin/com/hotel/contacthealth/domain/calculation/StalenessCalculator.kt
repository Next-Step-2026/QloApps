package com.hotel.contacthealth.domain.calculation

import com.hotel.contacthealth.model.FactorStatus
import com.hotel.contacthealth.util.DateTimeParser
import java.time.LocalDate

object StalenessCalculator {

    data class StalenessResult(
        val daysSince: Long,
        val status: FactorStatus,
        val issue: String? = null
    )

    fun calculateDate(lastVerifiedDate: LocalDate?, refDate: LocalDate): StalenessResult {
        if (lastVerifiedDate == null) {
            return StalenessResult(180L, FactorStatus.STALE, "STALENESS_EXCEEDED_90_DAYS")
        }

        require(!lastVerifiedDate.isAfter(refDate)) {
            "Field 'last_verified_at' cannot be in the future relative to 'reference_date'."
        }

        val days = refDate.toEpochDay() - lastVerifiedDate.toEpochDay()

        return when {
            days > 90 -> StalenessResult(days, FactorStatus.STALE, "STALENESS_EXCEEDED_90_DAYS")
            days > 30 -> StalenessResult(days, FactorStatus.AGING, "STALENESS_EXCEEDED_30_DAYS")
            else -> StalenessResult(days, FactorStatus.FRESH, null)
        }
    }

    fun calculate(lastVerifiedAt: String?, refDate: LocalDate): StalenessResult {
        val lastVerifiedDate = parseDate(lastVerifiedAt, "last_verified_at")
        return calculateDate(lastVerifiedDate, refDate)
    }

    fun parseDate(raw: String?, fieldName: String = "data"): LocalDate? =
        DateTimeParser.parseDate(raw, fieldName)
}
