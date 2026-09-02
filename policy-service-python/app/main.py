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
import json
from datetime import datetime, timezone


logging.basicConfig(level=logging.INFO, format="%(message)s")
logger = logging.getLogger("policy-engine")

app = FastAPI(
    title="Reservation Policy Engine",
    description="Motor determinístico de validação de políticas de estadia mínima, antecedência e overbooking",
    version="1.0.0"
)

class PolicyValidationException(Exception):                                                                                
    def __init__(self, detail: str):                                                                                       
        self.detail = detail

@app.exception_handler(PolicyValidationException)                                                                          
async def policy_validation_exception_handler(request: Request, exc: PolicyValidationException):                           
        return JSONResponse(                                                                                                   
            status_code=status.HTTP_400_BAD_REQUEST,                                                                           
            content={                                                                                                          
                "type": "https://hotel.local/errors/invalid-policy-facts",                                                     
                "title": "Fatos de Política Inválidos",                                                                        
                "status": 400,                                                                                                 
                "detail": exc.detail,                                                                                          
                "instance": request.url.path,                                                                                  
            }
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
        raise PolicyValidationException(detail=str(exc))

    duration_ms = (time.perf_counter() - start_time) * 1000
                                                        
    log_data = {                                                                                                           
        "timestamp": datetime.now(timezone.utc).isoformat(),                                                               
        "level": "INFO",                                                                                                   
        "correlation_id": correlation_id,                                                                                  
        "event": "POLICY_EVALUATED",                                                                                       
        "policy": req.policy.value,                                                                                        
        "decision": decision.value,                                                                                        
        "reason_code": reason_code,                                                                                        
        "duration_ms": round(duration_ms, 2)                                                                               
    }                   

    logger.info(json.dumps(log_data))                                                                                      
  
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