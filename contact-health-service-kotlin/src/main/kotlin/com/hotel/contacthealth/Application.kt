// src/main/kotlin/com/hotel/contacthealth/Application.kt
package com.hotel.contacthealth

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
        })
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error = "INVALID_PAYLOAD", message = cause.message ?: "Parâmetros inválidos.")
            )
        }
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error = "INVALID_PAYLOAD", message = cause.message ?: "JSON malformado ou campos ausentes.")
            )
        }
    }

    val evaluator = HygieneEvaluator()

    routing {
        get("/healthz") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "UP"))
        }

        post("/v1/contact-evaluations") {
            val correlationId = call.request.headers["X-Correlation-ID"] ?: UUID.randomUUID().toString()
            val request = call.receive<ContactEvaluationRequest>()
            val response = evaluator.evaluate(request, correlationId)
            call.respond(HttpStatusCode.OK, response)
        }
    }
}