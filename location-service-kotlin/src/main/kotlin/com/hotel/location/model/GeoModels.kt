package com.hotel.location.model

import kotlinx.serialization.Serializable

@Serializable
data class LocationEventRequest(
    val hotel_id: String,
    val hotel_lat: Double,
    val hotel_lng: Double,
    val guest_lat: Double,
    val guest_lng: Double,
    val geofence_radius_m: Double = 200.0,
    val previous_state: String = "outside"
)

@Serializable
data class LocationEventResponse(
    val correlation_id: String,
    val hotel_id: String,
    val distance_meters: Double,
    val current_state: String,
    val transition: String,
    val alert_triggered: Boolean,
    val message: String
)

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val port: Int
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String
)
