package com.hotel.contacthealth.service

import com.hotel.contacthealth.domain.calculation.ScoreCalculator
import com.hotel.contacthealth.domain.calculation.StalenessCalculator
import com.hotel.contacthealth.domain.validation.ConsentValidator
import com.hotel.contacthealth.domain.validation.FormatValidators
import com.hotel.contacthealth.model.ContactEvaluationRequest
import com.hotel.contacthealth.model.ContactEvaluationResponse
import com.hotel.contacthealth.model.FactorEvaluation
import com.hotel.contacthealth.model.FactorStatus
import com.hotel.contacthealth.model.FactorType
import com.hotel.contacthealth.model.RecommendedAction

class HygieneEvaluator {

    fun evaluate(request: ContactEvaluationRequest, correlationId: String): ContactEvaluationResponse {
        request.validate()

        val refDate = StalenessCalculator.parseDate(request.referenceDate, "reference_date")
            ?: throw IllegalArgumentException("Field 'reference_date' is required.")

        val staleness = StalenessCalculator.calculate(request.lastVerifiedAt, refDate)
        val consent = ConsentValidator.validate(request.consentExpiresAt, refDate)

        val emailFactor = buildEmailFactor(request.email, staleness)
        val phoneFactor = buildPhoneFactor(request.phone, staleness)

        val finalScore = ScoreCalculator.calculate(emailFactor.status, phoneFactor.status, consent.isValid)

        val overallStatus = when {
            !consent.isValid -> FactorStatus.CONSENT_EXPIRED
            emailFactor.status == FactorStatus.INVALID_FORMAT || phoneFactor.status == FactorStatus.INVALID_FORMAT -> FactorStatus.INVALID_FORMAT
            emailFactor.status == FactorStatus.STALE || phoneFactor.status == FactorStatus.STALE -> FactorStatus.STALE
            emailFactor.status == FactorStatus.AGING || phoneFactor.status == FactorStatus.AGING -> FactorStatus.AGING
            else -> FactorStatus.FRESH
        }

        val action = if (overallStatus != FactorStatus.FRESH) {
            RecommendedAction.TRIGGER_BACKGROUND_RECONFIRMATION
        } else {
            RecommendedAction.NONE
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
            type = FactorType.EMAIL,
            valueMasked = FormatValidators.maskEmail(email),
            status = status,
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
            type = FactorType.PHONE,
            valueMasked = FormatValidators.maskPhone(phone),
            status = status,
            daysSinceVerification = staleness.daysSince,
            issues = issues
        )
    }
}
