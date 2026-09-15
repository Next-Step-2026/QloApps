"""
@file schemas.py
@brief Modelos Pydantic, DTOs e tipos enumerados do Motor de Políticas de Reserva.
@details Define as estruturas de dados de entrada, resposta e especificação de erros RFC 7807.
"""

from enum import Enum
from typing import Any

from pydantic import BaseModel, Field


class PolicyType(str, Enum):
    """
    @brief Enumeração dos identificadores de políticas suportadas.
    """

    MINIMUM_STAY = "MINIMUM_STAY"  # Política de estadia mínima em noites
    ADVANCE_BOOKING = "ADVANCE_BOOKING"  # Política de antecedência mínima de compra
    OVERBOOKING_LIMIT = "OVERBOOKING_LIMIT"  # Política de margem controlada de overbooking


class PolicyDecision(str, Enum):
    """
    @brief Decisao da avaliação da política.
    """

    ALLOW = "ALLOW"  # Reserva permitida sob a política avaliada
    DENY = "DENY"  # Reserva rejeitada pela política avaliada


class MinimumStayFacts(BaseModel):
    """
    @brief Fatos requeridos para avaliar a política MINIMUM_STAY.
    @details Exige que a quantidade de noites solicitadas satisfaça o mínimo configurado.
    """

    requested_nights: int = Field(..., ge=1, description="Noites solicitadas (mínimo 1)")
    required_minimum_nights: int = Field(..., ge=1, description="Mínimo exigido (mínimo 1)")
    room_type: str = Field(default="standard", description="Categoria do quarto")


class AdvanceBookingFacts(BaseModel):
    """
    @brief Fatos requeridos para avaliar a política ADVANCE_BOOKING.
    @details Exige um intervalo mínimo em dias entre a data de solicitação e a data do check-in.
    """

    days_in_advance: int = Field(..., ge=0, description="Dias de antecedência (>= 0)")
    min_advance_days: int = Field(..., ge=0, description="Antecedência mínima exigida (>= 0)")


class OverbookingLimitFacts(BaseModel):
    """
    @brief Fatos requeridos para avaliar a política OVERBOOKING_LIMIT.
    @details Garante que a ocupação projetada não ultrapasse o teto de tolerância operacional.
    """

    total_capacity: int = Field(..., gt=0, description="Capacidade total (> 0)")
    current_occupied: int = Field(..., ge=0, description="Ocupação atual (>= 0)")
    requested_units: int = Field(default=1, ge=1, description="Unidades solicitadas (>= 1)")
    max_overbooking_rate: float = Field(default=0.05, ge=0.0, description="Taxa máxima de overbooking (>= 0)")


class PolicyEvaluationRequest(BaseModel):
    """
    @brief DTO de entrada para requisições do endpoint /v1/policy-evaluations.
    """

    policy: PolicyType = Field(..., description="Nome da política a ser avaliada")
    facts: dict[str, Any] = Field(..., description="Dicionário de fatos contextuais")


class PolicyEvaluationResponse(BaseModel):
    """
    @brief DTO de saída contendo a decisão e a justificativa auditável.
    """

    correlation_id: str | None = Field(None, description="Identificador único da requisição")
    policy: str = Field(..., description="Nome da política avaliada")
    decision: PolicyDecision = Field(..., description="Decisão da política: ALLOW ou DENY")
    reason_code: str = Field(..., description="Código de motivo padronizado")
    explanation: str = Field(..., description="Justificativa legível da decisão")


class ProblemDetails(BaseModel):
    """
    @brief Modelo de erro em conformidade com o padrão RFC 7807.
    """

    type: str | None = Field(
        default="https://hotel.local/errors/invalid-policy-facts", description="URI de referência do tipo de erro"
    )
    title: str | None = Field(default="Fatos de Política Inválidos", description="Resumo legível do erro")
    status: int = Field(default=400, description="Código de status HTTP")
    detail: str = Field(..., description="Explicação detalhada do erro")
    instance: str | None = Field(default=None, description="URI da requisição que originou o erro")
