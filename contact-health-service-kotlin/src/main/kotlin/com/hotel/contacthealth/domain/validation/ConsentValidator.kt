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
            return ConsentResult(isValid = false, isExpired = true)
        }
        val expiresDate = StalenessCalculator.parseDate(consentExpiresAt, "consent_expires_at")
            ?: return ConsentResult(isValid = false, isExpired = true)

        val expired = expiresDate.isBefore(refDate)

        return ConsentResult(isValid = !expired, isExpired = expired)
    }
}
