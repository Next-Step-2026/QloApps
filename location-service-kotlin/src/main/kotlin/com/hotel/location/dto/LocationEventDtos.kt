package com.hotel.location.dto

import com.hotel.location.exception.MissingFieldException
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
    val geofence_radius_m: Double? = null, 
    val previous_state: String? = null 
) {
    fun toDomain(): LocationEvent {
        if (hotel_id.isNullOrBlank()) {
            throw MissingFieldException("hotel_id")
        }
        val hotelLat = hotel_lat ?: throw MissingFieldException("hotel_lat")
        val hotelLng = hotel_lng ?: throw MissingFieldException("hotel_lng")
        val guestLat = guest_lat ?: throw MissingFieldException("guest_lat")
        val guestLng = guest_lng ?: throw MissingFieldException("guest_lng")

        val radius = geofence_radius_m ?: throw MissingFieldException("geofence_radius_m")
            
        if (previous_state.isNullOrBlank()) {
            throw MissingFieldException("previous_state")
        }

        return LocationEvent(
            hotelId = hotel_id.trim(),
            hotelLocation = Coordinates(hotelLat, hotelLng),
            guestLocation = Coordinates(guestLat, guestLng),
            geofenceRadiusMeters = radius,
            previousState = GeofenceState.fromString(previous_state)
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
