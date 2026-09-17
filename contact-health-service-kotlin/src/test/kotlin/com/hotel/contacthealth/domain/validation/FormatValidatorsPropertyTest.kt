package com.hotel.contacthealth.domain.validation

import io.kotest.property.Arb
import io.kotest.property.arbitrary.email
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class FormatValidatorsPropertyTest {

    @Test
    fun `isValidEmail never crashes for arbitrary generated strings`() {
        runBlocking {
            forAll(Arb.string()) { input ->
                val result = FormatValidators.isValidEmail(input)
                result == true || result == false
            }
        }
    }

    @Test
    fun `isValidEmail handles generated email properties consistently`() {
        runBlocking {
            forAll(Arb.email()) { email ->
                val firstRun = FormatValidators.isValidEmail(email)
                val secondRun = FormatValidators.isValidEmail(email)
                firstRun == secondRun
            }
        }
    }
}
