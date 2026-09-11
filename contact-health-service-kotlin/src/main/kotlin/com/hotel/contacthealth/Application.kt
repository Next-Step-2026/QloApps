package com.hotel.contacthealth

import com.hotel.contacthealth.model.ContactEvaluationRequest
import com.hotel.contacthealth.model.ErrorResponse
import com.hotel.contacthealth.service.HygieneEvaluator
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.UUID

fun main() {
    embeddedServer(Netty, port = 8103, host = "127.0.0.1", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        })
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error = "INVALID_PAYLOAD", message = cause.message ?: "Invalid parameters.")
            )
        }
        exception<SerializationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error = "INVALID_PAYLOAD", message = "Malformed JSON or incompatible types: ${cause.message}")
            )
        }
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error = "INVALID_PAYLOAD", message = cause.message ?: "Malformed request.")
            )
        }
        exception<Throwable> { call, _ ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(error = "INTERNAL_SERVER_ERROR", message = "Internal server error.")
            )
        }
    }

    val evaluator = HygieneEvaluator()

    routing {
        get("/healthz") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "UP"))
        }

        post("/v1/contact-evaluations") {
            val contentType = call.request.contentType()
            if (!contentType.match(ContentType.Application.Json)) {
                call.respond(
                    HttpStatusCode.UnsupportedMediaType,
                    ErrorResponse("UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json.")
                )
                return@post
            }

            val correlationId = call.request.headers["X-Correlation-ID"]
            if (correlationId.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("INVALID_PAYLOAD", "Header 'X-Correlation-ID' is required and cannot be blank.")
                )
                return@post
            }

            val isValidUuidV4 = runCatching {
                val parsed = UUID.fromString(correlationId.trim())
                parsed.version() == 4
            }.getOrDefault(false)

            if (!isValidUuidV4) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("INVALID_PAYLOAD", "Header 'X-Correlation-ID' must be a valid UUIDv4 (RFC 4122).")
                )
                return@post
            }

            val request = call.receive<ContactEvaluationRequest>()
            request.validate()
            val response = evaluator.evaluate(request, correlationId.trim())
            call.respond(HttpStatusCode.OK, response)
        }
    }
}