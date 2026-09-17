package com.hotel.contacthealth.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContactEvaluationRequest(
    @SerialName("customer_id") val customerId: String,
    val email: String,
    val phone: String,
    @SerialName("last_verified_at") val lastVerifiedAt: String? = null,
    @SerialName("consent_expires_at") val consentExpiresAt: String? = null,
    @SerialName("reference_date") val referenceDate: String
) {
    fun validate() {
        require(customerId.isNotBlank()) { "Field 'customer_id' is required and cannot be blank." }
        require(email.isNotBlank()) { "Field 'email' is required and cannot be blank." }
        require(phone.isNotBlank()) { "Field 'phone' is required and cannot be blank." }
        require(referenceDate.isNotBlank()) { "Field 'reference_date' is required and cannot be blank." }
    }
}

@Serializable
data class FactorEvaluation(
    val type: FactorType,
    @SerialName("value_masked") val valueMasked: String,
    val status: FactorStatus,
    @SerialName("days_since_verification") val daysSinceVerification: Long,
    val issues: List<String> = emptyList()
)

@Serializable
data class ContactEvaluationResponse(
    @SerialName("correlation_id") val correlationId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("overall_status") val overallStatus: FactorStatus,
    @SerialName("hygiene_score") val hygieneScore: Int,
    val factors: List<FactorEvaluation>,
    @SerialName("consent_valid") val consentValid: Boolean,
    @SerialName("recommended_action") val recommendedAction: RecommendedAction
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String
)
