package com.hotel.contacthealth.domain.validation

import com.hotel.contacthealth.domain.calculation.StalenessCalculator
import java.time.LocalDate

object ConsentValidator {
    data class ConsentResult(
        val isValid: Boolean,
        val isExpired: Boolean
    )

    fun validate(consentExpiresAt: String?, refDate: LocalDate): ConsentResult {
        if (consentExpiresAt.isNullOrBlank()) {
            return ConsentResult(isValid = true, isExpired = false)
        }
        val expiresDate = StalenessCalculator.parseDate(consentExpiresAt, "consent_expires_at")
            ?: return ConsentResult(isValid = true, isExpired = false)

        val expired = expiresDate.isBefore(refDate)

        return ConsentResult(isValid = !expired, isExpired = expired)
    }
}
