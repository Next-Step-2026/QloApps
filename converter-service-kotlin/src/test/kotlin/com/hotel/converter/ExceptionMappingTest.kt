package com.hotel.converter

import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExceptionMappingTest {
    @Test
    fun `SerializationException maps to INVALID_SCHEMA with malformed json prefix`() {
        val ex = SerializationException("Unexpected token")
        val error = ex.toValidationError()

        assertEquals("payload", error.field)
        assertEquals("INVALID_SCHEMA", error.errorCode)
        assertEquals("Malformed JSON: Unexpected token", error.message)
    }

    @Test
    fun `IllegalStateException with message preserves message`() {
        val ex = IllegalStateException("Channel temporarily disabled")
        val error = ex.toValidationError()

        assertEquals("payload", error.field)
        assertEquals("INVALID_SCHEMA", error.errorCode)
        assertEquals("Channel temporarily disabled", error.message)
    }

    @Test
    fun `IllegalStateException without message falls back to Invalid state`() {
        val ex = IllegalStateException()
        val error = ex.toValidationError()

        assertEquals("payload", error.field)
        assertEquals("INVALID_SCHEMA", error.errorCode)
        assertEquals("Invalid state", error.message)
    }

    @Test
    fun `IllegalArgumentException with message preserves message`() {
        val ex = IllegalArgumentException("Invalid parameter")
        val error = ex.toValidationError()

        assertEquals("payload", error.field)
        assertEquals("INVALID_SCHEMA", error.errorCode)
        assertEquals("Invalid parameter", error.message)
    }

    @Test
    fun `IllegalArgumentException without message falls back to Invalid argument`() {
        val ex = IllegalArgumentException()
        val error = ex.toValidationError()

        assertEquals("payload", error.field)
        assertEquals("INVALID_SCHEMA", error.errorCode)
        assertEquals("Invalid argument", error.message)
    }

    @Test
    fun `generic Exception without message falls back to Request processing error`() {
        val ex = RuntimeException()
        val error = ex.toValidationError()

        assertEquals("payload", error.field)
        assertEquals("INVALID_SCHEMA", error.errorCode)
        assertEquals("Request processing error", error.message)
    }
}
