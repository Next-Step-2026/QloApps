package com.hotel.contacthealth.domain.validation

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("Consent Validator Domain Tests (LGPD)")
class ConsentValidatorTest {

    private val referenceDate = LocalDate.parse("2026-08-27")

    @Nested
    @DisplayName("Absence or Blankness of Consent")
    inner class AbsenceOrBlankness {

        @Test
        @DisplayName("Null consent_expires_at must be treated as invalid and expired")
        fun shouldTreatNullConsentAsInvalidAndExpired() {
            val result = ConsentValidator.validate(null, referenceDate)
            assertFalse(result.isValid)
            assertTrue(result.isExpired)
        }

        @Test
        @DisplayName("Empty string consent_expires_at must be treated as invalid and expired")
        fun shouldTreatEmptyConsentAsInvalidAndExpired() {
            val result = ConsentValidator.validate("", referenceDate)
            assertFalse(result.isValid)
            assertTrue(result.isExpired)
        }

        @Test
        @DisplayName("Blank whitespace consent_expires_at must be treated as invalid and expired")
        fun shouldTreatBlankConsentAsInvalidAndExpired() {
            val result = ConsentValidator.validate("   ", referenceDate)
            assertFalse(result.isValid)
            assertTrue(result.isExpired)
        }
    }

    @Nested
    @DisplayName("Expiration Boundaries Relative to Reference Date")
    inner class ExpirationBoundaries {

        @Test
        @DisplayName("Consent expiring exactly on reference_date is still valid (not expired yet)")
        fun shouldConsiderConsentValidWhenExpiringOnReferenceDate() {
            val result = ConsentValidator.validate("2026-08-27", referenceDate)
            assertTrue(result.isValid)
            assertFalse(result.isExpired)
        }

        @Test
        @DisplayName("Consent expiring one day after reference_date is valid")
        fun shouldConsiderConsentValidWhenExpiringInFuture() {
            val result = ConsentValidator.validate("2026-08-28", referenceDate)
            assertTrue(result.isValid)
            assertFalse(result.isExpired)
        }

        @Test
        @DisplayName("Consent expiring one day before reference_date is expired")
        fun shouldConsiderConsentExpiredWhenExpiringOneDayBefore() {
            val result = ConsentValidator.validate("2026-08-26", referenceDate)
            assertFalse(result.isValid)
            assertTrue(result.isExpired)
        }

        @Test
        @DisplayName("Consent with full ISO-8601 timestamp in the future is valid")
        fun shouldHandleIsoTimestampInFuture() {
            val result = ConsentValidator.validate("2027-01-01T00:00:00Z", referenceDate)
            assertTrue(result.isValid)
            assertFalse(result.isExpired)
        }

        @Test
        @DisplayName("Consent with full ISO-8601 timestamp in the past is expired")
        fun shouldHandleIsoTimestampInPast() {
            val result = ConsentValidator.validate("2026-06-01T10:00:00Z", referenceDate)
            assertFalse(result.isValid)
            assertTrue(result.isExpired)
        }
    }
}
