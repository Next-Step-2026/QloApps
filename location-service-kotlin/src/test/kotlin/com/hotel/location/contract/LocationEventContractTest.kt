package com.hotel.location.contract

import com.hotel.location.isServiceAvailable
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Testes de Contrato HTTP e Validação de JSON Schema (RFC-004)")
class LocationEventContractTest : BaseContractTest() {

    companion object {
        private const val VALID_CORRELATION_ID = "123e4567-e89b-42d3-a456-426614174000"
    }

    @Test
    fun `deve validar contrato JSON Schema de sucesso no health check UP`() {
        given()
            .`when`()
            .get("/healthz")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body(matchesJsonSchemaInClasspath("schemas/health-response.schema.json"))
            .body("status", equalTo("UP"))
            .body("service", equalTo("location-service-kotlin"))
    }

    @Test
    fun `deve validar contrato JSON Schema de degradacao no health check DOWN`() {
        isServiceAvailable = false

        given()
            .`when`()
            .get("/healthz")
            .then()
            .statusCode(503)
            .contentType(ContentType.JSON)
            .body(matchesJsonSchemaInClasspath("schemas/health-response.schema.json"))
            .body("status", equalTo("DOWN"))
            .body("service", equalTo("location-service-kotlin"))
    }

    @Test
    fun `deve validar contrato JSON Schema quando hospede entra no raio gerando transicao ENTERED com alerta ativo`() {
        val payload = """
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

        given()
            .contentType(ContentType.JSON)
            .header("X-Correlation-ID", VALID_CORRELATION_ID)
            .body(payload)
            .`when`()
            .post("/v1/location-events")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body(matchesJsonSchemaInClasspath("schemas/location-event-response.schema.json"))
            .body("correlation_id", equalTo(VALID_CORRELATION_ID))
            .body("hotel_id", equalTo("htl-recife-01"))
            .body("current_state", equalTo("inside"))
            .body("transition", equalTo("ENTERED"))
            .body("alert_triggered", equalTo(true))
            .body("message", equalTo("Hóspede entrou no raio de 200m da propriedade."))
    }

    @Test
    fun `deve validar contrato JSON Schema quando hospede sai do raio gerando transicao EXITED sem alerta`() {
        val payload = """
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

        given()
            .contentType(ContentType.JSON)
            .header("X-Correlation-ID", VALID_CORRELATION_ID)
            .body(payload)
            .`when`()
            .post("/v1/location-events")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body(matchesJsonSchemaInClasspath("schemas/location-event-response.schema.json"))
            .body("correlation_id", equalTo(VALID_CORRELATION_ID))
            .body("current_state", equalTo("outside"))
            .body("transition", equalTo("EXITED"))
            .body("alert_triggered", equalTo(false))
            .body("message", equalTo("Posição atualizada sem alerta."))
    }

    @Test
    fun `deve validar contrato JSON Schema quando hospede permanece fora gerando NO_CHANGE sem alerta`() {
        val payload = """
            {
                "hotel_id": "htl-recife-01",
                "hotel_lat": -8.052240,
                "hotel_lng": -34.885650,
                "guest_lat": -8.065000,
                "guest_lng": -34.890000,
                "geofence_radius_m": 200.0,
                "previous_state": "outside"
            }
        """.trimIndent()

        given()
            .contentType(ContentType.JSON)
            .header("X-Correlation-ID", VALID_CORRELATION_ID)
            .body(payload)
            .`when`()
            .post("/v1/location-events")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body(matchesJsonSchemaInClasspath("schemas/location-event-response.schema.json"))
            .body("current_state", equalTo("outside"))
            .body("transition", equalTo("NO_CHANGE"))
            .body("alert_triggered", equalTo(false))
    }

    @Test
    fun `deve validar contrato RFC 7807 Problem Details quando latitude for invalida`() {
        val payload = """
            {
                "hotel_id": "htl-recife-01",
                "hotel_lat": 95.0,
                "hotel_lng": -34.885650,
                "guest_lat": -8.053100,
                "guest_lng": -34.886100,
                "geofence_radius_m": 200.0,
                "previous_state": "outside"
            }
        """.trimIndent()

        given()
            .contentType(ContentType.JSON)
            .header("X-Correlation-ID", VALID_CORRELATION_ID)
            .body(payload)
            .`when`()
            .post("/v1/location-events")
            .then()
            .statusCode(400)
            .contentType("application/problem+json; charset=UTF-8")
            .body(matchesJsonSchemaInClasspath("schemas/problem-details.schema.json"))
            .body("code", equalTo("INVALID_COORDINATES"))
            .body("status", equalTo(400))
            .body("instance", equalTo("/v1/location-events"))
    }

    @Test
    fun `deve validar contrato RFC 7807 Problem Details quando raio for zero ou negativo`() {
        val payload = """
            {
                "hotel_id": "htl-recife-01",
                "hotel_lat": -8.052240,
                "hotel_lng": -34.885650,
                "guest_lat": -8.053100,
                "guest_lng": -34.886100,
                "geofence_radius_m": 0.0,
                "previous_state": "outside"
            }
        """.trimIndent()

        given()
            .contentType(ContentType.JSON)
            .header("X-Correlation-ID", VALID_CORRELATION_ID)
            .body(payload)
            .`when`()
            .post("/v1/location-events")
            .then()
            .statusCode(400)
            .contentType("application/problem+json; charset=UTF-8")
            .body(matchesJsonSchemaInClasspath("schemas/problem-details.schema.json"))
            .body("code", equalTo("INVALID_RADIUS"))
            .body("status", equalTo(400))
    }

    @Test
    fun `deve validar contrato RFC 7807 Problem Details quando previous_state for invalido`() {
        val payload = """
            {
                "hotel_id": "htl-recife-01",
                "hotel_lat": -8.052240,
                "hotel_lng": -34.885650,
                "guest_lat": -8.053100,
                "guest_lng": -34.886100,
                "geofence_radius_m": 200.0,
                "previous_state": "unknown_state"
            }
        """.trimIndent()

        given()
            .contentType(ContentType.JSON)
            .header("X-Correlation-ID", VALID_CORRELATION_ID)
            .body(payload)
            .`when`()
            .post("/v1/location-events")
            .then()
            .statusCode(400)
            .contentType("application/problem+json; charset=UTF-8")
            .body(matchesJsonSchemaInClasspath("schemas/problem-details.schema.json"))
            .body("code", equalTo("INVALID_STATE"))
            .body("status", equalTo(400))
    }

    @Test
    fun `deve validar contrato RFC 7807 Problem Details quando campo obrigatorio estiver ausente`() {
        val payload = """
            {
                "hotel_lat": -8.052240,
                "hotel_lng": -34.885650,
                "guest_lat": -8.053100,
                "guest_lng": -34.886100,
                "geofence_radius_m": 200.0,
                "previous_state": "outside"
            }
        """.trimIndent()

        given()
            .contentType(ContentType.JSON)
            .header("X-Correlation-ID", VALID_CORRELATION_ID)
            .body(payload)
            .`when`()
            .post("/v1/location-events")
            .then()
            .statusCode(400)
            .contentType("application/problem+json; charset=UTF-8")
            .body(matchesJsonSchemaInClasspath("schemas/problem-details.schema.json"))
            .body("code", equalTo("MISSING_FIELD"))
            .body("detail", equalTo("O campo 'hotel_id' é obrigatório."))
    }

    @Test
    fun `deve validar contrato RFC 7807 Problem Details quando geofence_radius_m estiver ausente`() {
        val payload = """
            {
                "hotel_id": "htl-recife-01",
                "hotel_lat": -8.052240,
                "hotel_lng": -34.885650,
                "guest_lat": -8.053100,
                "guest_lng": -34.886100,
                "previous_state": "outside"
            }
        """.trimIndent()

        given()
            .contentType(ContentType.JSON)
            .header("X-Correlation-ID", VALID_CORRELATION_ID)
            .body(payload)
            .`when`()
            .post("/v1/location-events")
            .then()
            .statusCode(400)
            .contentType("application/problem+json; charset=UTF-8")
            .body(matchesJsonSchemaInClasspath("schemas/problem-details.schema.json"))
            .body("code", equalTo("MISSING_FIELD"))
            .body("detail", equalTo("O campo 'geofence_radius_m' é obrigatório."))
    }

    @Test
    fun `deve validar contrato RFC 7807 Problem Details quando header X-Correlation-ID estiver ausente`() {
        val payload = """
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

        given()
            .contentType(ContentType.JSON)
            .body(payload)
            .`when`()
            .post("/v1/location-events")
            .then()
            .statusCode(400)
            .contentType("application/problem+json; charset=UTF-8")
            .body(matchesJsonSchemaInClasspath("schemas/problem-details.schema.json"))
            .body("code", equalTo("MISSING_HEADER"))
    }

    @Test
    fun `deve validar contrato RFC 7807 Problem Details quando header X-Correlation-ID for formato invalido`() {
        val payload = """
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

        given()
            .contentType(ContentType.JSON)
            .header("X-Correlation-ID", "not-a-valid-uuid-v4")
            .body(payload)
            .`when`()
            .post("/v1/location-events")
            .then()
            .statusCode(400)
            .contentType("application/problem+json; charset=UTF-8")
            .body(matchesJsonSchemaInClasspath("schemas/problem-details.schema.json"))
            .body("code", equalTo("INVALID_HEADER"))
    }

    @Test
    fun `deve validar contrato RFC 7807 Problem Details quando Content-Type nao for application-json`() {
        given()
            .contentType(ContentType.TEXT)
            .header("X-Correlation-ID", VALID_CORRELATION_ID)
            .body("corpo-de-texto-invalido")
            .`when`()
            .post("/v1/location-events")
            .then()
            .statusCode(415)
            .contentType("application/problem+json; charset=UTF-8")
            .body(matchesJsonSchemaInClasspath("schemas/problem-details.schema.json"))
            .body("code", equalTo("UNSUPPORTED_MEDIA_TYPE"))
    }

    @Test
    fun `deve validar contrato RFC 7807 Problem Details quando o payload for um JSON malformado`() {
        given()
            .contentType(ContentType.JSON)
            .header("X-Correlation-ID", VALID_CORRELATION_ID)
            .body("{ malformed: json, syntax-error }")
            .`when`()
            .post("/v1/location-events")
            .then()
            .statusCode(400)
            .contentType("application/problem+json; charset=UTF-8")
            .body(matchesJsonSchemaInClasspath("schemas/problem-details.schema.json"))
            .body("type", equalTo("urn:problem-type:malformed-json"))
            .body("code", equalTo("MALFORMED_JSON"))
            .body("status", equalTo(400))
    }

    @Test
    fun `deve validar contrato RFC 7807 Problem Details quando servico estiver simulado indisponivel via header`() {
        val payload = """
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

        given()
            .contentType(ContentType.JSON)
            .header("X-Correlation-ID", VALID_CORRELATION_ID)
            .header("X-Mock-Service-Unavailable", "true")
            .body(payload)
            .`when`()
            .post("/v1/location-events")
            .then()
            .statusCode(503)
            .contentType("application/problem+json; charset=UTF-8")
            .body(matchesJsonSchemaInClasspath("schemas/problem-details.schema.json"))
            .body("type", equalTo("urn:problem-type:service-unavailable"))
            .body("code", equalTo("SERVICE_UNAVAILABLE"))
            .body("status", equalTo(503))
    }
}
