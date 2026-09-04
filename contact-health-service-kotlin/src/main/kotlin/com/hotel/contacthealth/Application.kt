package com.hotel.contacthealth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = 8103, host = "127.0.0.1", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    routing {
        get("/healthz") {
            call.respondText(
                text = """{"status":"UP"}""",
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK
            )
        }
    }
}
