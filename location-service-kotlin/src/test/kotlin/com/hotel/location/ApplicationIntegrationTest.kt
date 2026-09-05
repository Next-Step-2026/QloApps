package com.hotel.location

import com.hotel.location.dto.HealthResponse
import com.hotel.location.dto.LocationEventResponseDto
import com.hotel.location.dto.ProblemDetailsResponse
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
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
        assertEquals("Hóspede entrou no raio de 200m da propriedade.", body.message)
    }

    @Test
    fun `deve responder 200 OK com NO_CHANGE e sem alerta quando hospede permanecer dentro do raio (inside para inside)`() = testApplication {
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
                    "previous_state": "inside"
                }
                """.trimIndent()
            )
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
    fun `deve responder 200 OK com EXITED e sem alerta quando hospede sair do raio (inside para outside)`() = testApplication {
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
                    "guest_lat": -8.065000,
                    "guest_lng": -34.890000,
                    "geofence_radius_m": 200.0,
                    "previous_state": "inside"
                }
                """.trimIndent()
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
    fun `deve responder 400 Bad Request quando geofence_radius_m for ausente`() = testApplication {
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
                    "previous_state": "outside"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("urn:problem-type:invalid-payload", error.type)
        assertEquals("Invalid Payload", error.title)
        assertEquals(400, error.status)
        assertTrue(error.detail.contains("geofence_radius_m"))
    }

    @Test
    fun `deve responder 400 Bad Request quando geofence_radius_m for nulo`() = testApplication {
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
                    "geofence_radius_m": null,
                    "previous_state": "outside"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("urn:problem-type:invalid-payload", error.type)
        assertEquals("Invalid Payload", error.title)
        assertEquals(400, error.status)
        assertTrue(error.detail.contains("geofence_radius_m"))
    }

    @Test
    fun `deve responder 400 Bad Request quando previous_state for ausente`() = testApplication {
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
                    "geofence_radius_m": 200.0
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("urn:problem-type:invalid-payload", error.type)
        assertEquals("Invalid Payload", error.title)
        assertEquals(400, error.status)
        assertTrue(error.detail.contains("previous_state"))
    }

    @Test
    fun `deve responder 400 Bad Request quando previous_state for nulo`() = testApplication {
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
                    "previous_state": null
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("urn:problem-type:invalid-payload", error.type)
        assertEquals("Invalid Payload", error.title)
        assertEquals(400, error.status)
        assertTrue(error.detail.contains("previous_state"))
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
    fun `deve responder 415 Unsupported Media Type quando header Content-Type estiver ausente`() = testApplication {
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

        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("urn:problem-type:unsupported-media-type", error.type)
        assertEquals("Unsupported Media Type", error.title)
        assertEquals(415, error.status)
        assertTrue(error.detail.contains("Content-Type"))
    }

    @Test
    fun `deve responder 415 Unsupported Media Type quando Content-Type for diferente de application-json`() = testApplication {
        application {
            module()
        }

        val response = client.post("/v1/location-events") {
            header(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            header("X-Correlation-ID", VALID_CORRELATION_ID)
            setBody("texto simples")
        }

        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("urn:problem-type:unsupported-media-type", error.type)
        assertEquals("Unsupported Media Type", error.title)
        assertEquals(415, error.status)
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

    @AfterEach
    fun tearDown() {
        resetServiceState()
    }

    @Test
    fun `deve responder 503 Service Unavailable com RFC 7807 quando servico estiver indisponivel via flag`() = testApplication {
        application {
            module()
        }

        isServiceAvailable = false

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

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("urn:problem-type:service-unavailable", error.type)
        assertEquals("Service Unavailable", error.title)
        assertEquals(503, error.status)
        assertEquals("/v1/location-events", error.instance)
        assertTrue(error.detail.contains("indisponível", ignoreCase = true))
    }

    @Test
    fun `deve responder 503 Service Unavailable com RFC 7807 quando header X-Mock-Service-Unavailable estiver presente`() = testApplication {
        application {
            module()
        }

        val response = client.post("/v1/location-events") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header("X-Correlation-ID", VALID_CORRELATION_ID)
            header("X-Mock-Service-Unavailable", "true")
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

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        val error = json.decodeFromString<ProblemDetailsResponse>(response.bodyAsText())
        assertEquals("urn:problem-type:service-unavailable", error.type)
        assertEquals("Service Unavailable", error.title)
        assertEquals(503, error.status)
        assertEquals("/v1/location-events", error.instance)
        assertTrue(error.detail.contains("indisponível", ignoreCase = true))
    }

    @Test
    fun `deve responder 503 Service Unavailable no healthcheck quando servico estiver indisponivel`() = testApplication {
        application {
            module()
        }

        isServiceAvailable = false

        val response = client.get("/healthz")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        val body = json.decodeFromString<HealthResponse>(response.bodyAsText())
        assertEquals("DOWN", body.status)
        assertEquals("location-service-kotlin", body.service)
        assertEquals(8104, body.port)
    }
}
