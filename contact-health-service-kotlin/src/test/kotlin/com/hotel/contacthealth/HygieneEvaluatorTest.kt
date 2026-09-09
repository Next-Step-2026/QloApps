package com.hotel.contacthealth

import com.hotel.contacthealth.model.ContactEvaluationRequest
import com.hotel.contacthealth.model.FactorStatus
import com.hotel.contacthealth.model.FactorType
import com.hotel.contacthealth.model.RecommendedAction
import com.hotel.contacthealth.service.HygieneEvaluator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HygieneEvaluatorTest {

    private val evaluator = HygieneEvaluator()

    @Test
    @DisplayName("Fixture 1 - Recent contact and valid consent should have FRESH status, score >= 90 and NONE action")
    fun shouldEvaluateFreshContactWithValidConsent() {
        val request = ContactEvaluationRequest(
            customerId = "cust-001",
            email = "marina.costa@tech.com",
            phone = "+5511991234567",
            lastVerifiedAt = "2026-08-15T10:00:00Z",
            consentExpiresAt = "2027-01-01T00:00:00Z",
            referenceDate = "2026-08-27"
        )

        val response = evaluator.evaluate(request, "test-01")

        assertEquals("test-01", response.correlationId)
        assertEquals("cust-001", response.customerId)
        assertEquals(FactorStatus.FRESH, response.overallStatus)
        assertTrue(response.hygieneScore >= 90)
        assertTrue(response.consentValid)
        assertEquals(RecommendedAction.NONE, response.recommendedAction)

        val emailFactor = response.factors.first { it.type == FactorType.EMAIL }
        assertEquals(FactorStatus.FRESH, emailFactor.status)
        assertEquals("m***a@tech.com", emailFactor.valueMasked)
        assertEquals(12L, emailFactor.daysSinceVerification)

        val phoneFactor = response.factors.first { it.type == FactorType.PHONE }
        assertEquals(FactorStatus.FRESH, phoneFactor.status)
        assertEquals("+5511*****4567", phoneFactor.valueMasked)
        assertEquals(12L, phoneFactor.daysSinceVerification)
    }

    @Test
    @DisplayName("Fixture 2 - Contact outdated for more than 180 days should be STALE and suggest reconfirmation")
    fun shouldEvaluateStaleContactOver90Days() {
        val request = ContactEvaluationRequest(
            customerId = "cust-002",
            email = "joao.antigo@provedor.com.br",
            phone = "+5521988887777",
            lastVerifiedAt = "2026-01-10T10:00:00Z",
            consentExpiresAt = "2026-12-31T00:00:00Z",
            referenceDate = "2026-08-27"
        )

        val response = evaluator.evaluate(request, "test-02")

        assertEquals(FactorStatus.STALE, response.overallStatus)
        assertTrue(response.hygieneScore <= 70)
        assertTrue(response.consentValid)
        assertEquals(RecommendedAction.TRIGGER_BACKGROUND_RECONFIRMATION, response.recommendedAction)

        val emailFactor = response.factors.first { it.type == FactorType.EMAIL }
        assertEquals(FactorStatus.STALE, emailFactor.status)
        assertTrue(emailFactor.issues.contains("STALENESS_EXCEEDED_90_DAYS"))

        val phoneFactor = response.factors.first { it.type == FactorType.PHONE }
        assertEquals(FactorStatus.STALE, phoneFactor.status)
        assertTrue(phoneFactor.issues.contains("STALENESS_EXCEEDED_90_DAYS"))
    }

    @Test
    @DisplayName("Fixture 3 - Expired consent should cap score at 40 and set status CONSENT_EXPIRED")
    fun shouldCapScoreAt40WhenConsentExpired() {
        val request = ContactEvaluationRequest(
            customerId = "cust-003",
            email = "paulo.silva@empresa.com",
            phone = "+5531977776666",
            lastVerifiedAt = "2026-08-20T10:00:00Z",
            consentExpiresAt = "2026-06-01T00:00:00Z",
            referenceDate = "2026-08-27"
        )

        val response = evaluator.evaluate(request, "test-03")

        assertEquals(FactorStatus.CONSENT_EXPIRED, response.overallStatus)
        assertFalse(response.consentValid)
        assertTrue(response.hygieneScore <= 40)
        assertEquals(RecommendedAction.TRIGGER_BACKGROUND_RECONFIRMATION, response.recommendedAction)
    }

    @Test
    @DisplayName("Boundary test - Exactly 30 days should be FRESH without penalty")
    fun shouldClassify30DaysAsFresh() {
        val request = ContactEvaluationRequest(
            customerId = "cust-boundary-30",
            email = "user@test.com",
            phone = "+5511999991111",
            lastVerifiedAt = "2026-07-28",
            referenceDate = "2026-08-27"
        )

        val response = evaluator.evaluate(request, "test-30")

        assertEquals(FactorStatus.FRESH, response.overallStatus)
        assertEquals(100, response.hygieneScore)
        assertEquals(30L, response.factors[0].daysSinceVerification)
    }

    @Test
    @DisplayName("Boundary test - Exactly 31 days should be AGING with 15 points penalty each factor")
    fun shouldClassify31DaysAsAging() {
        val request = ContactEvaluationRequest(
            customerId = "cust-boundary-31",
            email = "user@test.com",
            phone = "+5511999991111",
            lastVerifiedAt = "2026-07-27",
            referenceDate = "2026-08-27"
        )

        val response = evaluator.evaluate(request, "test-31")

        assertEquals(FactorStatus.AGING, response.overallStatus)
        assertEquals(70, response.hygieneScore) // 100 - 15 (email) - 15 (phone)
        assertEquals(31L, response.factors[0].daysSinceVerification)
        assertTrue(response.factors[0].issues.contains("STALENESS_EXCEEDED_30_DAYS"))
    }

    @Test
    @DisplayName("Boundary test - Exactly 90 days should be AGING")
    fun shouldClassify90DaysAsAging() {
        val request = ContactEvaluationRequest(
            customerId = "cust-boundary-90",
            email = "user@test.com",
            phone = "+5511999991111",
            lastVerifiedAt = "2026-05-29",
            referenceDate = "2026-08-27"
        )

        val response = evaluator.evaluate(request, "test-90")

        assertEquals(FactorStatus.AGING, response.overallStatus)
        assertEquals(70, response.hygieneScore)
        assertEquals(90L, response.factors[0].daysSinceVerification)
        assertTrue(response.factors[0].issues.contains("STALENESS_EXCEEDED_30_DAYS"))
    }

    @Test
    @DisplayName("Boundary test - Exactly 91 days should be STALE with 30 points penalty each factor")
    fun shouldClassify91DaysAsStale() {
        val request = ContactEvaluationRequest(
            customerId = "cust-boundary-91",
            email = "user@test.com",
            phone = "+5511999991111",
            lastVerifiedAt = "2026-05-28",
            referenceDate = "2026-08-27"
        )

        val response = evaluator.evaluate(request, "test-91")

        assertEquals(FactorStatus.STALE, response.overallStatus)
        assertEquals(40, response.hygieneScore) // 100 - 30 (email) - 30 (phone)
        assertEquals(91L, response.factors[0].daysSinceVerification)
        assertTrue(response.factors[0].issues.contains("STALENESS_EXCEEDED_90_DAYS"))
    }

    @Test
    @DisplayName("Null last_verified_at should default to 180 days and STALE")
    fun shouldDefaultTo180DaysAndStaleWhenLastVerifiedAtIsNull() {
        val request = ContactEvaluationRequest(
            customerId = "cust-null-verified",
            email = "user@test.com",
            phone = "+5511999991111",
            lastVerifiedAt = null,
            referenceDate = "2026-08-27"
        )

        val response = evaluator.evaluate(request, "test-null")

        assertEquals(FactorStatus.STALE, response.overallStatus)
        assertEquals(180L, response.factors[0].daysSinceVerification)
    }

    @Test
    @DisplayName("Phone with spaces and hyphens should be normalized and accepted as valid E.164")
    fun shouldAcceptAndNormalizePhoneWithSpacesAndHyphens() {
        val request = ContactEvaluationRequest(
            customerId = "cust-clean-phone",
            email = "user@test.com",
            phone = "+55 11 99123-4567",
            lastVerifiedAt = "2026-08-20",
            referenceDate = "2026-08-27"
        )

        val response = evaluator.evaluate(request, "test-clean-phone")

        val phoneFactor = response.factors.first { it.type == FactorType.PHONE }
        assertEquals(FactorStatus.FRESH, phoneFactor.status)
        assertFalse(phoneFactor.issues.contains("INVALID_E164_PHONE_FORMAT"))
    }

    @Test
    @DisplayName("Invalid email and phone format should result in INVALID_FORMAT with correct penalties")
    fun shouldPenalizeInvalidEmailAndPhoneFormat() {
        val request = ContactEvaluationRequest(
            customerId = "cust-invalid-fmt",
            email = "invalid-email-address",
            phone = "11999994444", // Missing leading + and country code
            lastVerifiedAt = "2026-08-20",
            referenceDate = "2026-08-27"
        )

        val response = evaluator.evaluate(request, "test-invalid-fmt")

        assertEquals(FactorStatus.INVALID_FORMAT, response.overallStatus)
        assertEquals(30, response.hygieneScore) // 100 - 40 (invalid email) - 30 (invalid phone)

        val emailFactor = response.factors.first { it.type == FactorType.EMAIL }
        assertEquals(FactorStatus.INVALID_FORMAT, emailFactor.status)
        assertTrue(emailFactor.issues.contains("INVALID_EMAIL_FORMAT"))

        val phoneFactor = response.factors.first { it.type == FactorType.PHONE }
        assertEquals(FactorStatus.INVALID_FORMAT, phoneFactor.status)
        assertTrue(phoneFactor.issues.contains("INVALID_E164_PHONE_FORMAT"))
    }

    @Test
    @DisplayName("Blank required fields must throw IllegalArgumentException")
    fun shouldThrowOnBlankRequiredFields() {
        assertThrows<IllegalArgumentException> {
            evaluator.evaluate(
                ContactEvaluationRequest(
                    customerId = "   ",
                    email = "user@test.com",
                    phone = "+5511999991111",
                    referenceDate = "2026-08-27"
                ),
                "test-blank-1"
            )
        }

        assertThrows<IllegalArgumentException> {
            evaluator.evaluate(
                ContactEvaluationRequest(
                    customerId = "cust-1",
                    email = "",
                    phone = "+5511999991111",
                    referenceDate = "2026-08-27"
                ),
                "test-blank-2"
            )
        }

        assertThrows<IllegalArgumentException> {
            evaluator.evaluate(
                ContactEvaluationRequest(
                    customerId = "cust-1",
                    email = "user@test.com",
                    phone = "  ",
                    referenceDate = "2026-08-27"
                ),
                "test-blank-3"
            )
        }

        assertThrows<IllegalArgumentException> {
            evaluator.evaluate(
                ContactEvaluationRequest(
                    customerId = "cust-1",
                    email = "user@test.com",
                    phone = "+5511999991111",
                    referenceDate = "   "
                ),
                "test-blank-4"
            )
        }
    }

    @Test
    @DisplayName("Invalid date formats must throw IllegalArgumentException")
    fun shouldThrowOnInvalidDateFormat() {
        assertThrows<IllegalArgumentException> {
            evaluator.evaluate(
                ContactEvaluationRequest(
                    customerId = "cust-invalid-date",
                    email = "user@test.com",
                    phone = "+5511999991111",
                    referenceDate = "invalid-date"
                ),
                "test-invalid-date"
            )
        }

        assertThrows<IllegalArgumentException> {
            evaluator.evaluate(
                ContactEvaluationRequest(
                    customerId = "cust-invalid-date-2",
                    email = "user@test.com",
                    phone = "+5511999991111",
                    lastVerifiedAt = "2026/08/27",
                    referenceDate = "2026-08-27"
                ),
                "test-invalid-date-2"
            )
        }
    }

    @Test
    @DisplayName("Score should be clamped between 0 and 100")
    fun shouldClampScoreBetween0And100() {
        val request = ContactEvaluationRequest(
            customerId = "cust-worst",
            email = "bad-email",
            phone = "bad-phone",
            lastVerifiedAt = "2020-01-01",
            consentExpiresAt = "2021-01-01",
            referenceDate = "2026-08-27"
        )

        val response = evaluator.evaluate(request, "test-worst")

        assertTrue(response.hygieneScore in 0..100)
    }
}