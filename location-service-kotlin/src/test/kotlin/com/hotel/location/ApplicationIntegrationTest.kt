package com.hotel.location

import com.hotel.location.dto.LocationEventResponseDto
import com.hotel.location.dto.ProblemDetailsResponse
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApplicationIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val VALID_CORRELATION_ID = "a1b2c3d4-e5f6-4a8b-9c0d-1e2f3a4b5c6d"
    }

    @Test
    fun `deve responder 200 OK no healthcheck`() = testApplication {
        application {
            module()
        }

        val response = client.get("/healthz")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `deve responder 200 OK para evento de localizacao valido`() = testApplication {
        application {
            module()
        }

        val response = client.post("/v1/location-events") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header("X-Correlation-ID", VALID_CORRELATION_ID)
            setBody(
                """
                {
                    "hotel_id": "htl-recife-01",
                    "hotel_lat": -8.052240,
                    "hotel_lng": -34.885650,
                    "guest_lat": -8.053100,
                    "guest_lng": -34.886100,
                    "geofence_radius_m": 200.0,
                    "previous_state": "outside"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<LocationEventResponseDto>(response.bodyAsText())
        assertEquals(VALID_CORRELATION_ID, body.correlation_id)
        assertEquals("htl-recife-01", body.hotel_id)
        assertEquals("inside", body.current_state)
        assertEquals("ENTERED", body.transition)
        assertTrue(body.alert_triggered)
    }

    @Test
    fun `deve responder 400 Bad Request com RFC 7807 quando header X-Correlation-ID estiver ausente`() = testApplication {
        application {
            module()
        }

        val response = client.post("/v1/location-events") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """
                {
                    "hotel_id": "htl-recife-01",
                    "hotel_lat": -8.052240,
                    "hotel_lng": -34.885650,
                    "guest_lat": -8.053100,
                    "guest_lng": -34.886100
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("urn:problem-type:missing-header", error.type)
        assertEquals("Missing Required Header", error.title)
        assertEquals(400, error.status)
        assertEquals("/v1/location-events", error.instance)
    }

    @Test
    fun `deve responder 400 Bad Request quando coordenadas forem invalidas`() = testApplication {
        application {
            module()
        }

        val response = client.post("/v1/location-events") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header("X-Correlation-ID", VALID_CORRELATION_ID)
            setBody(
                """
                {
                    "hotel_id": "htl-recife-01",
                    "hotel_lat": -8.052240,
                    "hotel_lng": -34.885650,
                    "guest_lat": 95.0,
                    "guest_lng": -34.886100,
                    "geofence_radius_m": 200.0,
                    "previous_state": "outside"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("urn:problem-type:invalid-coordinates", error.type)
        assertEquals(400, error.status)
    }

    @Test
    fun `deve responder 400 Bad Request quando raio for invalido`() = testApplication {
        application {
            module()
        }

        val response = client.post("/v1/location-events") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header("X-Correlation-ID", VALID_CORRELATION_ID)
            setBody(
                """
                {
                    "hotel_id": "htl-recife-01",
                    "hotel_lat": -8.052240,
                    "hotel_lng": -34.885650,
                    "guest_lat": -8.053100,
                    "guest_lng": -34.886100,
                    "geofence_radius_m": -10.0,
                    "previous_state": "outside"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("urn:problem-type:invalid-radius", error.type)
        assertEquals(400, error.status)
    }

    @Test
    fun `deve responder 400 Bad Request quando previous_state for invalido`() = testApplication {
        application {
            module()
        }

        val response = client.post("/v1/location-events") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header("X-Correlation-ID", VALID_CORRELATION_ID)
            setBody(
                """
                {
                    "hotel_id": "htl-recife-01",
                    "hotel_lat": -8.052240,
                    "hotel_lng": -34.885650,
                    "guest_lat": -8.053100,
                    "guest_lng": -34.886100,
                    "geofence_radius_m": 200.0,
                    "previous_state": "invalido"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("urn:problem-type:invalid-state", error.type)
        assertEquals(400, error.status)
    }

    @Test
    fun `deve responder 400 Bad Request para json malformado`() = testApplication {
        application {
            module()
        }

        val response = client.post("/v1/location-events") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header("X-Correlation-ID", VALID_CORRELATION_ID)
            setBody("{ payload_invalido: ")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("urn:problem-type:malformed-json", error.type)
        assertEquals(400, error.status)
    }

    @Test
    fun `deve responder 400 Bad Request quando header Content-Type estiver ausente`() = testApplication {
        application {
            module()
        }

        val response = client.post("/v1/location-events") {
            header("X-Correlation-ID", VALID_CORRELATION_ID)
            setBody(
                object : io.ktor.http.content.OutgoingContent.ByteArrayContent() {
                    override val contentType: ContentType? = null
                    override val contentLength: Long = 0L
                    override fun bytes(): ByteArray = ByteArray(0)
                }
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("urn:problem-type:missing-header", error.type)
        assertEquals("Missing Required Header", error.title)
        assertEquals(400, error.status)
        assertTrue(error.detail.contains("Content-Type"))
    }

    @Test
    fun `deve responder 400 Bad Request quando Content-Type for diferente de application-json`() = testApplication {
        application {
            module()
        }

        val response = client.post("/v1/location-events") {
            header(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            header("X-Correlation-ID", VALID_CORRELATION_ID)
            setBody("texto simples")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("urn:problem-type:invalid-content-type", error.type)
        assertEquals("Invalid Content-Type", error.title)
        assertEquals(400, error.status)
        assertTrue(error.detail.contains("text/plain"))
    }

    @Test
    fun `deve responder 400 Bad Request quando header X-Correlation-ID tiver UUID invalido`() = testApplication {
        application {
            module()
        }

        val response = client.post("/v1/location-events") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header("X-Correlation-ID", "uuid-invalido-12345")
            setBody(
                """
                {
                    "hotel_id": "htl-recife-01",
                    "hotel_lat": -8.052240,
                    "hotel_lng": -34.885650,
                    "guest_lat": -8.053100,
                    "guest_lng": -34.886100,
                    "geofence_radius_m": 200.0,
                    "previous_state": "outside"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("urn:problem-type:invalid-header", error.type)
        assertEquals("Invalid Header", error.title)
        assertEquals(400, error.status)
        assertTrue(error.detail.contains("UUID"))
    }
}
