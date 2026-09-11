package com.hotel.contacthealth.util

import com.hotel.contacthealth.model.ContactEvaluationResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.PrintStream
import java.time.Instant
import java.util.Locale

@Serializable
data class ContactEvaluatedLog(
    val timestamp: String,
    val level: String = "INFO",
    @SerialName("correlation_id") val correlationId: String,
    val event: String = "CONTACT_EVALUATED",
    @SerialName("customer_id") val customerId: String,
    @SerialName("overall_status") val overallStatus: String,
    @SerialName("hygiene_score") val hygieneScore: Int,
    @SerialName("duration_ms") val durationMs: Double
)

object StructuredLogger {
    private val json = Json { encodeDefaults = true }
    var outputStream: PrintStream = System.out

    fun logEvaluation(
        correlationId: String,
        customerId: String,
        response: ContactEvaluationResponse,
        durationMs: Double
    ) {
        val roundedDuration = String.format(Locale.US, "%.2f", durationMs).toDouble()
        val logEntry = ContactEvaluatedLog(
            timestamp = Instant.now().toString(),
            correlationId = correlationId,
            customerId = customerId,
            overallStatus = response.overallStatus.name,
            hygieneScore = response.hygieneScore,
            durationMs = roundedDuration
        )
        outputStream.println(json.encodeToString(logEntry))
    }
}
