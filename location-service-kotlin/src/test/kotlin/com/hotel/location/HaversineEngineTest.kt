package com.hotel.location

import com.hotel.location.model.LocationEventRequest
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
    fun `deve acionar alerta com transicao ENTERED quando entrar no raio`() {
        val req = LocationEventRequest(
            hotel_id = "htl-01",
            hotel_lat = -8.052240,
            hotel_lng = -34.885650,
            guest_lat = -8.053100,
            guest_lng = -34.886100,
            geofence_radius_m = 200.0,
            previous_state = "outside"
        )

        val result = HaversineEngine.processLocationEvent(req, "test-corr-01")

        assertEquals("inside", result.current_state)
        assertEquals("ENTERED", result.transition)
        assertTrue(result.alert_triggered)
    }

    @Test
    fun `deve retornar NO_CHANGE e sem alerta quando hospede estiver distante`() {
        val req = LocationEventRequest(
            hotel_id = "htl-01",
            hotel_lat = -8.052240,
            hotel_lng = -34.885650,
            guest_lat = -8.065000,
            guest_lng = -34.890000,
            geofence_radius_m = 200.0,
            previous_state = "outside"
        )

        val result = HaversineEngine.processLocationEvent(req, "test-corr-02")

        assertEquals("outside", result.current_state)
        assertEquals("NO_CHANGE", result.transition)
        assertFalse(result.alert_triggered)
    }
}
