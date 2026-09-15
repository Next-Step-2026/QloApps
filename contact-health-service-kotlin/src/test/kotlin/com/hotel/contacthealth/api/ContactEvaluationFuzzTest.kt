package com.hotel.contacthealth.api

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.hotel.contacthealth.model.ContactEvaluationRequest
import com.hotel.contacthealth.service.HygieneEvaluator
import kotlinx.serialization.json.Json

class ContactEvaluationFuzzTest {

    @FuzzTest(maxDuration = "5s")
    fun fuzzEvaluationRequest(data: FuzzedDataProvider) {
        val rawJson = data.consumeRemainingAsString()
        val evaluator = HygieneEvaluator()

        try {
            val request = Json.decodeFromString<ContactEvaluationRequest>(rawJson)
            evaluator.evaluate(request, "fuzz-correlation-id")
        } catch (e: Exception) {
            // Expected serialization or domain validation errors are caught gracefully.
            // Jazzer fails only if an unhandled JVM crash or memory corruption occurs.
        }
    }
}
