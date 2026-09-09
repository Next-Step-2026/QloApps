package com.hotel.location

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Testes de Integração da API - Healthcheck")
class ApplicationIntegrationTest {

    @Test
    fun `deve responder 200 OK no healthcheck`() = testApplication {
        application { module() }

        val response = client.get("/healthz")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
