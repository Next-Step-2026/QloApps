package com.hotel.contacthealth

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.jupiter.api.Test


class AplicationTest {

    @Test
    fun `GET healthz deve retornar status 200 OK e JSON com status OK`() = testApplication {

        application {
            module()
        }

        val response = client.get("/healthz")

        assertEquals(HttpStatusCode.OK, response.status)

        assertEquals(
            ContentType.Application.Json.withCharset(
                Charsets.UTF_8
            ),
            response.contentType()
        )

        val body = response.bodyAsText()

        assertTrue(
            body.contains("\"status\": \"OK\"") ||
                    body.contains("\"status\":\"OK\"")
        )
    }
}