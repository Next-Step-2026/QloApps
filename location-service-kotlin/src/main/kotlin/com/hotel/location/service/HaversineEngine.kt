package com.hotel.location.service

import com.hotel.location.model.LocationEventRequest
import com.hotel.location.model.LocationEventResponse
import kotlin.math.*


object HaversineEngine {
    private const val EARTH_RADIUS_METERS = 6371000.0

    fun isValidLatitude(lat: Double): Boolean = lat in -90.0..90.0

    fun isValidLongitude(lng: Double): Boolean = lng in -180.0..180.0

    fun areCoordinatesValid(req: LocationEventRequest): Boolean {
        return isValidLatitude(req.hotel_lat) &&
               isValidLatitude(req.guest_lat) &&
               isValidLongitude(req.hotel_lng) &&
               isValidLongitude(req.guest_lng)
    }

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun processLocationEvent(req: LocationEventRequest, correlationId: String): LocationEventResponse {
        val radius = if (req.geofence_radius_m <= 0.0) 200.0 else req.geofence_radius_m
        val distance = calculateDistanceMeters(req.hotel_lat, req.hotel_lng, req.guest_lat, req.guest_lng)
        val roundedDistance = (distance * 10.0).roundToInt() / 10.0

        val currentState = if (distance <= radius) "inside" else "outside"

        val transition = when {
            req.previous_state == "outside" && currentState == "inside" -> "ENTERED"
            req.previous_state == "inside" && currentState == "outside" -> "EXITED"
            else -> "NO_CHANGE"
        }
        val alertTriggered = (transition == "ENTERED")

        val message = if (alertTriggered) {
            "Hóspede entrou no raio de proximidade do hotel."
        } else {
            "Posição atualizada sem alerta."
        }

        return LocationEventResponse(
            correlation_id = correlationId,
            hotel_id = req.hotel_id,
            distance_meters = roundedDistance,
            current_state = currentState,
            transition = transition,
            alert_triggered = alertTriggered,
            message = message
        )
    }
}
