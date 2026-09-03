package com.hotel.location.exception

abstract class LocationValidationException(
    val typeUri: String,
    val title: String,
    override val message: String
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

class InvalidContentTypeException(contentType: String) : LocationValidationException(
    typeUri = "urn:problem-type:invalid-content-type",
    title = "Invalid Content-Type",
    message = "O Content-Type deve ser 'application/json' (recebido: '$contentType')."
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
