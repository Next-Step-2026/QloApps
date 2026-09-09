package com.hotel.contacthealth

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    @DisplayName("GET /healthz should respond HTTP 200 OK with status UP")
    fun testHealthCheck() = testApplication {
        application {
            module()
        }

        val response = client.get("/healthz")

        assertEquals(HttpStatusCode.OK, response.status)
        val contentType = response.contentType()
        assertTrue(
            contentType?.match(ContentType.Application.Json) == true,
            "Expected Content-Type application/json, but was: $contentType"
        )
        assertEquals("""{"status":"UP"}""", response.bodyAsText().trim())
    }

    @Test
    @DisplayName("POST /v1/contact-evaluations with valid JSON should return 200 OK and propagate correlation ID")
    fun testSuccessfulEvaluationWithCorrelationId() = testApplication {
        application {
            module()
        }

        val correlationId = "test-corr-12345"
        val payload = """
        {
            "customer_id": "cust-1042",
            "email": "carlos.silva@empresa.com.br",
            "phone": "+5511987654321",
            "last_verified_at": "2026-04-10T10:00:00Z",
            "consent_expires_at": "2026-12-31T23:59:59Z",
            "reference_date": "2026-08-27"
        }
        """.trimIndent()

        val response = client.post("/v1/contact-evaluations") {
            contentType(ContentType.Application.Json)
            header("X-Correlation-ID", correlationId)
            setBody(payload)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()

        assertTrue(body.contains(""""correlation_id":"$correlationId""""))

        assertTrue(body.contains(""""days_since_verification""""))
        assertTrue(body.contains(""""issues""""))
    }

    @Test
    @DisplayName("POST /v1/contact-evaluations should generate UUID if X-Correlation-ID is missing")
    fun testMissingCorrelationIdGeneratesUUID() = testApplication {
        application {
            module()
        }

        val payload = """
        {
            "customer_id": "cust-1042",
            "email": "carlos.silva@empresa.com.br",
            "phone": "+5511987654321",
            "reference_date": "2026-08-27"
        }
        """.trimIndent()

        val response = client.post("/v1/contact-evaluations") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(""""correlation_id":"""))
    }

    @Test
    @DisplayName("POST /v1/contact-evaluations should reject non-JSON Content-Type with 415")
    fun testUnsupportedMediaType() = testApplication {
        application {
            module()
        }

        val response = client.post("/v1/contact-evaluations") {
            contentType(ContentType.Text.Plain)
            setBody("test")
        }

        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        assertTrue(response.bodyAsText().contains("UNSUPPORTED_MEDIA_TYPE"))
    }

    @Test
    @DisplayName("POST /v1/contact-evaluations with blank customer_id should return 400 Bad Request")
    fun testBlankCustomerIdReturns400() = testApplication {
        application {
            module()
        }

        val payload = """
        {
            "customer_id": "   ",
            "email": "user@test.com",
            "phone": "+5511999991111",
            "reference_date": "2026-08-27"
        }
        """.trimIndent()

        val response = client.post("/v1/contact-evaluations") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("INVALID_PAYLOAD"))
        assertTrue(body.contains("customer_id"))
    }

    @Test
    @DisplayName("POST /v1/contact-evaluations with invalid date format should return 400 Bad Request")
    fun testInvalidDateReturns400() = testApplication {
        application {
            module()
        }

        val payload = """
        {
            "customer_id": "cust-01",
            "email": "user@test.com",
            "phone": "+5511999991111",
            "reference_date": "invalid-reference-date"
        }
        """.trimIndent()

        val response = client.post("/v1/contact-evaluations") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("INVALID_PAYLOAD"))
    }
}
