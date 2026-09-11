package com.hotel.contacthealth.api

import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("Contact Evaluation Route Integration Tests")
class ContactEvaluationRouteTest {

    @Nested
    @DisplayName("Header and Media-Type Validations")
    inner class HeaderValidation {

        @Test
        @DisplayName("Should reject missing X-Correlation-ID with 400 Bad Request")
        fun testMissingCorrelationIdReturns400() = testApplication {
            withTestApp {
                val response = postEvaluation(payload = jsonPayload(), correlationId = null)
                assertEquals(HttpStatusCode.BadRequest, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains("INVALID_PAYLOAD"))
                assertTrue(body.contains("X-Correlation-ID"))
            }
        }

        @Test
        @DisplayName("Should reject blank X-Correlation-ID with 400 Bad Request")
        fun testBlankCorrelationIdReturns400() = testApplication {
            withTestApp {
                val response = postEvaluation(payload = jsonPayload(), correlationId = "   ")
                assertEquals(HttpStatusCode.BadRequest, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains("INVALID_PAYLOAD"))
                assertTrue(body.contains("X-Correlation-ID"))
            }
        }

        @Test
        @DisplayName("Should reject invalid UUIDv4 X-Correlation-ID with 400 Bad Request")
        fun testInvalidUuidFormatReturns400() = testApplication {
            withTestApp {
                val response = postEvaluation(payload = jsonPayload(), correlationId = "test-corr-12345")
                assertEquals(HttpStatusCode.BadRequest, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains("INVALID_PAYLOAD"))
                assertTrue(body.contains("UUIDv4"))
            }
        }

        @Test
        @DisplayName("Should reject non-JSON Content-Type with 415 Unsupported Media Type")
        fun testUnsupportedMediaType() = testApplication {
            withTestApp {
                val response = postEvaluation(payload = "test", contentType = ContentType.Text.Plain)
                assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
                assertTrue(response.bodyAsText().contains("UNSUPPORTED_MEDIA_TYPE"))
            }
        }
    }

    @Nested
    @DisplayName("Payload and Field Validations")
    inner class PayloadValidation {

        @Test
        @DisplayName("Should reject blank customer_id with 400 Bad Request")
        fun testBlankCustomerIdReturns400() = testApplication {
            withTestApp {
                val response = postEvaluation(payload = jsonPayload(customerId = "   "))
                assertEquals(HttpStatusCode.BadRequest, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains("INVALID_PAYLOAD"))
                assertTrue(body.contains("customer_id"))
            }
        }

        @Test
        @DisplayName("Should reject blank email with 400 Bad Request")
        fun testBlankEmailReturns400() = testApplication {
            withTestApp {
                val response = postEvaluation(payload = jsonPayload(email = "   "))
                assertEquals(HttpStatusCode.BadRequest, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains("INVALID_PAYLOAD"))
                assertTrue(body.contains("email"))
            }
        }

        @Test
        @DisplayName("Should reject blank phone with 400 Bad Request")
        fun testBlankPhoneReturns400() = testApplication {
            withTestApp {
                val response = postEvaluation(payload = jsonPayload(phone = "  "))
                assertEquals(HttpStatusCode.BadRequest, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains("INVALID_PAYLOAD"))
                assertTrue(body.contains("phone"))
            }
        }

        @Test
        @DisplayName("Should reject null customer_id with 400 Bad Request")
        fun testNullCustomerIdInJsonReturns400() = testApplication {
            withTestApp {
                val response = postEvaluation(payload = jsonPayload(customerId = null))
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("INVALID_PAYLOAD"))
            }
        }

        @Test
        @DisplayName("Should reject null email with 400 Bad Request")
        fun testNullEmailInJsonReturns400() = testApplication {
            withTestApp {
                val response = postEvaluation(payload = jsonPayload(email = null))
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("INVALID_PAYLOAD"))
            }
        }

        @Test
        @DisplayName("Should reject null phone with 400 Bad Request")
        fun testNullPhoneInJsonReturns400() = testApplication {
            withTestApp {
                val response = postEvaluation(payload = jsonPayload(phone = null))
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("INVALID_PAYLOAD"))
            }
        }

        @Test
        @DisplayName("Should reject null reference_date with 400 Bad Request")
        fun testNullReferenceDateInJsonReturns400() = testApplication {
            withTestApp {
                val response = postEvaluation(payload = jsonPayload(referenceDate = null))
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("INVALID_PAYLOAD"))
            }
        }

        @Test
        @DisplayName("Should reject missing required JSON fields with 400 Bad Request")
        fun testMissingRequiredFieldsInJsonReturns400() = testApplication {
            withTestApp {
                val payload = """{"email": "user@test.com", "phone": "+5511999991111"}"""
                val response = postEvaluation(payload = payload)
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("INVALID_PAYLOAD"))
            }
        }

        @Test
        @DisplayName("Should reject invalid date format with 400 Bad Request")
        fun testInvalidDateReturns400() = testApplication {
            withTestApp {
                val response = postEvaluation(payload = jsonPayload(referenceDate = "invalid-reference-date"))
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("INVALID_PAYLOAD"))
            }
        }

        @Test
        @DisplayName("Should reject incompatible array type for reference_date with 400 Bad Request")
        fun testIncompatibleTypeForReferenceDateReturns400() = testApplication {
            withTestApp {
                val payload = """{"customer_id":"c1","email":"u@t.com","phone":"+5511999991111","reference_date":["2026","08","27"]}"""
                val response = postEvaluation(payload = payload)
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("INVALID_PAYLOAD"))
            }
        }

        @Test
        @DisplayName("Should reject incompatible object type for email with 400 Bad Request")
        fun testIncompatibleTypeForEmailReturns400() = testApplication {
            withTestApp {
                val payload = """{"customer_id":"c1","email":{"address":"test@test.com"},"phone":"+5511999991111","reference_date":"2026-08-27"}"""
                val response = postEvaluation(payload = payload)
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("INVALID_PAYLOAD"))
            }
        }

        @Test
        @DisplayName("Should reject last_verified_at in the future with 400 Bad Request")
        fun testFutureLastVerifiedAtReturns400() = testApplication {
            withTestApp {
                val response = postEvaluation(payload = jsonPayload(lastVerifiedAt = "2026-09-10T10:00:00Z"))
                assertEquals(HttpStatusCode.BadRequest, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains("INVALID_PAYLOAD"))
                assertTrue(body.contains("future"))
            }
        }
    }

    @Nested
    @DisplayName("Business Evaluation Scenarios (HTTP 200)")
    inner class EvaluationScenarios {

        @Test
        @DisplayName("Valid JSON should return 200 OK and propagate correlation ID")
        fun testSuccessfulEvaluationWithCorrelationId() = testApplication {
            withTestApp {
                val correlationId = "a1b2c3d4-e5f6-4a1b-8c2d-0123456789ab"
                val response = postEvaluation(payload = jsonPayload(), correlationId = correlationId)
                assertEquals(HttpStatusCode.OK, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains(""""correlation_id":"$correlationId""""))
                assertTrue(body.contains(""""days_since_verification""""))
                assertTrue(body.contains(""""issues""""))
            }
        }

        @Test
        @DisplayName("Should accept MySQL datetime format with space sent by PHP in last_verified_at with 200 OK")
        fun testMysqlDatetimeFormatFromPhpReturns200() = testApplication {
            withTestApp {
                val payload = jsonPayload(lastVerifiedAt = "2026-08-20 10:00:00Z")
                val response = postEvaluation(payload = payload)
                assertEquals(HttpStatusCode.OK, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains(""""overall_status""""))
            }
        }

        @Test
        @DisplayName("Missing consent_expires_at field in JSON should return 200 with CONSENT_EXPIRED")
        fun testMissingConsentExpiresAtInJsonReturnsConsentExpired() = testApplication {
            withTestApp {
                val payload = """
                {
                    "customer_id": "cust-no-consent-json",
                    "email": "carlos.silva@empresa.com.br",
                    "phone": "+5511987654321",
                    "last_verified_at": "2026-08-20T10:00:00Z",
                    "reference_date": "2026-08-27"
                }
                """.trimIndent()
                val response = postEvaluation(payload = payload)
                assertEquals(HttpStatusCode.OK, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains(""""overall_status":"CONSENT_EXPIRED""""))
                assertTrue(body.contains(""""consent_valid":false"""))
                assertTrue(body.contains(""""hygiene_score":40"""))
                assertTrue(body.contains(""""recommended_action":"TRIGGER_BACKGROUND_RECONFIRMATION""""))
            }
        }

        @Test
        @DisplayName("Consent expiring on reference_date should return 200 with FRESH")
        fun testConsentExpiresAtSameAsReferenceDateReturnsFresh() = testApplication {
            withTestApp {
                val payload = jsonPayload(
                    consentExpiresAt = "2026-08-27",
                    referenceDate = "2026-08-27"
                )
                val response = postEvaluation(payload = payload)
                assertEquals(HttpStatusCode.OK, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains(""""overall_status":"FRESH""""))
                assertTrue(body.contains(""""consent_valid":true"""))
                assertTrue(body.contains(""""hygiene_score":100"""))
                assertTrue(body.contains(""""recommended_action":"NONE""""))
            }
        }

        @Test
        @DisplayName("Aging contact should return 200 with AGING and TRIGGER_BACKGROUND_RECONFIRMATION")
        fun testResponseConsistencyForAgingContact() = testApplication {
            withTestApp {
                val payload = jsonPayload(lastVerifiedAt = "2026-07-13")
                val response = postEvaluation(payload = payload)
                assertEquals(HttpStatusCode.OK, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains(""""overall_status":"AGING""""))
                assertTrue(body.contains(""""recommended_action":"TRIGGER_BACKGROUND_RECONFIRMATION""""))
                assertTrue(body.contains(""""hygiene_score":70"""))
            }
        }

        @Test
        @DisplayName("Invalid format contact should return 200 with INVALID_FORMAT and TRIGGER_BACKGROUND_RECONFIRMATION")
        fun testResponseConsistencyForInvalidFormatContact() = testApplication {
            withTestApp {
                val payload = jsonPayload(email = "user-invalid-email")
                val response = postEvaluation(payload = payload)
                assertEquals(HttpStatusCode.OK, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains(""""overall_status":"INVALID_FORMAT""""))
                assertTrue(body.contains(""""recommended_action":"TRIGGER_BACKGROUND_RECONFIRMATION""""))
                assertTrue(body.contains(""""hygiene_score":60"""))
            }
        }
    }
}
