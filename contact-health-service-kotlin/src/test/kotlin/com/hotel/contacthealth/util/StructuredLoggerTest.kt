package com.hotel.contacthealth.util

import com.hotel.contacthealth.model.ContactEvaluationResponse
import com.hotel.contacthealth.model.FactorStatus
import com.hotel.contacthealth.model.RecommendedAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("StructuredLogger Observability Unit Tests (RFC-003 Section 12)")
class StructuredLoggerTest {

    @Test
    @DisplayName("Should output valid structured JSON matching RFC-003 Section 12 schema")
    fun shouldEmitValidStructuredJson() {
        val originalOut = StructuredLogger.outputStream
        val byteStream = ByteArrayOutputStream()
        StructuredLogger.outputStream = PrintStream(byteStream)

        try {
            val response = ContactEvaluationResponse(
                correlationId = "e3b0c442-98fc-1c14-9afb-4c8996fb9242",
                customerId = "cust-1042",
                overallStatus = FactorStatus.AGING,
                hygieneScore = 75,
                factors = emptyList(),
                consentValid = true,
                recommendedAction = RecommendedAction.TRIGGER_BACKGROUND_RECONFIRMATION
            )

            StructuredLogger.logEvaluation(
                correlationId = "e3b0c442-98fc-1c14-9afb-4c8996fb9242",
                customerId = "cust-1042",
                response = response,
                durationMs = 3.1234
            )

            val output = byteStream.toString().trim()
            assertTrue(output.isNotEmpty())

            val jsonElement = Json.parseToJsonElement(output).jsonObject
            assertEquals("INFO", jsonElement["level"]?.jsonPrimitive?.content)
            assertEquals("CONTACT_EVALUATED", jsonElement["event"]?.jsonPrimitive?.content)
            assertEquals("e3b0c442-98fc-1c14-9afb-4c8996fb9242", jsonElement["correlation_id"]?.jsonPrimitive?.content)
            assertEquals("cust-1042", jsonElement["customer_id"]?.jsonPrimitive?.content)
            assertEquals("AGING", jsonElement["overall_status"]?.jsonPrimitive?.content)
            assertEquals("75", jsonElement["hygiene_score"]?.jsonPrimitive?.content)
            assertEquals("3.12", jsonElement["duration_ms"]?.jsonPrimitive?.content)
            assertNotNull(jsonElement["timestamp"]?.jsonPrimitive?.content)
        } finally {
            StructuredLogger.outputStream = originalOut
        }
    }
}
