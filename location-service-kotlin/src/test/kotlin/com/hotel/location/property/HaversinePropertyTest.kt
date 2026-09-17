package com.hotel.location.property

import com.hotel.location.model.Coordinates
import com.hotel.location.model.GeofenceState
import com.hotel.location.model.GeofenceTransition
import com.hotel.location.model.LocationEvent
import com.hotel.location.service.HaversineEngine
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.abs

@DisplayName("Testes Baseados em Propriedades para HaversineEngine e Geofencing (RFC-004)")
class HaversinePropertyTest {

    private val arbCoordinates = Arb.bind(
        Arb.double(-90.0, 90.0),
        Arb.double(-180.0, 180.0)
    ) { lat, lon -> Coordinates(lat, lon) }

    private val arbRadius = Arb.double(1.0, 50000.0)

    @Test
    fun `Propriedade de Identidade - distancia de um ponto para si mesmo deve ser identicamente zero`() {
        runBlocking {
            forAll(arbCoordinates) { coord ->
                val distance = HaversineEngine.calculateDistanceMeters(coord, coord)
                abs(distance) < 0.001
            }
        }
    }

    @Test
    fun `Propriedade de Simetria - distancia de A para B deve ser igual a distancia de B para A`() {
        runBlocking {
            forAll(arbCoordinates, arbCoordinates) { a, b ->
                val distAB = HaversineEngine.calculateDistanceMeters(a, b)
                val distBA = HaversineEngine.calculateDistanceMeters(b, a)
                abs(distAB - distBA) < 0.01
            }
        }
    }

    @Test
    fun `Propriedade de Nao-negatividade e Limite Terrestre - distancia finita entre zero e metade da circunferencia`() {
        runBlocking {
            forAll(arbCoordinates, arbCoordinates) { a, b ->
                val dist = HaversineEngine.calculateDistanceMeters(a, b)
                dist.isFinite() && !dist.isNaN() && dist >= 0.0 && dist <= 20037500.0
            }
        }
    }

    @Test
    fun `Propriedade da Desigualdade Triangular - distancia de A ate C nunca excede A ate B mais B ate C`() {
        runBlocking {
            forAll(arbCoordinates, arbCoordinates, arbCoordinates) { a, b, c ->
                val distAC = HaversineEngine.calculateDistanceMeters(a, c)
                val distAB = HaversineEngine.calculateDistanceMeters(a, b)
                val distBC = HaversineEngine.calculateDistanceMeters(b, c)
                distAC <= (distAB + distBC + 0.5)
            }
        }
    }

    @Test
    fun `Propriedade de Consistencia de Estado de Geofence - estado deve ser INSIDE se e somente se distancia arredondada for menor ou igual ao raio`() {
        runBlocking {
            forAll(arbCoordinates, arbCoordinates, arbRadius, Arb.enum<GeofenceState>()) { hotel, guest, radius, prevState ->
                val event = LocationEvent(
                    hotelId = "htl-test",
                    hotelLocation = hotel,
                    guestLocation = guest,
                    geofenceRadiusMeters = radius,
                    previousState = prevState
                )
                val result = HaversineEngine.evaluate(event, "test-correlation-id")
                val isInside = result.distanceMeters <= radius

                if (isInside) {
                    result.currentState == GeofenceState.INSIDE
                } else {
                    result.currentState == GeofenceState.OUTSIDE
                }
            }
        }
    }

    @Test
    fun `Propriedade da Regra de Ouro do Alerta RFC-004 - alerta e acionado unica e exclusivamente na transicao ENTERED`() {
        runBlocking {
            forAll(arbCoordinates, arbCoordinates, arbRadius, Arb.enum<GeofenceState>()) { hotel, guest, radius, prevState ->
                val event = LocationEvent(
                    hotelId = "htl-test",
                    hotelLocation = hotel,
                    guestLocation = guest,
                    geofenceRadiusMeters = radius,
                    previousState = prevState
                )
                val result = HaversineEngine.evaluate(event, "test-correlation-id")

                // Regra 1: alertTriggered só pode ser true se a transição for ENTERED
                val alertConsistentWithTransition = (result.alertTriggered == (result.transition == GeofenceTransition.ENTERED))

                // Regra 2: ENTERED só ocorre quando previousState era OUTSIDE e currentState é INSIDE
                val enteredConsistentWithStates = if (result.transition == GeofenceTransition.ENTERED) {
                    prevState == GeofenceState.OUTSIDE && result.currentState == GeofenceState.INSIDE
                } else true

                // Regra 3: EXITED só ocorre quando previousState era INSIDE e currentState é OUTSIDE
                val exitedConsistentWithStates = if (result.transition == GeofenceTransition.EXITED) {
                    prevState == GeofenceState.INSIDE && result.currentState == GeofenceState.OUTSIDE
                } else true

                alertConsistentWithTransition && enteredConsistentWithStates && exitedConsistentWithStates
            }
        }
    }
}
