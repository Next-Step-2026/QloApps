package com.hotel.contacthealth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    println("Iniciando Contact Health Service em http://127.0.0.1:8103 ...")

    embeddedServer(Netty, port = 8103, host = "127.0.0.1") {
        routing {
            // Endpoint de Healthcheck (DoD Item 1)
            get("/healthz") {
                call.respondText(
                    text = """{"status":"UP"}""",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )
            }
        }
    }.start(wait = true)
}
