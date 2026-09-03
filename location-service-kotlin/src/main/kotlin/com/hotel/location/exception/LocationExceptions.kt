package com.hotel.location.exception

import io.ktor.http.HttpStatusCode

abstract class LocationValidationException(
    val typeUri: String,
    val title: String,
    override val message: String,
    val statusCode: HttpStatusCode = HttpStatusCode.BadRequest
) : RuntimeException(message)

class MissingHeaderException(headerName: String) : LocationValidationException(
    typeUri = "urn:problem-type:missing-header",
    title = "Missing Required Header",
    message = "O header obrigatório '$headerName' não foi fornecido."
)

class InvalidHeaderException(headerName: String, detail: String) : LocationValidationException(
    typeUri = "urn:problem-type:invalid-header",
    title = "Invalid Header",
    message = "O header '$headerName' é inválido: $detail"
)

class MissingContentTypeException : LocationValidationException(
    typeUri = "urn:problem-type:unsupported-media-type",
    title = "Unsupported Media Type",
    message = "O header 'Content-Type' é obrigatório e deve ser 'application/json'.",
    statusCode = HttpStatusCode.UnsupportedMediaType
)

class InvalidContentTypeException(contentType: String) : LocationValidationException(
    typeUri = "urn:problem-type:unsupported-media-type",
    title = "Unsupported Media Type",
    message = "O Content-Type deve ser 'application/json' (recebido: '$contentType').",
    statusCode = HttpStatusCode.UnsupportedMediaType
)

class InvalidCoordinatesException(detail: String) : LocationValidationException(
    typeUri = "urn:problem-type:invalid-coordinates",
    title = "Invalid Coordinates",
    message = detail
)

class InvalidGeofenceRadiusException(radius: Double) : LocationValidationException(
    typeUri = "urn:problem-type:invalid-radius",
    title = "Invalid Geofence Radius",
    message = "O raio da geocerca deve ser estritamente maior que zero (recebido: $radius)."
)

class InvalidGeofenceStateException(state: String) : LocationValidationException(
    typeUri = "urn:problem-type:invalid-state",
    title = "Invalid Geofence State",
    message = "O estado '$state' é inválido. Valores aceitos: 'inside' ou 'outside'."
)

class InvalidPayloadException(detail: String) : LocationValidationException(
    typeUri = "urn:problem-type:invalid-payload",
    title = "Invalid Payload",
    message = detail
)
