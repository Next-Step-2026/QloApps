package com.hotel.contacthealth.domain.calculation

import com.hotel.contacthealth.model.FactorStatus
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.enum
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ScoreCalculatorPropertyTest {

    @Test
    fun `calculated score is always bounded between 0 and 100 for all factor status combinations`() {
        runBlocking {
            forAll(
                Arb.enum<FactorStatus>(),
                Arb.enum<FactorStatus>(),
                Arb.boolean()
            ) { emailStatus, phoneStatus, consentValid ->
                val score = ScoreCalculator.calculate(emailStatus, phoneStatus, consentValid)
                score in 0..100
            }
        }
    }
}
