from enum import Enum
from typing import Dict, Any, Optional
from pydantic import BaseModel, Field


class PolicyType(str, Enum):
    MINIMUM_STAY = "MINIMUM_STAY"
    ADVANCE_BOOKING = "ADVANCE_BOOKING"
    OVERBOOKING_LIMIT = "OVERBOOKING_LIMIT"


class PolicyDecision(str, Enum):
    ALLOW = "ALLOW"
    DENY = "DENY"


class MinimumStayFacts(BaseModel):
    requested_nights: int = Field(..., ge=1, description="Noites solicitadas (mínimo 1)")
    required_minimum_nights: int = Field(..., ge=1, description="Mínimo exigido (mínimo 1)")
    room_type: str = Field(default="standard", description="Categoria do quarto")


class AdvanceBookingFacts(BaseModel):
    days_in_advance: int = Field(..., ge=0, description="Dias de antecedência (>= 0)")
    min_advance_days: int = Field(..., ge=0, description="Antecedência mínima exigida (>= 0)")


class OverbookingLimitFacts(BaseModel):
    total_capacity: int = Field(..., gt=0, description="Capacidade total (> 0)")
    current_occupied: int = Field(..., ge=0, description="Ocupação atual (>= 0)")
    requested_units: int = Field(default=1, ge=1, description="Unidades solicitadas (>= 1)")
    max_overbooking_rate: float = Field(default=0.05, ge=0.0, description="Taxa máxima de overbooking (>= 0)")


class PolicyEvaluationRequest(BaseModel):
    policy: PolicyType = Field(..., description="Nome da política a ser avaliada")
    facts: Dict[str, Any] = Field(..., description="Dicionário de fatos contextuais")


class PolicyEvaluationResponse(BaseModel):
    correlation_id: Optional[str] = Field(None, description="Identificador único da requisição")
    policy: str = Field(..., description="Nome da política avaliada")
    decision: PolicyDecision = Field(..., description="Decisão da política: ALLOW ou DENY")
    reason_code: str = Field(..., description="Código de motivo padronizado")
    explanation: str = Field(..., description="Justificativa em linguagem natural")
