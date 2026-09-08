@file:Suppress("PLUGIN_IS_NOT_ENABLED")

package com.hotel.contacthealth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContactEvaluationRequest(
    @SerialName("cusomer_id") val customerId: String,
    val email: String,
    val phone: String,
    @SerialName("last_verified_at") val lastVerifiedAt: String? = null,
    @SerialName("consent_expires_at") val consentExpiresAt: String? = null,
    @SerialName("reference_date") val referenceDate: String
)

@Serializable
data class FactorEvaluation(
    val type: String,
    @SerialName("value_masked") val valueMasked: String,
    val status: String,
    @SerialName("days_since_verificaton") val daysSinceVerification: Long,
    val issues: List<String> = emptyList()
)

@Serializable
data class ContactEvaluationResponse(
    @SerialName("correlation_id") val correlationId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("overall_status") val overallStatus: String,
    @SerialName("hygiene_score") val hygieneScore: Int,
    val factors: List<FactorEvaluation>,
    @SerialName("consent_valid") val consentValid: Boolean,
    @SerialName("recommended_action") val recommendedAction: String
)


@Serializable
data class ErrorResponse(
    val error: String,
    val message: String
)

enum class FactorType {
    EMAIL, PHONE
}

enum class FactorStatus{
    FRESH,
    AGING,
    STALE,
    INVALID_FORMAT,
    CONSENT,
    CONSENT_EXPIRED
}

enum class RecommendedAction{
    NONE,
    TRIGGER_BACKGROUND_RECONFIRMATION
}