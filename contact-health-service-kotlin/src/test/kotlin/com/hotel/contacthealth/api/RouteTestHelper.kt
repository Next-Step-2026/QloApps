package com.hotel.contacthealth.api

import com.hotel.contacthealth.module
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*

suspend fun ApplicationTestBuilder.withTestApp(testBlock: suspend HttpClient.() -> Unit) {
    application { module() }
    client.testBlock()
}

suspend fun HttpClient.postEvaluation(
    payload: String,
    correlationId: String? = "test-corr-id",
    contentType: ContentType = ContentType.Application.Json
): HttpResponse = post("/v1/contact-evaluations") {
    contentType(contentType)
    correlationId?.let { header("X-Correlation-ID", it) }
    setBody(payload)
}

fun jsonPayload(
    customerId: String? = "cust-1042",
    email: String? = "carlos.silva@empresa.com.br",
    phone: String? = "+5511987654321",
    lastVerifiedAt: String? = "2026-08-20T10:00:00Z",
    consentExpiresAt: String? = "2027-01-01T00:00:00Z",
    referenceDate: String? = "2026-08-27"
): String {
    fun field(name: String, value: String?) = when (value) {
        null -> """ "$name": null """
        else -> """ "$name": "$value" """
    }
    return """
    {
        ${field("customer_id", customerId)},
        ${field("email", email)},
        ${field("phone", phone)},
        ${field("last_verified_at", lastVerifiedAt)},
        ${field("consent_expires_at", consentExpiresAt)},
        ${field("reference_date", referenceDate)}
    }
    """.trimIndent()
}
