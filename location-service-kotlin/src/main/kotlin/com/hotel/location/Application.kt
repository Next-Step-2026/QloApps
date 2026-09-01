package com.hotel.location

import com.hotel.location.model.ErrorResponse
import com.hotel.location.model.HealthResponse
import com.hotel.location.model.LocationEventRequest
import com.hotel.location.service.HaversineEngine
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main() {
    println(">>> Iniciando Servidor Ktor de Geofencing em http://127.0.0.1:8104 ...")
    embeddedServer(Netty, port = 8104, host = "127.0.0.1") {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
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
                try {
                    val req = call.receive<LocationEventRequest>()
                    val correlationId = call.request.headers["X-Correlation-ID"] ?: "corr-${System.currentTimeMillis()}"

                    if (!HaversineEngine.areCoordinatesValid(req)) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(
                                error = "INVALID_COORDINATES",
                                message = "Latitude deve estar entre -90 e 90, e longitude entre -180 e 180."
                            )
                        )
                        return@post
                    }

                    val response = HaversineEngine.processLocationEvent(req, correlationId)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(
                            error = "MALFORMED_JSON",
                            message = e.message?.replace("\"", "'") ?: "Erro ao processar requisição JSON"
                        )
                    )
                }
            }
        }
    }.start(wait = true)
}
