package com.hotel.contacthealth.service

import com.hotel.contacthealth.model.ContactEvaluationRequest
import com.hotel.contacthealth.model.FactorStatus
import com.hotel.contacthealth.model.FactorType
import com.hotel.contacthealth.model.RecommendedAction
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("Hygiene Evaluator Service Integration Tests")
class HygieneEvaluatorTest {

    private val evaluator = HygieneEvaluator()

    private fun createRequest(
        customerId: String = "cust-test",
        email: String = "user@test.com",
        phone: String = "+5511999991111",
        lastVerifiedAt: String? = "2026-08-20",
        consentExpiresAt: String? = "2027-01-01",
        referenceDate: String = "2026-08-27"
    ) = ContactEvaluationRequest(
        customerId = customerId,
        email = email,
        phone = phone,
        lastVerifiedAt = lastVerifiedAt,
        consentExpiresAt = consentExpiresAt,
        referenceDate = referenceDate
    )

    @Nested
    @DisplayName("RFC-003 Acceptance Fixtures (BDD Scenarios)")
    inner class AcceptanceFixtures {

        @Test
        @DisplayName("Fixture 1 - Recent contact and valid consent -> FRESH, score >= 90, action NONE")
        fun shouldEvaluateFreshContactWithValidConsent() {
            val request = createRequest(
                customerId = "cust-001",
                email = "marina.costa@tech.com",
                phone = "+5511991234567",
                lastVerifiedAt = "2026-08-15T10:00:00Z",
                consentExpiresAt = "2027-01-01T00:00:00Z"
            )

            val response = evaluator.evaluate(request, "test-01")
            assertEquals("cust-001", response.customerId)
            assertEquals(FactorStatus.FRESH, response.overallStatus)
            assertTrue(response.hygieneScore >= 90)
            assertEquals(true, response.consentValid)
            assertEquals(RecommendedAction.NONE, response.recommendedAction)

            val email = response.factors.first { it.type == FactorType.EMAIL }
            assertEquals(FactorStatus.FRESH, email.status)
            assertEquals("m***a@tech.com", email.valueMasked)
            assertEquals(12L, email.daysSinceVerification)

            val phone = response.factors.first { it.type == FactorType.PHONE }
            assertEquals(FactorStatus.FRESH, phone.status)
            assertEquals("+5511*****4567", phone.valueMasked)
            assertEquals(12L, phone.daysSinceVerification)
        }

        @Test
        @DisplayName("Fixture 2 - Contact outdated for more than 180 days -> STALE, score <= 70, action RECONFIRMATION")
        fun shouldEvaluateStaleContactOver90Days() {
            val request = createRequest(
                customerId = "cust-002",
                email = "joao.antigo@provedor.com.br",
                phone = "+5521988887777",
                lastVerifiedAt = "2026-01-10T10:00:00Z",
                consentExpiresAt = "2026-12-31T00:00:00Z"
            )

            val response = evaluator.evaluate(request, "test-02")
            assertEquals(FactorStatus.STALE, response.overallStatus)
            assertTrue(response.hygieneScore <= 70)
            assertEquals(true, response.consentValid)
            assertEquals(RecommendedAction.TRIGGER_BACKGROUND_RECONFIRMATION, response.recommendedAction)

            val email = response.factors.first { it.type == FactorType.EMAIL }
            assertEquals(FactorStatus.STALE, email.status)
            assertTrue(email.issues.contains("STALENESS_EXCEEDED_90_DAYS"))

            val phone = response.factors.first { it.type == FactorType.PHONE }
            assertEquals(FactorStatus.STALE, phone.status)
            assertTrue(phone.issues.contains("STALENESS_EXCEEDED_90_DAYS"))
        }

        @Test
        @DisplayName("Fixture 3 - Expired consent -> CONSENT_EXPIRED, score <= 40, action RECONFIRMATION")
        fun shouldCapScoreAt40WhenConsentExpired() {
            val request = createRequest(
                customerId = "cust-003",
                email = "paulo.silva@empresa.com",
                phone = "+5531977776666",
                lastVerifiedAt = "2026-08-20T10:00:00Z",
                consentExpiresAt = "2026-06-01T00:00:00Z"
            )

            val response = evaluator.evaluate(request, "test-03")
            assertEquals(FactorStatus.CONSENT_EXPIRED, response.overallStatus)
            assertEquals(false, response.consentValid)
            assertTrue(response.hygieneScore <= 40)
            assertEquals(RecommendedAction.TRIGGER_BACKGROUND_RECONFIRMATION, response.recommendedAction)
        }
    }

    @Nested
    @DisplayName("Lifecycle Status and Action Matrix Consistency")
    inner class StatusAndActionMatrix {

        @Test
        @DisplayName("Overall status and recommended_action must match for all 5 lifecycle states")
        fun shouldVerifyConsistencyBetweenOverallStatusAndRecommendedAction() {
            val cases = listOf(
                createRequest(lastVerifiedAt = "2026-08-20") to (FactorStatus.FRESH to RecommendedAction.NONE),
                createRequest(lastVerifiedAt = "2026-07-13") to (FactorStatus.AGING to RecommendedAction.TRIGGER_BACKGROUND_RECONFIRMATION),
                createRequest(lastVerifiedAt = "2026-01-01") to (FactorStatus.STALE to RecommendedAction.TRIGGER_BACKGROUND_RECONFIRMATION),
                createRequest(email = "bad-email") to (FactorStatus.INVALID_FORMAT to RecommendedAction.TRIGGER_BACKGROUND_RECONFIRMATION),
                createRequest(consentExpiresAt = "2026-01-01") to (FactorStatus.CONSENT_EXPIRED to RecommendedAction.TRIGGER_BACKGROUND_RECONFIRMATION)
            )

            cases.forEach { (request, expected) ->
                val (expectedStatus, expectedAction) = expected
                val response = evaluator.evaluate(request, "test-matrix")
                assertEquals(expectedStatus, response.overallStatus)
                assertEquals(expectedAction, response.recommendedAction)
            }
        }
    }

    @Nested
    @DisplayName("Contract Input Validations")
    inner class ContractValidation {

        @Test
        @DisplayName("Blank required fields must throw IllegalArgumentException")
        fun shouldThrowOnBlankRequiredFields() {
            assertThrows<IllegalArgumentException> { evaluator.evaluate(createRequest(customerId = "   "), "t1") }
            assertThrows<IllegalArgumentException> { evaluator.evaluate(createRequest(email = ""), "t2") }
            assertThrows<IllegalArgumentException> { evaluator.evaluate(createRequest(phone = "  "), "t3") }
            assertThrows<IllegalArgumentException> { evaluator.evaluate(createRequest(referenceDate = "   "), "t4") }
        }

        @Test
        @DisplayName("Invalid date formats must throw IllegalArgumentException")
        fun shouldThrowOnInvalidDateFormat() {
            assertThrows<IllegalArgumentException> { evaluator.evaluate(createRequest(referenceDate = "invalid-date"), "t1") }
            assertThrows<IllegalArgumentException> { evaluator.evaluate(createRequest(lastVerifiedAt = "2026/08/27"), "t2") }
            assertThrows<IllegalArgumentException> { evaluator.evaluate(createRequest(consentExpiresAt = "invalid-consent-date"), "t3") }
        }

        @Test
        @DisplayName("Future last_verified_at relative to reference_date must throw IllegalArgumentException")
        fun shouldThrowOnFutureLastVerifiedAt() {
            val exception = assertThrows<IllegalArgumentException> {
                evaluator.evaluate(createRequest(lastVerifiedAt = "2026-08-28", referenceDate = "2026-08-27"), "t5")
            }
            assertTrue(exception.message!!.contains("future"))
        }
    }
}
