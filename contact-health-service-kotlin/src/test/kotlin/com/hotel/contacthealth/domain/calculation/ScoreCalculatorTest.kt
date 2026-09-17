package com.hotel.contacthealth.domain.calculation

import com.hotel.contacthealth.model.FactorStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("Score Calculator Domain Tests")
class ScoreCalculatorTest {

    @Nested
    @DisplayName("Factor Status Penalties")
    inner class FactorPenalties {

        @Test
        @DisplayName("Should return 100 for FRESH email and phone with valid consent")
        fun shouldReturn100ForFreshFactors() {
            val score = ScoreCalculator.calculate(
                emailStatus = FactorStatus.FRESH,
                phoneStatus = FactorStatus.FRESH,
                consentValid = true
            )
            assertEquals(100, score)
        }

        @Test
        @DisplayName("Should deduct 40 points for INVALID_FORMAT email")
        fun shouldDeduct40ForInvalidEmail() {
            val score = ScoreCalculator.calculate(
                emailStatus = FactorStatus.INVALID_FORMAT,
                phoneStatus = FactorStatus.FRESH,
                consentValid = true
            )
            assertEquals(60, score)
        }

        @Test
        @DisplayName("Should deduct 30 points for INVALID_FORMAT phone")
        fun shouldDeduct30ForInvalidPhone() {
            val score = ScoreCalculator.calculate(
                emailStatus = FactorStatus.FRESH,
                phoneStatus = FactorStatus.INVALID_FORMAT,
                consentValid = true
            )
            assertEquals(70, score)
        }

        @Test
        @DisplayName("Should deduct 15 points per AGING factor (30 total)")
        fun shouldDeduct15PerAgingFactor() {
            val score = ScoreCalculator.calculate(
                emailStatus = FactorStatus.AGING,
                phoneStatus = FactorStatus.AGING,
                consentValid = true
            )
            assertEquals(70, score)
        }

        @Test
        @DisplayName("Should deduct 30 points per STALE factor (60 total)")
        fun shouldDeduct30PerStaleFactor() {
            val score = ScoreCalculator.calculate(
                emailStatus = FactorStatus.STALE,
                phoneStatus = FactorStatus.STALE,
                consentValid = true
            )
            assertEquals(40, score)
        }

        @Test
        @DisplayName("Should combine deductions: INVALID_FORMAT email (-40) + AGING phone (-15) = 45")
        fun shouldCombineDeductionsCorrectly() {
            val score = ScoreCalculator.calculate(
                emailStatus = FactorStatus.INVALID_FORMAT,
                phoneStatus = FactorStatus.AGING,
                consentValid = true
            )
            assertEquals(45, score)
        }

        @Test
        @DisplayName("Worst format scenario: INVALID_FORMAT email (-40) + INVALID_FORMAT phone (-30) = 30")
        fun shouldCalculateWorstFormatScenario() {
            val score = ScoreCalculator.calculate(
                emailStatus = FactorStatus.INVALID_FORMAT,
                phoneStatus = FactorStatus.INVALID_FORMAT,
                consentValid = true
            )
            assertEquals(30, score)
        }
    }

    @Nested
    @DisplayName("LGPD Consent Capping and Clamping")
    inner class ConsentCapping {

        @Test
        @DisplayName("Should cap score of 100 at 40 when consent is invalid")
        fun shouldCap100At40WhenConsentInvalid() {
            val score = ScoreCalculator.calculate(
                emailStatus = FactorStatus.FRESH,
                phoneStatus = FactorStatus.FRESH,
                consentValid = false
            )
            assertEquals(40, score)
        }

        @Test
        @DisplayName("Should cap score of 70 at 40 when consent is invalid")
        fun shouldCap70At40WhenConsentInvalid() {
            val score = ScoreCalculator.calculate(
                emailStatus = FactorStatus.AGING,
                phoneStatus = FactorStatus.AGING,
                consentValid = false
            )
            assertEquals(40, score)
        }

        @Test
        @DisplayName("Should NOT increase score below 40 (e.g. 30) when consent is invalid")
        fun shouldPreserveScoreBelow40WhenConsentInvalid() {
            val score = ScoreCalculator.calculate(
                emailStatus = FactorStatus.INVALID_FORMAT,
                phoneStatus = FactorStatus.INVALID_FORMAT,
                consentValid = false
            )
            assertEquals(30, score)
        }

        @Test
        @DisplayName("Score should always be clamped between 0 and 100")
        fun shouldClampScoreBetween0And100() {
            val score = ScoreCalculator.calculate(
                emailStatus = FactorStatus.STALE,
                phoneStatus = FactorStatus.STALE,
                consentValid = false
            )
            assertTrue(score in 0..100)
        }
    }
}
