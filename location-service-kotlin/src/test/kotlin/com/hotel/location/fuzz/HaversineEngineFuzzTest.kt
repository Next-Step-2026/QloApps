package com.hotel.location.fuzz

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.hotel.location.dto.LocationEventRequestDto
import com.hotel.location.exception.DomainException
import com.hotel.location.exception.InvalidCoordinatesException
import com.hotel.location.model.Coordinates
import com.hotel.location.service.HaversineEngine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class HaversineEngineFuzzTest {

    private val json = Json { ignoreUnknownKeys = true }

    @FuzzTest(maxDuration = "5s")
    fun fuzzHaversineCoordinatesAndDistance(data: FuzzedDataProvider) {
        val lat1 = data.consumeDouble()
        val lon1 = data.consumeDouble()
        val lat2 = data.consumeDouble()
        val lon2 = data.consumeDouble()

        val c1 = try {
            Coordinates(lat1, lon1)
        } catch (e: InvalidCoordinatesException) {
            null
        }

        val c2 = try {
            Coordinates(lat2, lon2)
        } catch (e: InvalidCoordinatesException) {
            null
        }

        if (c1 != null && c2 != null) {
            val distance = HaversineEngine.calculateDistanceMeters(c1, c2)
            check(!distance.isNaN()) { "Distância gerou NaN para coordenadas válidas: $c1 -> $c2" }
            check(distance.isFinite()) { "Distância gerou valor infinito para coordenadas válidas" }
            check(distance >= 0.0) { "Distância negativa gerada: $distance" }
        }
    }

    @FuzzTest(maxDuration = "5s")
    fun fuzzStructuredLocationEventDtoAndDomain(data: FuzzedDataProvider) {
        val hotelId = if (data.consumeBoolean()) data.consumeString(30) else null
        val hotelLat = if (data.consumeBoolean()) data.consumeDouble() else null
        val hotelLng = if (data.consumeBoolean()) data.consumeDouble() else null
        val guestLat = if (data.consumeBoolean()) data.consumeDouble() else null
        val guestLng = if (data.consumeBoolean()) data.consumeDouble() else null
        val radius = if (data.consumeBoolean()) data.consumeDouble() else null
        val prevState = if (data.consumeBoolean()) {
            data.pickValue(arrayOf("inside", "outside", "INSIDE", "OUTSIDE", "invalid", "", "   ", null))
        } else null

        val dto = LocationEventRequestDto(
            hotel_id = hotelId,
            hotel_lat = hotelLat,
            hotel_lng = hotelLng,
            guest_lat = guestLat,
            guest_lng = guestLng,
            geofence_radius_m = radius,
            previous_state = prevState
        )

        try {
            val domain = dto.toDomain()
            val result = HaversineEngine.evaluate(domain, "fuzz-structured-corr")
            check(!result.distanceMeters.isNaN()) { "Distância calculada foi NaN" }
            check(result.distanceMeters.isFinite()) { "Distância calculada foi infinita" }
            check(result.distanceMeters >= 0.0) { "Distância calculada foi negativa" }
            check(result.alertTriggered == (result.transition == com.hotel.location.model.GeofenceTransition.ENTERED)) {
                "Invariante RFC-004 violada: alertTriggered=${result.alertTriggered} com transição=${result.transition}"
            }
        } catch (e: DomainException) {
            // Rejeições de domínio esperadas pela RFC-004
        }
    }

    @FuzzTest(maxDuration = "5s")
    fun fuzzJsonPayloadDeserializationAndEvaluation(data: FuzzedDataProvider) {
        val rawPayload = data.consumeRemainingAsString()

        try {
            val dto = json.decodeFromString<LocationEventRequestDto>(rawPayload)
            val domainEvent = dto.toDomain()
            val result = HaversineEngine.evaluate(domainEvent, "fuzz-correlation-id")
            check(!result.distanceMeters.isNaN()) { "Resultado de distância foi NaN" }
            check(result.distanceMeters.isFinite()) { "Resultado de distância foi infinito" }
            check(result.alertTriggered == (result.transition == com.hotel.location.model.GeofenceTransition.ENTERED)) {
                "Invariante RFC-004 violada no fluxo JSON"
            }
        } catch (e: SerializationException) {
            // Falha esperada de desserialização JSON em entradas malformadas
        } catch (e: DomainException) {
            // Rejeição controlada de domínio por validações de regras de negócio da RFC-004
        }
    }

    @FuzzTest(maxDuration = "5s")
    fun fuzzHeaderValidationAndNormalization(data: FuzzedDataProvider) {
        val rawHeader = data.consumeRemainingAsString()
        val isValid = com.hotel.location.isValidUuid(rawHeader)
        if (isValid) {
            val trimmed = rawHeader.trim()
            check(trimmed.length == 36) { "UUID v4 válido deve ter exatamente 36 caracteres (obtido: $trimmed)" }
            check(trimmed[14] == '4') { "UUID v4 deve possuir versão 4 no caractere 14 (obtido: $trimmed)" }
            check(trimmed[19] in "89abAB") { "UUID v4 deve possuir variante válida [89abAB] no caractere 19 (obtido: $trimmed)" }
        }
    }
}

