package com.hotel.location.fuzz

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.hotel.location.dto.LocationEventRequestDto
import com.hotel.location.exception.DomainException
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

        val validLat1 = HaversineEngine.isValidLatitude(lat1)
        val validLon1 = HaversineEngine.isValidLongitude(lon1)
        val validLat2 = HaversineEngine.isValidLatitude(lat2)
        val validLon2 = HaversineEngine.isValidLongitude(lon2)

        if (validLat1 && validLon1 && validLat2 && validLon2) {
            val distance = HaversineEngine.calculateDistanceMeters(lat1, lon1, lat2, lon2)
            check(!distance.isNaN()) { "Distância gerou NaN para coordenadas válidas: ($lat1, $lon1) -> ($lat2, $lon2)" }
            check(distance.isFinite()) { "Distância gerou valor infinito para coordenadas válidas" }
            check(distance >= 0.0) { "Distância negativa gerada: $distance" }
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
        } catch (e: SerializationException) {
            // Falha esperada de desserialização JSON em entradas malformadas
        } catch (e: DomainException) {
            // Rejeição controlada de domínio por validações de regras de negócio da RFC-004
        } catch (e: IllegalArgumentException) {
            // Rejeição de tipos ou argumentos inválidos
        }
    }
}
