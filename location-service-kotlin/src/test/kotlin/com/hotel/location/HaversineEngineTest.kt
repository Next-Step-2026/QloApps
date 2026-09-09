package com.hotel.location

import com.hotel.location.exception.InvalidCoordinatesException
import com.hotel.location.exception.InvalidGeofenceRadiusException
import com.hotel.location.exception.MissingFieldException
import com.hotel.location.model.Coordinates
import com.hotel.location.model.GeofenceState
import com.hotel.location.model.GeofenceTransition
import com.hotel.location.model.LocationEvent
import com.hotel.location.service.HaversineEngine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("Testes Unitários do Motor Geodésico de Haversine")
class HaversineEngineTest {

    @Nested
    @DisplayName("Validação de Invariantes e Limites de Coordenadas")
    inner class CoordinateValidationTests {

        @Test
        fun `deve aceitar coordenadas validas nos limites aceitaveis`() {
            assertDoesNotThrow {
                Coordinates(0.0, 0.0)
                Coordinates(90.0, 180.0)
                Coordinates(-90.0, -180.0)
            }
        }

        @Test
        fun `deve lancar InvalidCoordinatesException para latitude superior a 90`() {
            val ex = assertThrows<InvalidCoordinatesException> {
                Coordinates(90.1, 0.0)
            }
            assertTrue(ex.message.contains("Latitude deve ser finita e estar entre -90.0 e 90.0"))
            assertEquals("latitude", ex.field)
        }

        @Test
        fun `deve lancar InvalidCoordinatesException para latitude inferior a -90`() {
            val ex = assertThrows<InvalidCoordinatesException> {
                Coordinates(-90.1, 0.0)
            }
            assertTrue(ex.message.contains("Latitude deve ser finita e estar entre -90.0 e 90.0"))
            assertEquals("latitude", ex.field)
        }

        @Test
        fun `deve lancar InvalidCoordinatesException para longitude superior a 180`() {
            val ex = assertThrows<InvalidCoordinatesException> {
                Coordinates(0.0, 180.1)
            }
            assertTrue(ex.message.contains("Longitude deve ser finita e estar entre -180.0 e 180.0"))
            assertEquals("longitude", ex.field)
        }

        @Test
        fun `deve lancar InvalidCoordinatesException para longitude inferior a -180`() {
            val ex = assertThrows<InvalidCoordinatesException> {
                Coordinates(0.0, -180.1)
            }
            assertTrue(ex.message.contains("Longitude deve ser finita e estar entre -180.0 e 180.0"))
            assertEquals("longitude", ex.field)
        }

        @Test
        fun `deve lancar InvalidCoordinatesException para valores nao finitos`() {
            assertThrows<InvalidCoordinatesException> {
                Coordinates(Double.NaN, 0.0)
            }
            assertThrows<InvalidCoordinatesException> {
                Coordinates(Double.POSITIVE_INFINITY, 0.0)
            }
            assertThrows<InvalidCoordinatesException> {
                Coordinates(0.0, Double.NEGATIVE_INFINITY)
            }
        }

        @Test
        fun `deve lancar MissingFieldException quando hotel_id for em branco no LocationEvent`() {
            val coords = Coordinates(-8.052240, -34.885650)
            val ex = assertThrows<MissingFieldException> {
                LocationEvent(
                    hotelId = "",
                    hotelLocation = coords,
                    guestLocation = coords,
                    geofenceRadiusMeters = 200.0,
                    previousState = GeofenceState.OUTSIDE
                )
            }
            assertEquals("hotel_id", ex.field)
        }

        @Test
        fun `deve lancar InvalidGeofenceRadiusException quando raio for menor ou igual a zero`() {
            val coords = Coordinates(-8.052240, -34.885650)
            assertThrows<InvalidGeofenceRadiusException> {
                LocationEvent(
                    hotelId = "htl-recife-01",
                    hotelLocation = coords,
                    guestLocation = coords,
                    geofenceRadiusMeters = 0.0,
                    previousState = GeofenceState.OUTSIDE
                )
            }
            assertThrows<InvalidGeofenceRadiusException> {
                LocationEvent(
                    hotelId = "htl-recife-01",
                    hotelLocation = coords,
                    guestLocation = coords,
                    geofenceRadiusMeters = -50.0,
                    previousState = GeofenceState.OUTSIDE
                )
            }
        }
    }

    @Nested
    @DisplayName("Cálculos Geodésicos de Distância")
    inner class DistanceCalculationTests {

        @Test
        fun `deve calcular distancia aproximada de 108 metros para hospede proximo`() {
            val hotelCoords = Coordinates(-8.052240, -34.885650)
            val guestCoords = Coordinates(-8.053100, -34.886100)

            val distance = HaversineEngine.calculateDistanceMeters(hotelCoords, guestCoords)

            assertEquals(107.7, distance, 1.5, "A distância calculada deve ser aproximadamente 108 metros.")
        }

        @Test
        fun `deve calcular distancia aproximada de 1500 metros para hospede distante`() {
            val hotelCoords = Coordinates(-8.052240, -34.885650)
            val guestCoords = Coordinates(-8.065000, -34.890000)

            val distance = HaversineEngine.calculateDistanceMeters(hotelCoords, guestCoords)

            assertEquals(1497.5, distance, 5.0, "A distância calculada deve ser aproximadamente 1500 metros.")
        }

        @Test
        fun `deve retornar zero quando as coordenadas forem identicas`() {
            val coords = Coordinates(-8.052240, -34.885650)

            val distance = HaversineEngine.calculateDistanceMeters(coords, coords)

            assertEquals(0.0, distance, 0.001, "A distância entre pontos idênticos deve ser 0.0 metros.")
        }
    }

    @Nested
    @DisplayName("Avaliação de Geofence e Transições de Estado")
    inner class GeofenceEvaluationTests {

        @Test
        fun `deve acionar alerta com transicao ENTERED quando entrar no raio partindo de outside`() {
            val hotelCoords = Coordinates(-8.052240, -34.885650)
            val guestCoords = Coordinates(-8.053100, -34.886100)
            val event = LocationEvent(
                hotelId = "htl-recife-01",
                hotelLocation = hotelCoords,
                guestLocation = guestCoords,
                geofenceRadiusMeters = 200.0,
                previousState = GeofenceState.OUTSIDE
            )

            val result = HaversineEngine.evaluate(event, "corr-test-01")

            assertEquals("corr-test-01", result.correlationId)
            assertEquals("htl-recife-01", result.hotelId)
            assertEquals(107.7, result.distanceMeters, 1.5)
            assertEquals(GeofenceState.INSIDE, result.currentState)
            assertEquals(GeofenceTransition.ENTERED, result.transition)
            assertTrue(result.alertTriggered)
            assertEquals("Hóspede entrou no raio de 200m da propriedade.", result.message)
        }

        @Test
        fun `deve transicionar para EXITED sem alerta quando sair do raio partindo de inside`() {
            val hotelCoords = Coordinates(-8.052240, -34.885650)
            val guestCoords = Coordinates(-8.065000, -34.890000)
            val event = LocationEvent(
                hotelId = "htl-recife-01",
                hotelLocation = hotelCoords,
                guestLocation = guestCoords,
                geofenceRadiusMeters = 200.0,
                previousState = GeofenceState.INSIDE
            )

            val result = HaversineEngine.evaluate(event, "corr-test-02")

            assertEquals(GeofenceState.OUTSIDE, result.currentState)
            assertEquals(GeofenceTransition.EXITED, result.transition)
            assertFalse(result.alertTriggered)
            assertEquals("Posição atualizada sem alerta.", result.message)
        }

        @Test
        fun `deve retornar NO_CHANGE e sem alerta quando hospede estiver distante e previous_state for outside`() {
            val hotelCoords = Coordinates(-8.052240, -34.885650)
            val guestCoords = Coordinates(-8.065000, -34.890000)
            val event = LocationEvent(
                hotelId = "htl-recife-01",
                hotelLocation = hotelCoords,
                guestLocation = guestCoords,
                geofenceRadiusMeters = 200.0,
                previousState = GeofenceState.OUTSIDE
            )

            val result = HaversineEngine.evaluate(event, "corr-test-03")

            assertEquals(GeofenceState.OUTSIDE, result.currentState)
            assertEquals(GeofenceTransition.NO_CHANGE, result.transition)
            assertFalse(result.alertTriggered)
            assertEquals("Posição atualizada sem alerta.", result.message)
        }

        @Test
        fun `deve retornar NO_CHANGE e sem alerta quando hospede permanecer dentro do raio`() {
            val hotelCoords = Coordinates(-8.052240, -34.885650)
            val guestCoords = Coordinates(-8.053100, -34.886100)
            val event = LocationEvent(
                hotelId = "htl-recife-01",
                hotelLocation = hotelCoords,
                guestLocation = guestCoords,
                geofenceRadiusMeters = 200.0,
                previousState = GeofenceState.INSIDE
            )

            val result = HaversineEngine.evaluate(event, "corr-test-04")

            assertEquals(GeofenceState.INSIDE, result.currentState)
            assertEquals(GeofenceTransition.NO_CHANGE, result.transition)
            assertFalse(result.alertTriggered)
            assertEquals("Posição atualizada sem alerta.", result.message)
        }

        @Test
        fun `deve parametrizar dinamicamente o raio na mensagem quando acionar alerta ENTERED`() {
            val hotelCoords = Coordinates(-8.052240, -34.885650)
            val guestCoords = Coordinates(-8.053100, -34.886100)

            val event500 = LocationEvent(
                hotelId = "htl-01",
                hotelLocation = hotelCoords,
                guestLocation = guestCoords,
                geofenceRadiusMeters = 500.0,
                previousState = GeofenceState.OUTSIDE
            )
            val result500 = HaversineEngine.evaluate(event500, "test-corr-500m")
            assertTrue(result500.alertTriggered)
            assertEquals("Hóspede entrou no raio de 500m da propriedade.", result500.message)

            val eventDecimal = LocationEvent(
                hotelId = "htl-01",
                hotelLocation = hotelCoords,
                guestLocation = guestCoords,
                geofenceRadiusMeters = 150.5,
                previousState = GeofenceState.OUTSIDE
            )
            val resultDecimal = HaversineEngine.evaluate(eventDecimal, "test-corr-150-5m")
            assertTrue(resultDecimal.alertTriggered)
            assertEquals("Hóspede entrou no raio de 150.5m da propriedade.", resultDecimal.message)
        }

        @Test
        fun `deve considerar inside quando distancia for exatamente igual ao raio configurado`() {
            val hotelCoords = Coordinates(-8.052240, -34.885650)
            val guestCoords = Coordinates(-8.053100, -34.886100)
            val calculatedDistance = HaversineEngine.calculateDistanceMeters(hotelCoords, guestCoords)

            val eventInside = LocationEvent(
                hotelId = "htl-01",
                hotelLocation = hotelCoords,
                guestLocation = guestCoords,
                geofenceRadiusMeters = calculatedDistance,
                previousState = GeofenceState.INSIDE
            )
            val resultInside = HaversineEngine.evaluate(eventInside, "test-corr-exact-radius-inside")
            assertEquals(GeofenceState.INSIDE, resultInside.currentState)
            assertEquals(GeofenceTransition.NO_CHANGE, resultInside.transition)
            assertFalse(resultInside.alertTriggered)

            val eventOutside = LocationEvent(
                hotelId = "htl-01",
                hotelLocation = hotelCoords,
                guestLocation = guestCoords,
                geofenceRadiusMeters = calculatedDistance,
                previousState = GeofenceState.OUTSIDE
            )
            val resultOutside = HaversineEngine.evaluate(eventOutside, "test-corr-exact-radius-outside")
            assertEquals(GeofenceState.INSIDE, resultOutside.currentState)
            assertEquals(GeofenceTransition.ENTERED, resultOutside.transition)
            assertTrue(resultOutside.alertTriggered)
        }

        @Test
        fun `deve retornar zero quando as coordenadas forem identicas no evaluate`() {
            val coords = Coordinates(-8.052240, -34.885650)
            val event = LocationEvent(
                hotelId = "htl-01",
                hotelLocation = coords,
                guestLocation = coords,
                geofenceRadiusMeters = 200.0,
                previousState = GeofenceState.OUTSIDE
            )

            val result = HaversineEngine.evaluate(event, "test-corr-exact-outside")

            assertEquals(0.0, result.distanceMeters)
            assertEquals(GeofenceState.INSIDE, result.currentState)
            assertEquals(GeofenceTransition.ENTERED, result.transition)
            assertTrue(result.alertTriggered)
            assertEquals("Hóspede entrou no raio de 200m da propriedade.", result.message)
        }
    }
}
