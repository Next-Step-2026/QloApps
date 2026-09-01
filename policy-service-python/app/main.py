"""
FastAPI Server for Reservation Policy Engine
"""
import time
import logging
from typing import Optional
from fastapi import FastAPI, HTTPException, Header, status
from fastapi.responses import JSONResponse
from app.schemas import PolicyEvaluationRequest, PolicyEvaluationResponse
from app.engine import evaluate_policy

logging.basicConfig(level=logging.INFO, format="%(message)s")
logger = logging.getLogger("policy-engine")

app = FastAPI(
    title="Reservation Policy Engine",
    description="Motor determinístico de validação de políticas de estadia mínima, antecedência e overbooking",
    version="1.0.0"
)


@app.get("/healthz")
def health_check():
    """Healthcheck endpoint"""
    return {"status": "UP"}


@app.post(
    "/v1/policy-evaluations",
    response_model=PolicyEvaluationResponse,
    status_code=status.HTTP_200_OK
)
def evaluate(
    req: PolicyEvaluationRequest,
    x_correlation_id: Optional[str] = Header(default=None)
):
    """
    Avalia a conformidade de uma reserva com base na política e nos fatos informados.
    """
    start_time = time.perf_counter()
    correlation_id = x_correlation_id or "corr-generated"

    try:
        decision, reason_code, explanation = evaluate_policy(req.policy, req.facts)
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Fatos de Política Inválidos: {str(exc)}"
        )

    duration_ms = (time.perf_counter() - start_time) * 1000

    # Log estruturado
    logger.info(
        '{"event": "POLICY_EVALUATED", "policy": "%s", "decision": "%s", "reason_code": "%s", "duration_ms": %.2f, "correlation_id": "%s"}',
        req.policy.value,
        decision.value,
        reason_code,
        duration_ms,
        correlation_id
    )

    return PolicyEvaluationResponse(
        correlation_id=correlation_id,
        policy=req.policy.value,
        decision=decision,
        reason_code=reason_code,
        explanation=explanation
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8105)
