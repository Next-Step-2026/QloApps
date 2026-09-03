package com.hotel.location

import com.hotel.location.dto.HealthResponse
import com.hotel.location.dto.LocationEventRequestDto
import com.hotel.location.dto.ProblemDetailsResponse
import com.hotel.location.dto.toDto
import com.hotel.location.exception.InvalidContentTypeException
import com.hotel.location.exception.InvalidHeaderException
import com.hotel.location.exception.LocationValidationException
import com.hotel.location.exception.MissingContentTypeException
import com.hotel.location.exception.MissingHeaderException
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
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 8104, host = "127.0.0.1", module = Application::module).start(wait = true)
}

private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

fun isValidUuid(value: String): Boolean = UUID_REGEX.matches(value.trim())

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(StatusPages) {
        exception<LocationValidationException> { call, cause ->
            call.respond(
                cause.statusCode,
                ProblemDetailsResponse(
                    type = cause.typeUri,
                    title = cause.title,
                    status = cause.statusCode.value,
                    detail = cause.message,
                    instance = call.request.path()
                )
            )
        }

        exception<SerializationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ProblemDetailsResponse(
                    type = "urn:problem-type:malformed-json",
                    title = "Malformed JSON Request",
                    status = HttpStatusCode.BadRequest.value,
                    detail = cause.message?.replace("\"", "'") ?: "O payload enviado é um JSON malformado ou incompatível.",
                    instance = call.request.path()
                )
            )
        }

        exception<BadRequestException> { call, cause ->
            val isSerialization = cause.cause is SerializationException ||
                cause.cause?.cause is SerializationException ||
                cause.message?.contains("convert", ignoreCase = true) == true ||
                cause.message?.contains("json", ignoreCase = true) == true ||
                cause.cause?.javaClass?.simpleName?.contains("Json", ignoreCase = true) == true ||
                cause.cause?.javaClass?.simpleName?.contains("Convert", ignoreCase = true) == true

            val typeUri = if (isSerialization) "urn:problem-type:malformed-json" else "urn:problem-type:bad-request"
            val title = if (isSerialization) "Malformed JSON Request" else "Bad Request"

            call.respond(
                HttpStatusCode.BadRequest,
                ProblemDetailsResponse(
                    type = typeUri,
                    title = title,
                    status = HttpStatusCode.BadRequest.value,
                    detail = cause.message?.replace("\"", "'") ?: "Requisição inválida.",
                    instance = call.request.path()
                )
            )
        }

        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ProblemDetailsResponse(
                    type = "urn:problem-type:internal-server-error",
                    title = "Internal Server Error",
                    status = HttpStatusCode.InternalServerError.value,
                    detail = cause.message?.replace("\"", "'") ?: "Erro interno inesperado no servidor.",
                    instance = call.request.path()
                )
            )
        }
    }

    routing {
        get("/healthz") {
            call.respond(
                HttpStatusCode.OK,
                HealthResponse(
                    status = "UP",
                    service = "location-service-kotlin",
                    port = 8104
                )
            )
        }
        post("/v1/location-events") {
            val rawContentType = call.request.headers[HttpHeaders.ContentType]
            if (rawContentType.isNullOrBlank()) {
                throw MissingContentTypeException()
            }
            val parsedContentType = runCatching { ContentType.parse(rawContentType) }.getOrNull()
            if (parsedContentType == null || parsedContentType.withoutParameters() != ContentType.Application.Json) {
                throw InvalidContentTypeException(rawContentType)
            }

            val correlationId = call.request.headers["X-Correlation-ID"]
            if (correlationId.isNullOrBlank()) {
                throw MissingHeaderException("X-Correlation-ID")
            }
            if (!isValidUuid(correlationId)) {
                throw InvalidHeaderException("X-Correlation-ID", "O valor '$correlationId' não é um UUID v4 válido.")
            }

            val requestDto = call.receive<LocationEventRequestDto>()
            val domainEvent = requestDto.toDomain()
            val result = HaversineEngine.evaluate(domainEvent, correlationId)

            call.respond(HttpStatusCode.OK, result.toDto())
        }
    }
}
