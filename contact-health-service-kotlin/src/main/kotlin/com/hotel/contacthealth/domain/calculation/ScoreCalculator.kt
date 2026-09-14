package com.hotel.contacthealth.domain.calculation

import com.hotel.contacthealth.model.FactorStatus

object ScoreCalculator {
    fun calculate(
        emailStatus: FactorStatus,
        phoneStatus: FactorStatus,
        consentValid: Boolean
    ): Int {
        val emailPenalty = when (emailStatus) {
            FactorStatus.INVALID_FORMAT -> 40
            FactorStatus.STALE -> 30
            FactorStatus.AGING -> 15
            else -> 0
        }

        val phonePenalty = when (phoneStatus) {
            FactorStatus.INVALID_FORMAT -> 30
            FactorStatus.STALE -> 30
            FactorStatus.AGING -> 15
            else -> 0
        }

        val baseScore = (100 - emailPenalty - phonePenalty).coerceIn(0, 100)
        return if (consentValid) baseScore else minOf(baseScore, 40)
    }
}
