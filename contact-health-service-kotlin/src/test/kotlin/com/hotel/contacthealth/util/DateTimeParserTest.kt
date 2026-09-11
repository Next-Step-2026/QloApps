package com.hotel.contacthealth.util

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("DateTimeParser Utility Unit Tests")
class DateTimeParserTest {

    @Test
    @DisplayName("Null, empty or blank strings should return null")
    fun shouldReturnNullForBlankInputs() {
        assertNull(DateTimeParser.parseDate(null))
        assertNull(DateTimeParser.parseDate(""))
        assertNull(DateTimeParser.parseDate("   "))
    }

    @Test
    @DisplayName("Valid ISO local date should parse correctly")
    fun shouldParseIsoLocalDate() {
        val parsed = DateTimeParser.parseDate("2026-08-27")
        assertEquals(LocalDate.of(2026, 8, 27), parsed)
    }

    @Test
    @DisplayName("Valid ISO datetime with offset should parse correctly")
    fun shouldParseIsoOffsetDateTime() {
        val parsed = DateTimeParser.parseDate("2026-08-27T15:30:00-03:00")
        assertEquals(LocalDate.of(2026, 8, 27), parsed)
    }

    @Test
    @DisplayName("Valid ISO datetime UTC with Z should parse correctly")
    fun shouldParseIsoUtcDateTime() {
        val parsed = DateTimeParser.parseDate("2026-08-27T10:00:00Z")
        assertEquals(LocalDate.of(2026, 8, 27), parsed)
    }

    @Test
    @DisplayName("Valid ISO local datetime without offset should parse correctly")
    fun shouldParseIsoLocalDateTime() {
        val parsed = DateTimeParser.parseDate("2026-08-27T10:00:00")
        assertEquals(LocalDate.of(2026, 8, 27), parsed)
    }

    @Test
    @DisplayName("MySQL datetime with space should parse correctly")
    fun shouldParseMysqlDatetimeWithSpace() {
        val parsed = DateTimeParser.parseDate("2026-08-27 10:00:00")
        assertEquals(LocalDate.of(2026, 8, 27), parsed)
    }

    @Test
    @DisplayName("Invalid date strings should throw IllegalArgumentException with field name")
    fun shouldThrowOnInvalidDate() {
        val ex = assertThrows<IllegalArgumentException> {
            DateTimeParser.parseDate("not-a-date", "test_field")
        }
        assertTrue(ex.message!!.contains("test_field"))
        assertTrue(ex.message!!.contains("not-a-date"))
    }
}
