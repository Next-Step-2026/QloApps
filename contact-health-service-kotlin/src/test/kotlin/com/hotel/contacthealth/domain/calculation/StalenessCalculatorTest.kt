package com.hotel.contacthealth.domain.calculation

import com.hotel.contacthealth.model.FactorStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("Staleness Calculator Domain Tests")
class StalenessCalculatorTest {

    private val referenceDate = LocalDate.parse("2026-08-27")

    @Nested
    @DisplayName("Boundary Calculations for Staleness and Aging")
    inner class Boundaries {

        @Test
        @DisplayName("Boundary: Exactly 30 days should be FRESH with 0 issues")
        fun shouldClassify30DaysAsFresh() {
            val result = StalenessCalculator.calculate("2026-07-28", referenceDate)
            assertEquals(30L, result.daysSince)
            assertEquals(FactorStatus.FRESH, result.status)
            assertNull(result.issue)
        }

        @Test
        @DisplayName("Boundary: Exactly 31 days should be AGING with issue STALENESS_EXCEEDED_30_DAYS")
        fun shouldClassify31DaysAsAging() {
            val result = StalenessCalculator.calculate("2026-07-27", referenceDate)
            assertEquals(31L, result.daysSince)
            assertEquals(FactorStatus.AGING, result.status)
            assertEquals("STALENESS_EXCEEDED_30_DAYS", result.issue)
        }

        @Test
        @DisplayName("Boundary: Exactly 90 days should still be AGING")
        fun shouldClassify90DaysAsAging() {
            val result = StalenessCalculator.calculate("2026-05-29", referenceDate)
            assertEquals(90L, result.daysSince)
            assertEquals(FactorStatus.AGING, result.status)
            assertEquals("STALENESS_EXCEEDED_30_DAYS", result.issue)
        }

        @Test
        @DisplayName("Boundary: Exactly 91 days should be STALE with issue STALENESS_EXCEEDED_90_DAYS")
        fun shouldClassify91DaysAsStale() {
            val result = StalenessCalculator.calculate("2026-05-28", referenceDate)
            assertEquals(91L, result.daysSince)
            assertEquals(FactorStatus.STALE, result.status)
            assertEquals("STALENESS_EXCEEDED_90_DAYS", result.issue)
        }

        @Test
        @DisplayName("Null last_verified_at should default to 180 days and STALE")
        fun shouldDefaultTo180DaysWhenNull() {
            val result = StalenessCalculator.calculate(null, referenceDate)
            assertEquals(180L, result.daysSince)
            assertEquals(FactorStatus.STALE, result.status)
            assertEquals("STALENESS_EXCEEDED_90_DAYS", result.issue)
        }

        @Test
        @DisplayName("Temporal inconsistency: last_verified_at in future should throw IllegalArgumentException")
        fun shouldThrowWhenLastVerifiedAtIsInFuture() {
            val exception = assertThrows<IllegalArgumentException> {
                StalenessCalculator.calculate("2026-09-10", referenceDate)
            }
            assertTrue(exception.message?.contains("future") == true)
        }

        @Test
        @DisplayName("Temporal distance over 1 year (730 days) should calculate correctly and mark STALE")
        fun shouldHandleLargeGapsOverOneYear() {
            val result = StalenessCalculator.calculate("2024-08-27", referenceDate)
            assertEquals(730L, result.daysSince)
            assertEquals(FactorStatus.STALE, result.status)
            assertEquals("STALENESS_EXCEEDED_90_DAYS", result.issue)
        }
    }

    @Nested
    @DisplayName("Date Parsing Flexibility")
    inner class DateParsing {

        @Test
        @DisplayName("Should parse ISO local date, date-time, and UTC timestamps")
        fun shouldParseVariousValidFormats() {
            assertEquals(LocalDate.of(2026, 8, 20), StalenessCalculator.parseDate("2026-08-20"))
            assertEquals(LocalDate.of(2026, 8, 20), StalenessCalculator.parseDate("2026-08-20T10:00:00Z"))
            assertEquals(LocalDate.of(2026, 8, 20), StalenessCalculator.parseDate("2026-08-20T10:00:00"))
        }

        @Test
        @DisplayName("Should parse MySQL datetime format with space and appended Z sent by PHP")
        fun shouldParseMysqlDatetimeFormats() {
            assertEquals(LocalDate.of(2026, 8, 20), StalenessCalculator.parseDate("2026-08-20 10:00:00Z"))
            assertEquals(LocalDate.of(2026, 8, 20), StalenessCalculator.parseDate("2026-08-20 10:00:00"))
        }

        @Test
        @DisplayName("Should return null for null or blank date strings")
        fun shouldReturnNullForBlank() {
            assertNull(StalenessCalculator.parseDate(null))
            assertNull(StalenessCalculator.parseDate("   "))
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException on invalid date formats")
        fun shouldThrowOnInvalidFormat() {
            val exception = assertThrows<IllegalArgumentException> {
                StalenessCalculator.parseDate("2026/08/27", "test_field")
            }
            assertTrue(exception.message?.contains("test_field") == true)
        }
    }
}
