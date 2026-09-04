package com.hotel.contacthealth

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    @DisplayName("GET /healthz deve responder HTTP 200 OK com status UP")
    fun testHealthCheck() = testApplication {
        application {
            module()
        }

        val response = client.get("/healthz")

        // 1. Validar Status HTTP
        assertEquals(HttpStatusCode.OK, response.status)

        // 2. Validar Content-Type de forma tolerante (JSON)
        val contentType = response.contentType()
        assertTrue(
            contentType?.match(ContentType.Application.Json) == true,
            "Esperado Content-Type application/json, mas foi: $contentType"
        )

        // 3. Validar Corpo da Resposta (ignorando espaços ou quebras)
        assertEquals("""{"status":"UP"}""", response.bodyAsText().trim())
    }
}
