package com.hotel.location.dto

import com.hotel.location.exception.InvalidPayloadException
import com.hotel.location.model.Coordinates
import com.hotel.location.model.GeofenceEvaluationResult
import com.hotel.location.model.GeofenceState
import com.hotel.location.model.LocationEvent
import kotlinx.serialization.Serializable

@Serializable
data class LocationEventRequestDto(
    val hotel_id: String? = null,
    val hotel_lat: Double? = null,
    val hotel_lng: Double? = null,
    val guest_lat: Double? = null,
    val guest_lng: Double? = null,
    val geofence_radius_m: Double? = 200.0,
    val previous_state: String? = "outside"
) {
    fun toDomain(): LocationEvent {
        if (hotel_id == null || hotel_id.isBlank()) {
            throw InvalidPayloadException("O campo 'hotel_id' é obrigatório e não pode ser vazio.")
        }
        val hotelLat = hotel_lat ?: throw InvalidPayloadException("O campo 'hotel_lat' é obrigatório.")
        val hotelLng = hotel_lng ?: throw InvalidPayloadException("O campo 'hotel_lng' é obrigatório.")
        val guestLat = guest_lat ?: throw InvalidPayloadException("O campo 'guest_lat' é obrigatório.")
        val guestLng = guest_lng ?: throw InvalidPayloadException("O campo 'guest_lng' é obrigatório.")

        val radius = geofence_radius_m ?: 200.0
        val rawState = previous_state ?: "outside"

        return LocationEvent(
            hotelId = hotel_id.trim(),
            hotelLocation = Coordinates(hotelLat, hotelLng),
            guestLocation = Coordinates(guestLat, guestLng),
            geofenceRadiusMeters = radius,
            previousState = GeofenceState.fromString(rawState)
        )
    }
}

@Serializable
data class LocationEventResponseDto(
    val correlation_id: String,
    val hotel_id: String,
    val distance_meters: Double,
    val current_state: String,
    val transition: String,
    val alert_triggered: Boolean,
    val message: String
)

@Serializable
data class ProblemDetailsResponse(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String
)

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val port: Int
)

fun GeofenceEvaluationResult.toDto(): LocationEventResponseDto {
    return LocationEventResponseDto(
        correlation_id = correlationId,
        hotel_id = hotelId,
        distance_meters = distanceMeters,
        current_state = currentState.name.lowercase(),
        transition = transition.name,
        alert_triggered = alertTriggered,
        message = message
    )
}
