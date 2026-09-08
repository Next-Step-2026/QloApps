package com.hotel.contacthealth


object ScoreCalculator{
    fun calculate(
        emailStatus: FactorStatus,
        phoneStatus: FactorStatus,
        consentValid: Boolean
    ): Int{
        var score = 100

        when(emailStatus){
            FactorStatus.INVALID_FORMAT -> score -= 40
            FactorStatus.STALE -> score -= 30
            FactorStatus.AGING -> score -= 15
            else -> {}
        }

        when(phoneStatus){
            FactorStatus.INVALID_FORMAT -> score -= 30
            FactorStatus.STALE -> score -= 30
            FactorStatus.AGING -> score -= 15
            else -> {}
        }
        if(!consentValid){
            score = score.coerceAtMost(40)
        }

        return score.coerceIn(0, 100)
    }
}