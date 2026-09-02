package com.hotel.location.model

import com.hotel.location.exception.InvalidCoordinatesException
import com.hotel.location.exception.InvalidGeofenceRadiusException
import com.hotel.location.exception.InvalidGeofenceStateException
import com.hotel.location.exception.InvalidPayloadException

enum class GeofenceState {
    INSIDE,
    OUTSIDE;

    companion object {
        fun fromString(value: String): GeofenceState {
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
                ?: throw InvalidGeofenceStateException(value)
        }
    }
}

enum class GeofenceTransition {
    ENTERED,
    EXITED,
    NO_CHANGE
}

data class Coordinates(
    val latitude: Double,
    val longitude: Double
) {
    init {
        if (!latitude.isFinite() || latitude < -90.0 || latitude > 90.0) {
            throw InvalidCoordinatesException("Latitude deve ser finita e estar entre -90.0 e 90.0 (recebido: $latitude).")
        }
        if (!longitude.isFinite() || longitude < -180.0 || longitude > 180.0) {
            throw InvalidCoordinatesException("Longitude deve ser finita e estar entre -180.0 e 180.0 (recebido: $longitude).")
        }
    }
}

data class LocationEvent(
    val hotelId: String,
    val hotelLocation: Coordinates,
    val guestLocation: Coordinates,
    val geofenceRadiusMeters: Double,
    val previousState: GeofenceState
) {
    init {
        if (hotelId.isBlank()) {
            throw InvalidPayloadException("hotel_id não pode ser vazio ou conter apenas espaços.")
        }
        if (!geofenceRadiusMeters.isFinite() || geofenceRadiusMeters <= 0.0) {
            throw InvalidGeofenceRadiusException(geofenceRadiusMeters)
        }
    }
}

data class GeofenceEvaluationResult(
    val correlationId: String,
    val hotelId: String,
    val distanceMeters: Double,
    val currentState: GeofenceState,
    val transition: GeofenceTransition,
    val alertTriggered: Boolean,
    val message: String
)
