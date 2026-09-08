// src/main/kotlin/com/hotel/contacthealth/HygieneEvaluator.kt
package com.hotel.contacthealth

class HygieneEvaluator {

    fun evaluate(request: ContactEvaluationRequest, correlationId: String): ContactEvaluationResponse {
        val refDate = StalenessCalculator.parseDate(request.referenceDate)
            ?: throw IllegalArgumentException("Campo 'reference_date' inválido.")


        val staleness = StalenessCalculator.calculate(request.lastVerifiedAt, refDate)


        val consent = ConsentValidator.validate(request.consentExpiresAt, refDate)


        val emailFactor = buildEmailFactor(request.email, staleness)
        val phoneFactor = buildPhoneFactor(request.phone, staleness)


        val emailStatus = FactorStatus.valueOf(emailFactor.status)
        val phoneStatus = FactorStatus.valueOf(phoneFactor.status)
        val finalScore = ScoreCalculator.calculate(emailStatus, phoneStatus, consent.isValid)


        val overallStatus = when {
            !consent.isValid -> FactorStatus.CONSENT_EXPIRED.name
            emailStatus == FactorStatus.INVALID_FORMAT || phoneStatus == FactorStatus.INVALID_FORMAT -> FactorStatus.INVALID_FORMAT.name
            emailStatus == FactorStatus.STALE || phoneStatus == FactorStatus.STALE -> FactorStatus.STALE.name
            emailStatus == FactorStatus.AGING || phoneStatus == FactorStatus.AGING -> FactorStatus.AGING.name
            else -> FactorStatus.FRESH.name
        }

        val action = if (overallStatus != FactorStatus.FRESH.name) {
            RecommendedAction.TRIGGER_BACKGROUND_RECONFIRMATION.name
        } else {
            RecommendedAction.NONE.name
        }


        return ContactEvaluationResponse(
            correlationId = correlationId,
            customerId = request.customerId,
            overallStatus = overallStatus,
            hygieneScore = finalScore,
            factors = listOf(emailFactor, phoneFactor),
            consentValid = consent.isValid,
            recommendedAction = action
        )
    }

    private fun buildEmailFactor(email: String, staleness: StalenessCalculator.StalenessResult): FactorEvaluation {
        val isValid = FormatValidators.isValidEmail(email)
        val status = if (!isValid) FactorStatus.INVALID_FORMAT else staleness.status
        val issues = mutableListOf<String>()
        if (!isValid) issues.add("INVALID_EMAIL_FORMAT")
        staleness.issue?.let { if (isValid) issues.add(it) }

        return FactorEvaluation(
            type = FactorType.EMAIL.name,
            valueMasked = FormatValidators.maskEmail(email),
            status = status.name,
            daysSinceVerification = staleness.daysSince,
            issues = issues
        )
    }

    private fun buildPhoneFactor(phone: String, staleness: StalenessCalculator.StalenessResult): FactorEvaluation {
        val isValid = FormatValidators.isValidPhone(phone)
        val status = if (!isValid) FactorStatus.INVALID_FORMAT else staleness.status
        val issues = mutableListOf<String>()
        if (!isValid) issues.add("INVALID_E164_PHONE_FORMAT")
        staleness.issue?.let { if (isValid) issues.add(it) }

        return FactorEvaluation(
            type = FactorType.PHONE.name,
            valueMasked = FormatValidators.maskPhone(phone),
            status = status.name,
            daysSinceVerification = staleness.daysSince,
            issues = issues
        )
    }
}