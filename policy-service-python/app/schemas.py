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


class PolicyEvaluationRequest(BaseModel):
    policy: PolicyType = Field(..., description="Nome da política a ser avaliada")
    facts: Dict[str, Any] = Field(..., description="Dicionário de fatos contextuais")


class PolicyEvaluationResponse(BaseModel):
    correlation_id: Optional[str] = Field(None, description="Identificador único da requisição")
    policy: str = Field(..., description="Nome da política avaliada")
    decision: PolicyDecision = Field(..., description="Decisão da política: ALLOW ou DENY")
    reason_code: str = Field(..., description="Código de motivo padronizado")
    explanation: str = Field(..., description="Justificativa em linguagem natural")
