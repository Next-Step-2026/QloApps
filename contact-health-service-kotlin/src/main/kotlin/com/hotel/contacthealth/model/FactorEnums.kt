package com.hotel.contacthealth.model

import kotlinx.serialization.Serializable

@Serializable
enum class FactorType {
    EMAIL,
    PHONE
}

@Serializable
enum class FactorStatus {
    FRESH,
    AGING,
    STALE,
    INVALID_FORMAT,
    CONSENT_EXPIRED
}

@Serializable
enum class RecommendedAction {
    NONE,
    TRIGGER_BACKGROUND_RECONFIRMATION
}
