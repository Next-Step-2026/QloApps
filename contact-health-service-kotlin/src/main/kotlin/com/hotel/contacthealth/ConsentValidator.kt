package com.hotel.contacthealth

import java.time.LocalDate


object ConsentValidator{
    data class ConsentResult(
        val isValid: Boolean,
        val isExpired: Boolean
    )

    fun validate(consentExpiresAt: String?, refDate: LocalDate): ConsentResult{
        if(consentExpiresAt.isNullOrBlank()){
            return ConsentResult(isValid = true, isExpired = false)
        }
        val expiresDate = StalenessCalculator.parseDate(consentExpiresAt)
            ?: return ConsentResult(isValid = false, isExpired = true)

        val expired = expiresDate.isBefore(refDate)

        return ConsentResult(isValid = !expired, isExpired = expired)
    }
}