package com.hotel.location

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.hotel.location.dto.HealthResponse
import com.hotel.location.dto.LocationEventResponseDto
import com.hotel.location.dto.ProblemDetailsResponse
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class ApplicationIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val VALID_CORRELATION_ID = "a1b2c3d4-e5f6-4a8b-9c0d-1e2f3a4b5c6d"
    }

    @AfterEach
    fun tearDown() {
        resetServiceState()
    }

    private fun createLocationPayloadJson(
        hotelId: String? = "htl-recife-01",
        hotelLat: Double? = -8.052240,
        hotelLng: Double? = -34.885650,
        guestLat: Double? = -8.053100,
        guestLng: Double? = -34.886100,
        radius: Double? = 200.0,
        previousState: String? = "outside",
        omitField: String? = null
    ): String {
        val fields = mutableListOf<String>()
        if (omitField != "hotel_id") fields.add("\"hotel_id\": ${hotelId?.let { "\"$it\"" } ?: "null"}")
        if (omitField != "hotel_lat") fields.add("\"hotel_lat\": ${hotelLat ?: "null"}")
        if (omitField != "hotel_lng") fields.add("\"hotel_lng\": ${hotelLng ?: "null"}")
        if (omitField != "guest_lat") fields.add("\"guest_lat\": ${guestLat ?: "null"}")
        if (omitField != "guest_lng") fields.add("\"guest_lng\": ${guestLng ?: "null"}")
        if (omitField != "geofence_radius_m") fields.add("\"geofence_radius_m\": ${radius ?: "null"}")
        if (omitField != "previous_state") fields.add("\"previous_state\": ${previousState?.let { "\"$it\"" } ?: "null"}")
        return fields.joinToString(prefix = "{\n  ", postfix = "\n}", separator = ",\n  ")
    }

    @Nested
    @DisplayName("Success and Geofencing Scenarios")
    inner class GeofencingScenarios {

        @Test
        fun `should respond 200 OK on healthcheck`() = testApplication {
            application { module() }

            val response = client.get("/healthz")
            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `should respond 200 OK for valid location event`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.decodeFromString<LocationEventResponseDto>(response.bodyAsText())
            assertEquals(VALID_CORRELATION_ID, body.correlation_id)
            assertEquals("htl-recife-01", body.hotel_id)
            assertEquals("inside", body.current_state)
            assertEquals("ENTERED", body.transition)
            assertTrue(body.alert_triggered)
            assertEquals("Hóspede entrou no raio de 200m da propriedade.", body.message)
        }

        @Test
        fun `should respond 200 OK with NO_CHANGE and no alert when guest remains inside radius (inside to inside)`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(previousState = "inside"))
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.decodeFromString<LocationEventResponseDto>(response.bodyAsText())
            assertEquals(VALID_CORRELATION_ID, body.correlation_id)
            assertEquals("htl-recife-01", body.hotel_id)
            assertEquals("inside", body.current_state)
            assertEquals("NO_CHANGE", body.transition)
            assertFalse(body.alert_triggered)
            assertEquals("Posição atualizada sem alerta.", body.message)
        }

        @Test
        fun `should respond 200 OK with EXITED and no alert when guest leaves radius (inside to outside)`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(
                    createLocationPayloadJson(
                        guestLat = -8.065000,
                        guestLng = -34.890000,
                        previousState = "inside"
                    )
                )
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.decodeFromString<LocationEventResponseDto>(response.bodyAsText())
            assertEquals(VALID_CORRELATION_ID, body.correlation_id)
            assertEquals("htl-recife-01", body.hotel_id)
            assertEquals("outside", body.current_state)
            assertEquals("EXITED", body.transition)
            assertFalse(body.alert_triggered)
            assertEquals("Posição atualizada sem alerta.", body.message)
        }

        @Test
        fun `should respond 200 OK with sanitized correlation_id when header has whitespace padding`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", "   $VALID_CORRELATION_ID   ")
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.decodeFromString<LocationEventResponseDto>(response.bodyAsText())
            assertEquals(VALID_CORRELATION_ID, body.correlation_id)
        }

        @Test
        fun `should respond 200 OK when Content-Type contains charset parameter`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, "application/json; charset=utf-8")
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.decodeFromString<LocationEventResponseDto>(response.bodyAsText())
            assertTrue(body.alert_triggered)
        }

        @Test
        fun `should respond 200 OK when previous_state is uppercase and padded with spaces`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(previousState = "  INSIDE  "))
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.decodeFromString<LocationEventResponseDto>(response.bodyAsText())
            assertEquals("inside", body.current_state)
            assertEquals("NO_CHANGE", body.transition)
            assertFalse(body.alert_triggered)
        }
    }

    @Nested
    @DisplayName("Input Validation and Error Scenarios")
    inner class InputValidationScenarios {

        @Test
        fun `should respond 400 Bad Request with RFC 7807 when X-Correlation-ID header is missing`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:missing-header", error.type)
            assertEquals("Missing Required Header", error.title)
            assertEquals(400, error.status)
            assertTrue(error.detail.contains("X-Correlation-ID"))
            assertEquals("/v1/location-events", error.instance)
        }

        @Test
        fun `should respond 400 Bad Request when coordinates are invalid`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(guestLat = 95.0))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val responseBody = response.bodyAsText()
            assertTrue(
                responseBody.contains("INVALID_COORDINATES"),
                "O corpo da resposta de erro 400 deve conter literalmente 'INVALID_COORDINATES' conforme BDD da RFC-004"
            )
            val error = json.decodeFromString<ProblemDetailsResponse>(responseBody)
            assertEquals("urn:problem-type:invalid-coordinates", error.type)
            assertEquals("INVALID_COORDINATES", error.code)
            assertEquals(400, error.status)
        }

        @Test
        fun `should respond 400 Bad Request when geofence radius is invalid`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(radius = -10.0))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-radius", error.type)
            assertEquals(400, error.status)
        }

        @Test
        fun `should respond 400 Bad Request when previous_state is invalid`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(previousState = "invalido"))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-state", error.type)
            assertEquals(400, error.status)
        }

        @Test
        fun `should respond 400 Bad Request when geofence_radius_m is missing`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(omitField = "geofence_radius_m"))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-payload", error.type)
            assertEquals(400, error.status)
        }

        @Test
        fun `should respond 400 Bad Request when geofence_radius_m is null`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(radius = null))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-payload", error.type)
            assertEquals(400, error.status)
        }

        @Test
        fun `should respond 400 Bad Request when previous_state is missing`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(omitField = "previous_state"))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-payload", error.type)
            assertEquals(400, error.status)
        }

        @Test
        fun `should respond 400 Bad Request when previous_state is null`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(previousState = null))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-payload", error.type)
            assertEquals(400, error.status)
        }

        @Test
        fun `should respond 400 Bad Request when hotel_id is missing`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(omitField = "hotel_id"))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-payload", error.type)
            assertEquals("MISSING_FIELD", error.code)
            assertEquals("O campo 'hotel_id' é obrigatório.", error.detail)
        }

        @Test
        fun `should respond 400 Bad Request when hotel_id is blank`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(hotelId = "   "))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-payload", error.type)
            assertEquals("MISSING_FIELD", error.code)
            assertEquals("O campo 'hotel_id' é obrigatório.", error.detail)
        }

        @Test
        fun `should respond 400 Bad Request when hotel_lat is missing`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(omitField = "hotel_lat"))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-payload", error.type)
            assertEquals("MISSING_FIELD", error.code)
            assertEquals("O campo 'hotel_lat' é obrigatório.", error.detail)
        }

        @Test
        fun `should respond 400 Bad Request when hotel_lng is missing`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(omitField = "hotel_lng"))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-payload", error.type)
            assertEquals("MISSING_FIELD", error.code)
            assertEquals("O campo 'hotel_lng' é obrigatório.", error.detail)
        }

        @Test
        fun `should respond 400 Bad Request when guest_lat is missing`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(omitField = "guest_lat"))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-payload", error.type)
            assertEquals("MISSING_FIELD", error.code)
            assertEquals("O campo 'guest_lat' é obrigatório.", error.detail)
        }

        @Test
        fun `should respond 400 Bad Request when guest_lng is missing`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(omitField = "guest_lng"))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-payload", error.type)
            assertEquals("MISSING_FIELD", error.code)
            assertEquals("O campo 'guest_lng' é obrigatório.", error.detail)
        }

        @Test
        fun `should respond 400 Bad Request for malformed json`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody("{ malformed json }")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:malformed-json", error.type)
            assertEquals("Malformed JSON Request", error.title)
            assertEquals(400, error.status)
        }

        @Test
        fun `should respond 415 Unsupported Media Type when Content-Type header is missing`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:unsupported-media-type", error.type)
            assertEquals("Unsupported Media Type", error.title)
            assertEquals(415, error.status)
        }

        @Test
        fun `should respond 415 Unsupported Media Type when Content-Type is not application-json`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, "text/plain")
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:unsupported-media-type", error.type)
            assertEquals("Unsupported Media Type", error.title)
            assertEquals(415, error.status)
        }

        @Test
        fun `should respond 400 Bad Request when X-Correlation-ID header has invalid UUID`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", "uuid-invalido-12345")
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-header", error.type)
            assertEquals("Invalid Header", error.title)
            assertEquals(400, error.status)
            assertTrue(error.detail.contains("UUID"))
        }

        @Test
        fun `should respond 400 Bad Request when X-Correlation-ID is not a valid UUID v4`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", "a1b2c3d4-e5f6-1a8b-9c0d-1e2f3a4b5c6d")
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:invalid-header", error.type)
            assertEquals("Invalid Header", error.title)
            assertEquals(400, error.status)
            assertTrue(error.detail.contains("UUID"))
        }

        @Test
        fun `should respond 400 Bad Request when X-Correlation-ID header is blank`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", "   ")
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:missing-header", error.type)
            assertEquals("Missing Required Header", error.title)
            assertEquals(400, error.status)
        }

        @Test
        fun `should respond 415 Unsupported Media Type when Content-Type is incompatible xml`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, "application/xml")
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody("<xml></xml>")
            }

            assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:unsupported-media-type", error.type)
            assertEquals("Unsupported Media Type", error.title)
            assertEquals(415, error.status)
        }

        @Test
        fun `should respond 400 Bad Request when payload contains invalid field type`() = testApplication {
            application { module() }

            val payload = """
            {
              "hotel_id": "htl-recife-01",
              "hotel_lat": "INVALID_LATITUDE",
              "hotel_lng": -34.885650,
              "guest_lat": -8.053100,
              "guest_lng": -34.886100,
              "geofence_radius_m": 200.0,
              "previous_state": "outside"
            }
            """.trimIndent()

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(payload)
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:malformed-json", error.type)
            assertEquals("MALFORMED_JSON", error.code)
            assertEquals(400, error.status)
            assertNotNull(error.detail)
        }

        @Test
        fun `should respond 400 Bad Request on generic BadRequestException without serialization cause`() = testApplication {
            application {
                module()
                routing {
                    get("/v1/generic-bad-request") {
                        throw io.ktor.server.plugins.BadRequestException("Requisição inválida simulada")
                    }
                }
            }

            val response = client.get("/v1/generic-bad-request") {
                header("X-Correlation-ID", VALID_CORRELATION_ID)
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:bad-request", error.type)
            assertEquals("Bad Request", error.title)
            assertEquals(400, error.status)
            assertEquals("BAD_REQUEST", error.code)
            assertEquals("Requisição inválida.", error.detail)
        }
    }

    @Nested
    @DisplayName("Resilience and Failure Simulation Scenarios")
    inner class ResilienceScenarios {

        @Test
        fun `should respond 503 Service Unavailable with RFC 7807 when service is unavailable via flag`() = testApplication {
            application { module() }

            isServiceAvailable = false

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:service-unavailable", error.type)
            assertEquals("Service Unavailable", error.title)
            assertEquals(503, error.status)
        }

        @Test
        fun `should respond 503 Service Unavailable with RFC 7807 when X-Mock-Service-Unavailable header is present`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                header("X-Mock-Service-Unavailable", "true")
                setBody(createLocationPayloadJson())
            }

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:service-unavailable", error.type)
            assertEquals("Service Unavailable", error.title)
            assertEquals(503, error.status)
        }

        @Test
        fun `should respond 503 Service Unavailable on healthcheck when service is unavailable`() = testApplication {
            application { module() }

            isServiceAvailable = false

            val response = client.get("/healthz")
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            val body = json.decodeFromString<HealthResponse>(response.bodyAsText())
            assertEquals("DOWN", body.status)
            assertEquals("location-service-kotlin", body.service)
            assertEquals(8104, body.port)
        }

        @Test
        fun `should respond with application problem json content type on error`() = testApplication {
            application { module() }

            val response = client.post("/v1/location-events") {
                header("Content-Type", "application/json")
                header("X-Correlation-ID", VALID_CORRELATION_ID)
                setBody(createLocationPayloadJson(guestLat = 95.0))
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val contentType = response.contentType()
            assertNotNull(contentType)
            assertEquals("application", contentType?.contentType)
            assertEquals("problem+json", contentType?.contentSubtype)
        }

        @Test
        fun `should respond 500 Internal Server Error with RFC 7807 when unexpected exception occurs`() = testApplication {
            application {
                module()
                routing {
                    get("/v1/crash-simulation") {
                        throw RuntimeException("Falha inesperada no servidor")
                    }
                }
            }

            val response = client.get("/v1/crash-simulation") {
                header("X-Correlation-ID", VALID_CORRELATION_ID)
            }

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
            assertEquals("urn:problem-type:internal-server-error", error.type)
            assertEquals("Internal Server Error", error.title)
            assertEquals(500, error.status)
            assertEquals("INTERNAL_SERVER_ERROR", error.code)
            assertEquals("Erro interno inesperado no servidor.", error.detail)
            assertEquals("/v1/crash-simulation", error.instance)
        }
    }

    @Nested
    @DisplayName("Observability and Structured Logging Scenarios")
    inner class ObservabilityScenarios {

        @Test
        fun `should log structured JSON for GEOFENCE_EVALUATED event`() = testApplication {
            application { module() }

            val logbackLogger = LoggerFactory.getLogger("com.hotel.location.Application") as Logger
            val listAppender = ListAppender<ILoggingEvent>()
            listAppender.start()
            logbackLogger.addAppender(listAppender)

            try {
                val response = client.post("/v1/location-events") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody(createLocationPayloadJson())
                }

                assertEquals(HttpStatusCode.OK, response.status)

                val logEvent = listAppender.list.firstOrNull { 
                    it.formattedMessage.contains("GEOFENCE_EVALUATED") || it.mdcPropertyMap["event"] == "GEOFENCE_EVALUATED" 
                }
                assertNotNull(logEvent, "Deveria ter registrado log com evento GEOFENCE_EVALUATED")

                val mdc = logEvent!!.mdcPropertyMap
                assertEquals("INFO", logEvent.level.toString())
                assertEquals(VALID_CORRELATION_ID, mdc["correlation_id"])
                assertEquals("GEOFENCE_EVALUATED", mdc["event"])
                assertEquals("htl-recife-01", mdc["hotel_id"])
                assertEquals(107.7, mdc["distance_meters"]?.toDouble())
                assertEquals("ENTERED", mdc["transition"])
                assertNotNull(mdc["duration_ms"]?.toDouble())
                assertNotNull(mdc["timestamp"])

                assertFalse(mdc.containsKey("hotel_lat"))
                assertFalse(mdc.containsKey("hotel_lng"))
                assertFalse(mdc.containsKey("guest_lat"))
                assertFalse(mdc.containsKey("guest_lng"))
            } finally {
                logbackLogger.detachAppender(listAppender)
            }
        }

        @Test
        fun `should log structured JSON with GEOFENCE_VALIDATION_FAILED event on coordinate validation error`() = testApplication {
            application { module() }

            val logbackLogger = LoggerFactory.getLogger("com.hotel.location.Application") as Logger
            val listAppender = ListAppender<ILoggingEvent>()
            listAppender.start()
            logbackLogger.addAppender(listAppender)

            try {
                val response = client.post("/v1/location-events") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody(createLocationPayloadJson(guestLat = 999.0))
                }

                assertEquals(HttpStatusCode.BadRequest, response.status)

                val logEvent = listAppender.list.firstOrNull { it.mdcPropertyMap["event"] == "GEOFENCE_VALIDATION_FAILED" }
                assertNotNull(logEvent, "Deveria ter registrado log estruturado com evento GEOFENCE_VALIDATION_FAILED")

                val mdc = logEvent!!.mdcPropertyMap
                assertEquals("WARN", logEvent.level.toString())
                assertEquals(VALID_CORRELATION_ID, mdc["correlation_id"])
                assertEquals("GEOFENCE_VALIDATION_FAILED", mdc["event"])
                assertEquals("urn:problem-type:invalid-coordinates", mdc["error_type"])
                assertEquals("INVALID_COORDINATES", mdc["code"])
                assertEquals("400", mdc["status_code"])
                assertEquals("/v1/location-events", mdc["path"])
                assertNotNull(mdc["timestamp"])
                assertNotNull(logEvent.formattedMessage)

                assertFalse(mdc.containsKey("hotel_lat"))
                assertFalse(mdc.containsKey("hotel_lng"))
                assertFalse(mdc.containsKey("guest_lat"))
                assertFalse(mdc.containsKey("guest_lng"))
            } finally {
                logbackLogger.detachAppender(listAppender)
            }
        }

        @Test
        fun `should log structured JSON with GEOFENCE_SERVICE_UNAVAILABLE event when service is simulated unavailable`() = testApplication {
            application { module() }

            val logbackLogger = LoggerFactory.getLogger("com.hotel.location.Application") as Logger
            val listAppender = ListAppender<ILoggingEvent>()
            listAppender.start()
            logbackLogger.addAppender(listAppender)

            try {
                val response = client.post("/v1/location-events") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    header("X-Mock-Service-Unavailable", "true")
                    setBody(createLocationPayloadJson())
                }

                assertEquals(HttpStatusCode.ServiceUnavailable, response.status)

                val logEvent = listAppender.list.firstOrNull { it.mdcPropertyMap["event"] == "GEOFENCE_SERVICE_UNAVAILABLE" }
                assertNotNull(logEvent, "Deveria ter registrado log estruturado com evento GEOFENCE_SERVICE_UNAVAILABLE")

                val mdc = logEvent!!.mdcPropertyMap
                assertEquals("ERROR", logEvent.level.toString())
                assertEquals(VALID_CORRELATION_ID, mdc["correlation_id"])
                assertEquals("GEOFENCE_SERVICE_UNAVAILABLE", mdc["event"])
                assertEquals("urn:problem-type:service-unavailable", mdc["error_type"])
                assertEquals("SERVICE_UNAVAILABLE", mdc["code"])
                assertEquals("503", mdc["status_code"])
                assertEquals("/v1/location-events", mdc["path"])
                assertNotNull(mdc["timestamp"])
                assertNotNull(logEvent.formattedMessage)
            } finally {
                logbackLogger.detachAppender(listAppender)
            }
        }

        @Test
        fun `should log structured JSON with MALFORMED_JSON_ERROR event when payload is malformed`() = testApplication {
            application { module() }

            val logbackLogger = LoggerFactory.getLogger("com.hotel.location.Application") as Logger
            val listAppender = ListAppender<ILoggingEvent>()
            listAppender.start()
            logbackLogger.addAppender(listAppender)

            try {
                val response = client.post("/v1/location-events") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                    setBody("{ malformed json }")
                }

                assertEquals(HttpStatusCode.BadRequest, response.status)

                val logEvent = listAppender.list.firstOrNull { it.mdcPropertyMap["event"] == "MALFORMED_JSON_ERROR" }
                assertNotNull(logEvent, "Deveria ter registrado log estruturado com evento MALFORMED_JSON_ERROR")

                val mdc = logEvent!!.mdcPropertyMap
                assertEquals("WARN", logEvent.level.toString())
                assertEquals(VALID_CORRELATION_ID, mdc["correlation_id"])
                assertEquals("MALFORMED_JSON_ERROR", mdc["event"])
                assertEquals("urn:problem-type:malformed-json", mdc["error_type"])
                assertEquals("MALFORMED_JSON", mdc["code"])
                assertEquals("400", mdc["status_code"])
                assertNotNull(mdc["timestamp"])
                assertNotNull(logEvent.formattedMessage)
            } finally {
                logbackLogger.detachAppender(listAppender)
            }
        }

        @Test
        fun `should log structured JSON with INTERNAL_SERVER_ERROR event on unexpected crash`() = testApplication {
            application {
                module()
                routing {
                    get("/v1/crash-log-simulation") {
                        throw IllegalStateException("Erro inesperado para teste de log")
                    }
                }
            }

            val logbackLogger = LoggerFactory.getLogger("com.hotel.location.Application") as Logger
            val listAppender = ListAppender<ILoggingEvent>()
            listAppender.start()
            logbackLogger.addAppender(listAppender)

            try {
                val response = client.get("/v1/crash-log-simulation") {
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                }

                assertEquals(HttpStatusCode.InternalServerError, response.status)

                val logEvent = listAppender.list.firstOrNull { it.mdcPropertyMap["event"] == "INTERNAL_SERVER_ERROR" }
                assertNotNull(logEvent, "Deveria ter registrado log estruturado com evento INTERNAL_SERVER_ERROR")

                val mdc = logEvent!!.mdcPropertyMap
                assertEquals("ERROR", logEvent.level.toString())
                assertEquals(VALID_CORRELATION_ID, mdc["correlation_id"])
                assertEquals("INTERNAL_SERVER_ERROR", mdc["event"])
                assertEquals("urn:problem-type:internal-server-error", mdc["error_type"])
                assertEquals("INTERNAL_SERVER_ERROR", mdc["code"])
                assertEquals("500", mdc["status_code"])
                assertEquals("/v1/crash-log-simulation", mdc["path"])
                assertNotNull(mdc["timestamp"])
            } finally {
                logbackLogger.detachAppender(listAppender)
            }
        }

        @Test
        fun `should log structured JSON with BAD_REQUEST_ERROR event on generic bad request`() = testApplication {
            application {
                module()
                routing {
                    get("/v1/generic-bad-request-log") {
                        throw io.ktor.server.plugins.BadRequestException("Falha genérica")
                    }
                }
            }

            val logbackLogger = LoggerFactory.getLogger("com.hotel.location.Application") as Logger
            val listAppender = ListAppender<ILoggingEvent>()
            listAppender.start()
            logbackLogger.addAppender(listAppender)

            try {
                val response = client.get("/v1/generic-bad-request-log") {
                    header("X-Correlation-ID", VALID_CORRELATION_ID)
                }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                val logEvent = listAppender.list.firstOrNull { it.mdcPropertyMap["event"] == "BAD_REQUEST_ERROR" }
                assertNotNull(logEvent, "Deveria ter registrado log estruturado com evento BAD_REQUEST_ERROR")
                val mdc = logEvent!!.mdcPropertyMap
                assertEquals("WARN", logEvent.level.toString())
                assertEquals(VALID_CORRELATION_ID, mdc["correlation_id"])
                assertEquals("BAD_REQUEST_ERROR", mdc["event"])
                assertEquals("urn:problem-type:bad-request", mdc["error_type"])
                assertEquals("BAD_REQUEST", mdc["code"])
                assertEquals("400", mdc["status_code"])
            } finally {
                logbackLogger.detachAppender(listAppender)
            }
        }
    }
}
