package com.hotel.location

import com.hotel.location.exception.InvalidCoordinatesException
import com.hotel.location.exception.InvalidGeofenceRadiusException
import com.hotel.location.model.Coordinates
import com.hotel.location.model.GeofenceState
import com.hotel.location.model.GeofenceTransition
import com.hotel.location.model.LocationEvent
import com.hotel.location.service.HaversineEngine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HaversineEngineTest {

    @Test
    fun `deve calcular distancia aproximada de 108 metros para hospede proximo`() {
        val hotelLat = -8.052240
        val hotelLng = -34.885650
        val guestLat = -8.053100
        val guestLng = -34.886100

        val distance = HaversineEngine.calculateDistanceMeters(hotelLat, hotelLng, guestLat, guestLng)

        assertEquals(107.7, distance, 1.5, "A distância calculada deve ser aproximadamente 108 metros.")
    }

    @Test
    fun `deve calcular distancia aproximada de 1500 metros para hospede distante`() {
        val hotelLat = -8.052240
        val hotelLng = -34.885650
        val guestLat = -8.065000
        val guestLng = -34.890000

        val distance = HaversineEngine.calculateDistanceMeters(hotelLat, hotelLng, guestLat, guestLng)

        assertEquals(1497.5, distance, 5.0, "A distância calculada deve ser aproximadamente 1500 metros.")
    }

    @Test
    fun `deve retornar zero quando as coordenadas forem identicas`() {
        val lat = -8.052240
        val lng = -34.885650

        val distance = HaversineEngine.calculateDistanceMeters(lat, lng, lat, lng)

        assertEquals(0.0, distance, 0.001, "A distância entre pontos idênticos deve ser 0.0 metros.")
    }

    @Test
    fun `deve validar limites de coordenadas corretamente`() {
        assertTrue(HaversineEngine.isValidLatitude(0.0))
        assertTrue(HaversineEngine.isValidLatitude(90.0))
        assertTrue(HaversineEngine.isValidLatitude(-90.0))
        assertFalse(HaversineEngine.isValidLatitude(90.1))
        assertFalse(HaversineEngine.isValidLatitude(-90.1))

        assertTrue(HaversineEngine.isValidLongitude(0.0))
        assertTrue(HaversineEngine.isValidLongitude(180.0))
        assertTrue(HaversineEngine.isValidLongitude(-180.0))
        assertFalse(HaversineEngine.isValidLongitude(180.1))
        assertFalse(HaversineEngine.isValidLongitude(-180.1))
    }

    @Test
    fun `deve lancar excecao ao instanciar coordenadas fora dos limites`() {
        assertThrows(InvalidCoordinatesException::class.java) {
            Coordinates(95.0, 0.0)
        }
        assertThrows(InvalidCoordinatesException::class.java) {
            Coordinates(-90.1, 0.0)
        }
        assertThrows(InvalidCoordinatesException::class.java) {
            Coordinates(0.0, 185.0)
        }
        assertThrows(InvalidCoordinatesException::class.java) {
            Coordinates(Double.NaN, 0.0)
        }
    }

    @Test
    fun `deve lancar excecao quando raio for menor ou igual a zero`() {
        assertThrows(InvalidGeofenceRadiusException::class.java) {
            LocationEvent(
                hotelId = "htl-01",
                hotelLocation = Coordinates(-8.052240, -34.885650),
                guestLocation = Coordinates(-8.053100, -34.886100),
                geofenceRadiusMeters = 0.0,
                previousState = GeofenceState.OUTSIDE
            )
        }
        assertThrows(InvalidGeofenceRadiusException::class.java) {
            LocationEvent(
                hotelId = "htl-01",
                hotelLocation = Coordinates(-8.052240, -34.885650),
                guestLocation = Coordinates(-8.053100, -34.886100),
                geofenceRadiusMeters = -50.0,
                previousState = GeofenceState.OUTSIDE
            )
        }
    }

    @Test
    fun `deve acionar alerta com transicao ENTERED quando entrar no raio`() {
        val event = LocationEvent(
            hotelId = "htl-01",
            hotelLocation = Coordinates(-8.052240, -34.885650),
            guestLocation = Coordinates(-8.053100, -34.886100),
            geofenceRadiusMeters = 200.0,
            previousState = GeofenceState.OUTSIDE
        )

        val result = HaversineEngine.evaluate(event, "test-corr-01")

        assertEquals(GeofenceState.INSIDE, result.currentState)
        assertEquals(GeofenceTransition.ENTERED, result.transition)
        assertTrue(result.alertTriggered)
    }

    @Test
    fun `deve retornar NO_CHANGE e sem alerta quando hospede estiver distante`() {
        val event = LocationEvent(
            hotelId = "htl-01",
            hotelLocation = Coordinates(-8.052240, -34.885650),
            guestLocation = Coordinates(-8.065000, -34.890000),
            geofenceRadiusMeters = 200.0,
            previousState = GeofenceState.OUTSIDE
        )

        val result = HaversineEngine.evaluate(event, "test-corr-02")

        assertEquals(GeofenceState.OUTSIDE, result.currentState)
        assertEquals(GeofenceTransition.NO_CHANGE, result.transition)
        assertFalse(result.alertTriggered)
    }

    @Test
    fun `deve registrar transicao EXITED quando sair do raio`() {
        val event = LocationEvent(
            hotelId = "htl-01",
            hotelLocation = Coordinates(-8.052240, -34.885650),
            guestLocation = Coordinates(-8.065000, -34.890000),
            geofenceRadiusMeters = 200.0,
            previousState = GeofenceState.INSIDE
        )

        val result = HaversineEngine.evaluate(event, "test-corr-03")

        assertEquals(GeofenceState.OUTSIDE, result.currentState)
        assertEquals(GeofenceTransition.EXITED, result.transition)
        assertFalse(result.alertTriggered)
    }
}
