package com.hotel.contacthealth.domain.validation

import com.hotel.contacthealth.util.DateTimeParser
import java.time.LocalDate

object ConsentValidator {
    data class ConsentResult(
        val isValid: Boolean,
        val isExpired: Boolean
    )

    fun validateDate(expiresDate: LocalDate?, refDate: LocalDate): ConsentResult {
        if (expiresDate == null) {
            return ConsentResult(isValid = false, isExpired = true)
        }
        val expired = expiresDate.isBefore(refDate)
        return ConsentResult(isValid = !expired, isExpired = expired)
    }

    fun validate(consentExpiresAt: String?, refDate: LocalDate): ConsentResult {
        if (consentExpiresAt.isNullOrBlank()) {
            return ConsentResult(isValid = false, isExpired = true)
        }
        val expiresDate = DateTimeParser.parseDate(consentExpiresAt, "consent_expires_at")
        return validateDate(expiresDate, refDate)
    }
}
