"""
@file main.py
@brief Servidor HTTP FastAPI para o Motor de Políticas de Reserva.
@details Expõe endpoints RESTful para consulta de saúde e avaliação determinística
         de regras de negócio, com suporte a correlação e tratamento de erros RFC 7807.
"""

import json
import logging
import time
from datetime import UTC, datetime

from fastapi import FastAPI, Header, Request, status
from fastapi.responses import JSONResponse

from app.engine import evaluate_policy
from app.schemas import PolicyEvaluationRequest, PolicyEvaluationResponse, ProblemDetails

logging.basicConfig(level=logging.INFO, format="%(message)s")
logger = logging.getLogger("policy-engine")

app = FastAPI(
    title="Reservation Policy Engine",
    description="Motor determinístico de validação de políticas de estadia mínima, antecedência e overbooking",
    version="1.0.0",
)


class PolicyValidationException(Exception):
    """
    @brief Exceção customizada para erros de validação semântica de regras.
    """
    def __init__(self, detail: str):
        self.detail = detail


@app.exception_handler(PolicyValidationException)
async def policy_validation_exception_handler(request: Request, exc: PolicyValidationException):
    """
    @brief Manipulador global que formata erros conforme a RFC 7807.
    @param request Objeto da requisição HTTP recebida.
    @param exc Instância da PolicyValidationException capturada.
    @return JSONResponse com status HTTP 400 e schema ProblemDetails.
    """
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content={
            "type": "https://hotel.local/errors/invalid-policy-facts",
            "title": "Fatos de Política Inválidos",
            "status": 400,
            "detail": exc.detail,
            "instance": request.url.path,
        },
    )


@app.get("/healthz")
def health_check():
    """
    @brief Endpoint de verificação de integridade.
    @return dict Dicionário indicando status UP.
    """
    return {"status": "UP"}


@app.post(
    "/v1/policy-evaluations",
    response_model=PolicyEvaluationResponse,
    status_code=status.HTTP_200_OK,
    responses={
        400: {
            "model": ProblemDetails,
            "description": "Fatos de política inválidos ou ausentes (RFC 7807)",
        }
    },
)
def evaluate(req: PolicyEvaluationRequest, x_correlation_id: str | None = Header(default=None)):
    """
    @brief Avalia a conformidade de uma reserva contra a política selecionada.
    @param req Requisição contendo a política e o dicionário de fatos contextuais.
    @return PolicyEvaluationResponse Decisão (ALLOW/DENY), reason_code e justificativa.
    @throws PolicyValidationException Se os fatos forem inválidos para a política.
    @note O tempo de resposta é logado estruturado em JSON para observabilidade.
    """
    start_time = time.perf_counter()
    correlation_id = x_correlation_id or "corr-generated"

    try:
        decision, reason_code, explanation = evaluate_policy(req.policy, req.facts)
    except Exception as exc:
        raise PolicyValidationException(detail=str(exc)) from exc

    duration_ms = (time.perf_counter() - start_time) * 1000

    log_data = {
        "timestamp": datetime.now(UTC).isoformat(),
        "level": "INFO",
        "correlation_id": correlation_id,
        "event": "POLICY_EVALUATED",
        "policy": req.policy.value,
        "decision": decision.value,
        "reason_code": reason_code,
        "duration_ms": round(duration_ms, 2),
    }

    logger.info(json.dumps(log_data))

    return PolicyEvaluationResponse(
        correlation_id=correlation_id,
        policy=req.policy.value,
        decision=decision,
        reason_code=reason_code,
        explanation=explanation,
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=8105)
