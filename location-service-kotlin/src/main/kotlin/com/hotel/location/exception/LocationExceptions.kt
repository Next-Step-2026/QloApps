package com.hotel.location.exception

import io.ktor.http.HttpStatusCode

abstract class LocationValidationException(
    val typeUri: String,
    val title: String,
    override val message: String,
    val statusCode: HttpStatusCode = HttpStatusCode.BadRequest,
    val field: String? = null
) : RuntimeException(message)

class MissingHeaderException(headerName: String) : LocationValidationException(
    typeUri = "urn:problem-type:missing-header",
    title = "Missing Required Header",
    message = "O header obrigatório '$headerName' não foi fornecido.",
    field = headerName
)

class InvalidHeaderException(headerName: String, detail: String) : LocationValidationException(
    typeUri = "urn:problem-type:invalid-header",
    title = "Invalid Header",
    message = "O header '$headerName' é inválido: $detail",
    field = headerName
)

class MissingContentTypeException : LocationValidationException(
    typeUri = "urn:problem-type:unsupported-media-type",
    title = "Unsupported Media Type",
    message = "O header 'Content-Type' é obrigatório e deve ser 'application/json'.",
    statusCode = HttpStatusCode.UnsupportedMediaType,
    field = "Content-Type"
)

class InvalidContentTypeException(contentType: String) : LocationValidationException(
    typeUri = "urn:problem-type:unsupported-media-type",
    title = "Unsupported Media Type",
    message = "O Content-Type deve ser 'application/json' (recebido: '$contentType').",
    statusCode = HttpStatusCode.UnsupportedMediaType,
    field = "Content-Type"
)

class InvalidCoordinatesException(detail: String, field: String = "coordinates") : LocationValidationException(
    typeUri = "urn:problem-type:invalid-coordinates",
    title = "Invalid Coordinates",
    message = detail,
    field = field
)

class InvalidGeofenceRadiusException(radius: Double) : LocationValidationException(
    typeUri = "urn:problem-type:invalid-radius",
    title = "Invalid Geofence Radius",
    message = "O raio da geocerca deve ser estritamente maior que zero (recebido: $radius).",
    field = "geofence_radius_m"
)

class InvalidGeofenceStateException(state: String) : LocationValidationException(
    typeUri = "urn:problem-type:invalid-state",
    title = "Invalid Geofence State",
    message = "O estado '$state' é inválido. Valores aceitos: 'inside' ou 'outside'.",
    field = "previous_state"
)

class MissingFieldException(fieldName: String) : LocationValidationException(
    typeUri = "urn:problem-type:invalid-payload",
    title = "Invalid Payload",
    message = "O campo '$fieldName' é obrigatório.",
    field = fieldName
)

class ServiceUnavailableException(
    detail: String = "O serviço de cálculo de geofencing está temporariamente indisponível."
) : LocationValidationException(
    typeUri = "urn:problem-type:service-unavailable",
    title = "Service Unavailable",
    message = detail,
    statusCode = HttpStatusCode.ServiceUnavailable
)
