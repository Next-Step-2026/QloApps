package com.hotel.contacthealth.domain.validation

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("Format Validators Domain Tests")
class FormatValidatorsTest {

    @Nested
    @DisplayName("Email Format Validation")
    inner class EmailValidation {

        @Test
        @DisplayName("Should accept valid email addresses")
        fun shouldAcceptValidEmails() {
            assertTrue(FormatValidators.isValidEmail("user@example.com"))
            assertTrue(FormatValidators.isValidEmail("carlos.silva@empresa.com.br"))
            assertTrue(FormatValidators.isValidEmail("marina_costa+tag@tech.co"))
        }

        @Test
        @DisplayName("Should reject email containing whitespace inside")
        fun shouldRejectEmailWithSpaceInside() {
            assertFalse(FormatValidators.isValidEmail("user name@example.com"))
        }

        @Test
        @DisplayName("Should reject email with leading or trailing whitespace")
        fun shouldRejectEmailWithLeadingOrTrailingWhitespace() {
            assertFalse(FormatValidators.isValidEmail(" user@example.com"))
            assertFalse(FormatValidators.isValidEmail("user@example.com "))
        }

        @Test
        @DisplayName("Should reject email with newline (header injection attempt)")
        fun shouldRejectEmailWithNewline() {
            assertFalse(FormatValidators.isValidEmail("user\n@example.com"))
        }

        @Test
        @DisplayName("Should reject email with CRLF (header injection attempt)")
        fun shouldRejectEmailWithCarriageReturn() {
            assertFalse(FormatValidators.isValidEmail("user\r\n@example.com"))
        }

        @Test
        @DisplayName("Should reject email missing @ or domain")
        fun shouldRejectEmailWithoutAtOrDomain() {
            assertFalse(FormatValidators.isValidEmail("userexample.com"))
            assertFalse(FormatValidators.isValidEmail("user@"))
            assertFalse(FormatValidators.isValidEmail("@example.com"))
        }

        @Test
        @DisplayName("Should reject blank or empty email")
        fun shouldRejectBlankEmail() {
            assertFalse(FormatValidators.isValidEmail(""))
            assertFalse(FormatValidators.isValidEmail("   "))
        }
    }

    @Nested
    @DisplayName("Phone Format Validation and Normalization")
    inner class PhoneValidation {

        @Test
        @DisplayName("Should normalize phone numbers by removing spaces, hyphens and parentheses")
        fun shouldNormalizePhoneCorrectly() {
            assertEquals("+5511991234567", FormatValidators.normalizePhone("+55 11 99123-4567"))
            assertEquals("+5511991234567", FormatValidators.normalizePhone("+55(11)99123-4567"))
        }

        @Test
        @DisplayName("Should accept valid formatted Brazilian E.164 phone numbers")
        fun shouldAcceptValidBrazilianPhones() {
            assertTrue(FormatValidators.isValidPhone("+55 11 99123-4567"))
            assertTrue(FormatValidators.isValidPhone("+5511991234567"))
        }

        @Test
        @DisplayName("Should accept valid international E.164 phone numbers (US +1, UK +44)")
        fun shouldAcceptValidInternationalPhones() {
            assertTrue(FormatValidators.isValidPhone("+14155552671"))
            assertTrue(FormatValidators.isValidPhone("+44 20 7183 8750"))
        }

        @Test
        @DisplayName("Should reject phone numbers missing the leading '+'")
        fun shouldRejectPhoneWithoutLeadingPlus() {
            assertFalse(FormatValidators.isValidPhone("5511999998888"))
        }

        @Test
        @DisplayName("Should reject phone numbers containing letters")
        fun shouldRejectPhoneWithLetters() {
            assertFalse(FormatValidators.isValidPhone("+551199999ABCD"))
        }

        @Test
        @DisplayName("Should reject phone numbers with too few digits (< 8 digits)")
        fun shouldRejectPhoneWithTooFewDigits() {
            assertFalse(FormatValidators.isValidPhone("+551199"))
        }

        @Test
        @DisplayName("Should reject phone numbers with too many digits (> 15 digits)")
        fun shouldRejectPhoneWithTooManyDigits() {
            assertFalse(FormatValidators.isValidPhone("+5511999998888777766"))
        }

        @Test
        @DisplayName("Should reject phone containing special characters like '#'")
        fun shouldRejectPhoneWithSpecialCharacters() {
            assertFalse(FormatValidators.isValidPhone("+55(11)99999#8888"))
        }
    }

    @Nested
    @DisplayName("Contact Masking")
    inner class ContactMasking {

        @Test
        @DisplayName("Should mask email address showing first and last char of user and full domain")
        fun shouldMaskEmailProperly() {
            assertEquals("m***a@tech.com", FormatValidators.maskEmail("marina.costa@tech.com"))
        }

        @Test
        @DisplayName("Should fallback to generic mask when email user part has 2 or fewer characters")
        fun shouldMaskShortEmailProperly() {
            assertEquals("***@tech.com", FormatValidators.maskEmail("ab@tech.com"))
        }

        @Test
        @DisplayName("Should mask phone preserving international prefix and last 4 digits")
        fun shouldMaskPhoneProperly() {
            assertEquals("+5511*****4567", FormatValidators.maskPhone("+55 11 99123-4567"))
        }

        @Test
        @DisplayName("Should return generic mask when phone has fewer than 8 digits")
        fun shouldMaskShortPhoneProperly() {
            assertEquals("****", FormatValidators.maskPhone("+55123"))
        }
    }
}
