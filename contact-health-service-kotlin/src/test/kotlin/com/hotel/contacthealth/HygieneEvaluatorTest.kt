// src/test/kotlin/com/hotel/contacthealth/HygieneEvaluatorTest.kt
package com.hotel.contacthealth

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HygieneEvaluatorTest {

    private val evaluator = HygieneEvaluator()

    @Test
    fun `Fixture 1 - Contato recente e consentimento valido deve ter status FRESH e score maior ou igual a 90`() {
        val request = ContactEvaluationRequest(
            customerId = "cust-001",
            email = "marina.costa@tech.com",
            phone = "+5511991234567",
            lastVerifiedAt = "2026-08-15T10:00:00Z",
            consentExpiresAt = "2027-01-01T00:00:00Z",
            referenceDate = "2026-08-27"
        )

        val response = evaluator.evaluate(request, "test-01")

        assertEquals("FRESH", response.overallStatus)
        assertTrue(response.hygieneScore >= 90)
        assertTrue(response.consentValid)
        assertEquals("NONE", response.recommendedAction)
    }

    @Test
    fun `Fixture 2 - Contato desatualizado ha mais de 180 dias deve ser STALE e sugerir reconfirmacao`() {
        val request = ContactEvaluationRequest(
            customerId = "cust-002",
            email = "joao.antigo@provedor.com.br",
            phone = "+5521988887777",
            lastVerifiedAt = "2026-01-10T10:00:00Z",
            consentExpiresAt = "2026-12-31T00:00:00Z",
            referenceDate = "2026-08-27"
        )

        val response = evaluator.evaluate(request, "test-02")

        assertEquals("STALE", response.overallStatus)
        assertTrue(response.hygieneScore <= 70)
        assertEquals("TRIGGER_BACKGROUND_RECONFIRMATION", response.recommendedAction)
    }

    @Test
    fun `Fixture 3 - Consentimento expirado deve limitar score a 40 e definir status CONSENT_EXPIRED`() {
        val request = ContactEvaluationRequest(
            customerId = "cust-003",
            email = "paulo.silva@empresa.com",
            phone = "+5531977776666",
            lastVerifiedAt = "2026-08-20T10:00:00Z",
            consentExpiresAt = "2026-06-01T00:00:00Z",
            referenceDate = "2026-08-27"
        )

        val response = evaluator.evaluate(request, "test-03")

        assertEquals("CONSENT_EXPIRED", response.overallStatus)
        assertFalse(response.consentValid)
        assertTrue(response.hygieneScore <= 40)
    }
}