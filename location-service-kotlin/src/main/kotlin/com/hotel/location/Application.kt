package com.hotel.location

import com.hotel.location.dto.HealthResponse
import com.hotel.location.dto.LocationEventRequestDto
import com.hotel.location.dto.ProblemDetailsResponse
import com.hotel.location.dto.toDto
import com.hotel.location.exception.DomainException
import com.hotel.location.exception.InvalidContentTypeException
import com.hotel.location.exception.InvalidCoordinatesException
import com.hotel.location.exception.InvalidGeofenceRadiusException
import com.hotel.location.exception.InvalidGeofenceStateException
import com.hotel.location.exception.InvalidHeaderException
import com.hotel.location.exception.LocationValidationException
import com.hotel.location.exception.MissingContentTypeException
import com.hotel.location.exception.MissingFieldException
import com.hotel.location.exception.MissingHeaderException
import com.hotel.location.exception.ServiceUnavailableException
import com.hotel.location.service.HaversineEngine
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.MDC

fun main() {
    embeddedServer(Netty, port = 8104, host = "127.0.0.1", module = Application::module).start(wait = true)
}

private val logger = LoggerFactory.getLogger("com.hotel.location.Application")

private val UUID_V4_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")

fun isValidUuid(value: String): Boolean = UUID_V4_REGEX.matches(value.trim())

@Volatile
var isServiceAvailable: Boolean = true

fun resetServiceState() {
    isServiceAvailable = true
}

private fun extractFieldFromSerializationMessage(message: String?): String? {
    if (message == null) return null
    val fieldMatch = Regex("Field '([^']+)'").find(message)
    if (fieldMatch != null) return fieldMatch.groupValues[1]
    val pathMatch = Regex("path: \\$\\.([a-zA-Z0-9_]+)").find(message)
    if (pathMatch != null) return pathMatch.groupValues[1]
    return null
}

private val problemJson = Json {
    prettyPrint = true
    isLenient = true
    ignoreUnknownKeys = true
}

private val PROBLEM_DETAILS_CONTENT_TYPE = ContentType("application", "problem+json").withCharset(Charsets.UTF_8)

private suspend fun ApplicationCall.respondProblem(
    status: HttpStatusCode,
    problem: ProblemDetailsResponse
) {
    respondText(
        text = problemJson.encodeToString(problem),
        contentType = PROBLEM_DETAILS_CONTENT_TYPE,
        status = status
    )
}

private fun logError(
    level: String,
    event: String,
    correlationId: String,
    errorType: String,
    statusCode: HttpStatusCode,
    path: String,
    detail: String,
    field: String? = null,
    code: String? = null
) {
    try {
        MDC.put("correlation_id", correlationId)
        MDC.put("event", event)
        MDC.put("error_type", errorType)
        MDC.put("status_code", statusCode.value.toString())
        MDC.put("path", path)
        MDC.put("error_detail", detail)
        if (field != null) MDC.put("field", field)
        if (code != null) MDC.put("code", code)
        MDC.put("timestamp", java.time.Instant.now().toString())

        if (level == "ERROR") logger.error(event) else logger.warn(event)
    } finally {
        MDC.clear()
    }
}

fun Application.module() {
    install(ContentNegotiation) {
        val defaultJson = Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        }
        json(defaultJson)
        json(defaultJson, ContentType("application", "problem+json"))
    }

    install(StatusPages) {
        exception<DomainException> { call, cause ->
            val correlationId = call.request.headers["X-Correlation-ID"] ?: "none"
            val (typeUri, title) = when (cause) {
                is InvalidCoordinatesException -> "urn:problem-type:invalid-coordinates" to "Invalid Coordinates"
                is InvalidGeofenceRadiusException -> "urn:problem-type:invalid-radius" to "Invalid Geofence Radius"
                is InvalidGeofenceStateException -> "urn:problem-type:invalid-state" to "Invalid Geofence State"
                is MissingFieldException -> "urn:problem-type:invalid-payload" to "Invalid Payload"
                else -> "urn:problem-type:bad-request" to "Bad Request"
            }
            logError(
                level = "WARN",
                event = "GEOFENCE_VALIDATION_FAILED",
                correlationId = correlationId,
                errorType = typeUri,
                statusCode = HttpStatusCode.BadRequest,
                path = call.request.path(),
                detail = cause.message,
                field = cause.field,
                code = cause.errorCode.name
            )
            call.respondProblem(
                HttpStatusCode.BadRequest,
                ProblemDetailsResponse(
                    type = typeUri,
                    title = title,
                    status = HttpStatusCode.BadRequest.value,
                    detail = cause.message,
                    instance = call.request.path(),
                    code = cause.errorCode.name
                )
            )
        }

        exception<LocationValidationException> { call, cause ->
            val correlationId = call.request.headers["X-Correlation-ID"] ?: "none"
            logError(
                level = "WARN",
                event = "GEOFENCE_VALIDATION_FAILED",
                correlationId = correlationId,
                errorType = cause.typeUri,
                statusCode = cause.statusCode,
                path = call.request.path(),
                detail = cause.message,
                field = cause.field,
                code = cause.errorCode.name
            )
            call.respondProblem(
                cause.statusCode,
                ProblemDetailsResponse(
                    type = cause.typeUri,
                    title = cause.title,
                    status = cause.statusCode.value,
                    detail = cause.message,
                    instance = call.request.path(),
                    code = cause.errorCode.name
                )
            )
        }

        exception<SerializationException> { call, cause ->
            val correlationId = call.request.headers["X-Correlation-ID"] ?: "none"
            val field = extractFieldFromSerializationMessage(cause.message)
            val detail = if (field != null) {
                "O campo '$field' contém dados inválidos ou incompatíveis."
            } else {
                "O payload enviado é um JSON malformado ou incompatível."
            }
            logError(
                level = "WARN",
                event = "MALFORMED_JSON_ERROR",
                correlationId = correlationId,
                errorType = "urn:problem-type:malformed-json",
                statusCode = HttpStatusCode.BadRequest,
                path = call.request.path(),
                detail = detail,
                field = field,
                code = "MALFORMED_JSON"
            )
            call.respondProblem(
                HttpStatusCode.BadRequest,
                ProblemDetailsResponse(
                    type = "urn:problem-type:malformed-json",
                    title = "Malformed JSON Request",
                    status = HttpStatusCode.BadRequest.value,
                    detail = detail,
                    instance = call.request.path(),
                    code = "MALFORMED_JSON"
                )
            )
        }

        exception<BadRequestException> { call, cause ->
            val correlationId = call.request.headers["X-Correlation-ID"] ?: "none"
            val serializationException = generateSequence(cause as Throwable) { it.cause }
                .filterIsInstance<SerializationException>()
                .firstOrNull()

            val isSerialization = serializationException != null
            val typeUri = if (isSerialization) "urn:problem-type:malformed-json" else "urn:problem-type:bad-request"
            val title = if (isSerialization) "Malformed JSON Request" else "Bad Request"
            val code = if (isSerialization) "MALFORMED_JSON" else "BAD_REQUEST"

            val field = extractFieldFromSerializationMessage(serializationException?.message ?: cause.cause?.message ?: cause.message)
            val detail = if (field != null) {
                "O campo '$field' contém dados inválidos."
            } else if (isSerialization) {
                "O payload enviado é um JSON malformado ou incompatível."
            } else {
                "Requisição inválida."
            }

            logError(
                level = "WARN",
                event = if (isSerialization) "MALFORMED_JSON_ERROR" else "BAD_REQUEST_ERROR",
                correlationId = correlationId,
                errorType = typeUri,
                statusCode = HttpStatusCode.BadRequest,
                path = call.request.path(),
                detail = detail,
                field = field,
                code = code
            )
            call.respondProblem(
                HttpStatusCode.BadRequest,
                ProblemDetailsResponse(
                    type = typeUri,
                    title = title,
                    status = HttpStatusCode.BadRequest.value,
                    detail = detail,
                    instance = call.request.path(),
                    code = code
                )
            )
        }

        exception<ServiceUnavailableException> { call, cause ->
            val correlationId = call.request.headers["X-Correlation-ID"] ?: "none"
            logError(
                level = "ERROR",
                event = "GEOFENCE_SERVICE_UNAVAILABLE",
                correlationId = correlationId,
                errorType = cause.typeUri,
                statusCode = cause.statusCode,
                path = call.request.path(),
                detail = cause.message,
                field = cause.field,
                code = cause.errorCode.name
            )
            call.respondProblem(
                HttpStatusCode.ServiceUnavailable,
                ProblemDetailsResponse(
                    type = cause.typeUri,
                    title = cause.title,
                    status = HttpStatusCode.ServiceUnavailable.value,
                    detail = cause.message,
                    instance = call.request.path(),
                    code = cause.errorCode.name
                )
            )
        }

        exception<Throwable> { call, cause ->
            val correlationId = call.request.headers["X-Correlation-ID"] ?: "none"
            val detail = cause.message ?: "Erro interno inesperado no servidor."
            logError(
                level = "ERROR",
                event = "INTERNAL_SERVER_ERROR",
                correlationId = correlationId,
                errorType = "urn:problem-type:internal-server-error",
                statusCode = HttpStatusCode.InternalServerError,
                path = call.request.path(),
                detail = detail,
                code = "INTERNAL_SERVER_ERROR"
            )
            call.respondProblem(
                HttpStatusCode.InternalServerError,
                ProblemDetailsResponse(
                    type = "urn:problem-type:internal-server-error",
                    title = "Internal Server Error",
                    status = HttpStatusCode.InternalServerError.value,
                    detail = "Erro interno inesperado no servidor.",
                    instance = call.request.path(),
                    code = "INTERNAL_SERVER_ERROR"
                )
            )
        }
    }

    routing {
        get("/healthz") {
            if (!isServiceAvailable) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    HealthResponse(
                        status = "DOWN",
                        service = "location-service-kotlin",
                        port = 8104
                    )
                )
            } else {
                call.respond(
                    HttpStatusCode.OK,
                    HealthResponse(
                        status = "UP",
                        service = "location-service-kotlin",
                        port = 8104
                    )
                )
            }
        }
        post("/v1/location-events") {
            if (!isServiceAvailable || call.request.headers["X-Mock-Service-Unavailable"]?.equals("true", ignoreCase = true) == true) {
                throw ServiceUnavailableException()
            }

            val rawContentType = call.request.headers[HttpHeaders.ContentType]
            if (rawContentType.isNullOrBlank()) {
                throw MissingContentTypeException()
            }
            val parsedContentType = runCatching { ContentType.parse(rawContentType) }.getOrNull()
            if (parsedContentType == null || parsedContentType.withoutParameters() != ContentType.Application.Json) {
                throw InvalidContentTypeException(rawContentType)
            }

            val rawCorrelationId = call.request.headers["X-Correlation-ID"]
            if (rawCorrelationId.isNullOrBlank()) {
                throw MissingHeaderException("X-Correlation-ID")
            }
            val correlationId = rawCorrelationId.trim()
            if (!isValidUuid(correlationId)) {
                throw InvalidHeaderException("X-Correlation-ID", "O valor '$rawCorrelationId' não é um UUID v4 válido.")
            }

            val requestDto = call.receive<LocationEventRequestDto>()
            val domainEvent = requestDto.toDomain()
            val startTimeNano = System.nanoTime()
            val result = HaversineEngine.evaluate(domainEvent, correlationId)
            val durationMs = (System.nanoTime() - startTimeNano) / 1_000_000.0

            try {
                MDC.put("correlation_id", correlationId)
                MDC.put("event", "GEOFENCE_EVALUATED")
                MDC.put("hotel_id", result.hotelId)
                MDC.put("distance_meters", result.distanceMeters.toString())
                MDC.put("transition", result.transition.name)
                MDC.put("duration_ms", "%.2f".format(java.util.Locale.US, durationMs))
                MDC.put("timestamp", java.time.Instant.now().toString())
                logger.info("GEOFENCE_EVALUATED")
            } finally {
                MDC.clear()
            }

            call.respond(HttpStatusCode.OK, result.toDto())
        }
    }
}
