package com.hotel.location.property

import com.hotel.location.model.Coordinates
import com.hotel.location.model.GeofenceState
import com.hotel.location.model.GeofenceTransition
import com.hotel.location.model.LocationEvent
import com.hotel.location.service.HaversineEngine
import com.hotel.location.dto.LocationEventRequestDto
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.abs

@DisplayName("Testes Baseados em Propriedades para HaversineEngine e Geofencing (RFC-004)")
class HaversinePropertyTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val arbCoordinates = Arb.bind(
        Arb.double(-90.0, 90.0),
        Arb.double(-180.0, 180.0)
    ) { lat, lon -> Coordinates(lat, lon) }

    private val arbRadius = Arb.double(0.01, 50000.0)

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
    fun `Propriedade de Monotonicidade e Fronteira Estrita de Geofence - estado deterministico garantido fora da margem de arredondamento`() {
        runBlocking {
            forAll(arbCoordinates, arbCoordinates, arbRadius, Arb.enum<GeofenceState>()) { hotel, guest, radius, prevState ->
                val rawDistance = HaversineEngine.calculateDistanceMeters(hotel, guest)
                val event = LocationEvent(
                    hotelId = "htl-test",
                    hotelLocation = hotel,
                    guestLocation = guest,
                    geofenceRadiusMeters = radius,
                    previousState = prevState
                )
                val result = HaversineEngine.evaluate(event, "test-correlation-id")

                // Limites inequívocos além da margem de arredondamento de 0.05m (1 casa decimal)
                val insideStrict = (rawDistance <= radius - 0.06)
                val outsideStrict = (rawDistance >= radius + 0.06)

                when {
                    insideStrict -> result.currentState == GeofenceState.INSIDE
                    outsideStrict -> result.currentState == GeofenceState.OUTSIDE
                    else -> true // Zona de fronteira exata testada nos testes de unidade específicos
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

                // Resolução determinística e bidirecional esperada da máquina de estados
                val expectedTransition = when {
                    prevState == GeofenceState.OUTSIDE && result.currentState == GeofenceState.INSIDE -> GeofenceTransition.ENTERED
                    prevState == GeofenceState.INSIDE && result.currentState == GeofenceState.OUTSIDE -> GeofenceTransition.EXITED
                    else -> GeofenceTransition.NO_CHANGE
                }

                val transitionExact = (result.transition == expectedTransition)
                val alertExact = (result.alertTriggered == (expectedTransition == GeofenceTransition.ENTERED))

                transitionExact && alertExact
            }
        }
    }

    @Test
    fun `Propriedade de Pontos Antipodais e Extremos - calculo geodesico nao gera NaN e respeita simetria e limite terrestre`() {
        runBlocking {
            forAll(arbCoordinates) { coord ->
                val antipodalLng = if (coord.longitude <= 0.0) coord.longitude + 180.0 else coord.longitude - 180.0
                val antipode = Coordinates(-coord.latitude, antipodalLng)

                val distForward = HaversineEngine.calculateDistanceMeters(coord, antipode)
                val distBackward = HaversineEngine.calculateDistanceMeters(antipode, coord)

                distForward.isFinite() && !distForward.isNaN() &&
                    distBackward.isFinite() && !distBackward.isNaN() &&
                    abs(distForward - distBackward) < 0.01 &&
                    distForward >= 20000000.0 && distForward <= 20037500.0
            }
        }
    }

    @Test
    fun `Propriedade de Invariancia por Rotacao Longitudinal - deslocar a longitude de ambos os pontos preserva a distancia`() {
        runBlocking {
            forAll(arbCoordinates, arbCoordinates, Arb.double(-45.0, 45.0)) { a, b, deltaLon ->
                val newLonA = a.longitude + deltaLon
                val newLonB = b.longitude + deltaLon

                if (newLonA in -180.0..180.0 && newLonB in -180.0..180.0) {
                    val rotatedA = Coordinates(a.latitude, newLonA)
                    val rotatedB = Coordinates(b.latitude, newLonB)

                    val origDist = HaversineEngine.calculateDistanceMeters(a, b)
                    val rotDist = HaversineEngine.calculateDistanceMeters(rotatedA, rotatedB)

                    abs(origDist - rotDist) < 0.05
                } else {
                    true
                }
            }
        }
    }

    @Test
    fun `Propriedade de Round-Trip Serde - DTO de requisicao serializado e desserializado preserva integridade exata`() {
        runBlocking {
            forAll(arbCoordinates, arbCoordinates, arbRadius, Arb.enum<GeofenceState>()) { hotel, guest, radius, prevState ->
                val dto = LocationEventRequestDto(
                    hotel_id = "htl-prop-test",
                    hotel_lat = hotel.latitude,
                    hotel_lng = hotel.longitude,
                    guest_lat = guest.latitude,
                    guest_lng = guest.longitude,
                    geofence_radius_m = radius,
                    previous_state = prevState.name.lowercase()
                )

                val serialized = json.encodeToString(dto)
                val deserialized = json.decodeFromString<LocationEventRequestDto>(serialized)

                dto == deserialized
            }
        }
    }
}
