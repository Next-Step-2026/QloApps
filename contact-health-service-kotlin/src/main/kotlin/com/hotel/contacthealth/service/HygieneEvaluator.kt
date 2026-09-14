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
import com.hotel.contacthealth.util.DateTimeParser

class HygieneEvaluator {

    fun evaluate(request: ContactEvaluationRequest, correlationId: String): ContactEvaluationResponse {
        request.validate()

        val refDate = DateTimeParser.parseDate(request.referenceDate, "reference_date")
            ?: throw IllegalArgumentException("Field 'reference_date' is required.")

        val lastVerifiedDate = DateTimeParser.parseDate(request.lastVerifiedAt, "last_verified_at")
        val consentExpiresDate = DateTimeParser.parseDate(request.consentExpiresAt, "consent_expires_at")

        val staleness = StalenessCalculator.calculateDate(lastVerifiedDate, refDate)
        val consent = ConsentValidator.validateDate(consentExpiresDate, refDate)

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

    private fun buildEmailFactor(email: String, staleness: StalenessCalculator.StalenessResult): FactorEvaluation =
        buildFactor(
            type = FactorType.EMAIL,
            value = email,
            isValid = FormatValidators.isValidEmail(email),
            invalidIssue = "INVALID_EMAIL_FORMAT",
            maskFn = FormatValidators::maskEmail,
            staleness = staleness
        )

    private fun buildPhoneFactor(phone: String, staleness: StalenessCalculator.StalenessResult): FactorEvaluation =
        buildFactor(
            type = FactorType.PHONE,
            value = phone,
            isValid = FormatValidators.isValidPhone(phone),
            invalidIssue = "INVALID_E164_PHONE_FORMAT",
            maskFn = FormatValidators::maskPhone,
            staleness = staleness
        )

    private fun buildFactor(
        type: FactorType,
        value: String,
        isValid: Boolean,
        invalidIssue: String,
        maskFn: (String) -> String,
        staleness: StalenessCalculator.StalenessResult
    ): FactorEvaluation {
        val status = if (!isValid) FactorStatus.INVALID_FORMAT else staleness.status
        val issues = listOfNotNull(if (!isValid) invalidIssue else staleness.issue)

        return FactorEvaluation(
            type = type,
            valueMasked = maskFn(value),
            status = status,
            daysSinceVerification = staleness.daysSince,
            issues = issues
        )
    }
}
