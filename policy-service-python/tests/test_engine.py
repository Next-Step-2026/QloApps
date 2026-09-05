"""
Unit and integration tests for the Reservation Policy Engine
"""
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.schemas import PolicyType, PolicyDecision
from app.engine import evaluate_policy

client = TestClient(app)


def test_healthz():
    """Valida o endpoint de healthcheck"""
    response = client.get("/healthz")
    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


# --- Testes de Unidade das Regras Determinísticas ---

def test_minimum_stay_allowed():
    """RN-001: Estadia dentro ou acima do mínimo exigido é autorizada"""
    decision, reason_code, explanation = evaluate_policy(
        PolicyType.MINIMUM_STAY,
        {"requested_nights": 3, "required_minimum_nights": 2, "room_type": "standard"}
    )
    assert decision == PolicyDecision.ALLOW
    assert reason_code == "MINIMUM_STAY_MET"
    assert "atende ao mínimo obrigatório" in explanation


def test_minimum_stay_denied():
    """RN-002: Estadia abaixo do mínimo exigido é negada"""
    decision, reason_code, explanation = evaluate_policy(
        PolicyType.MINIMUM_STAY,
        {"requested_nights": 1, "required_minimum_nights": 2, "room_type": "deluxe"}
    )
    assert decision == PolicyDecision.DENY
    assert reason_code == "NIGHTS_BELOW_MINIMUM"
    assert "é inferior ao mínimo obrigatório" in explanation


def test_advance_booking_allowed():
    """RN-003: Antecedência atendida é autorizada"""
    decision, reason_code, explanation = evaluate_policy(
        PolicyType.ADVANCE_BOOKING,
        {"days_in_advance": 5, "min_advance_days": 3}
    )
    assert decision == PolicyDecision.ALLOW
    assert reason_code == "ADVANCE_WINDOW_MET"
    assert "atende ao requisito mínimo" in explanation


def test_advance_booking_denied():
    """RN-004: Antecedência violada (ex: same-day) é negada"""
    decision, reason_code, explanation = evaluate_policy(
        PolicyType.ADVANCE_BOOKING,
        {"days_in_advance": 0, "min_advance_days": 3}
    )
    assert decision == PolicyDecision.DENY
    assert reason_code == "ADVANCE_WINDOW_VIOLATED"
    assert "é insuficiente frente" in explanation


def test_overbooking_allowed():
    """RN-005: Overbooking dentro do teto de 5% é autorizado"""
    # 50 * 1.05 = 52.5 -> floor = 52. 51 ocupados + 1 pedido = 52 <= 52 (ALLOW)
    decision, reason_code, explanation = evaluate_policy(
        PolicyType.OVERBOOKING_LIMIT,
        {"total_capacity": 50, "current_occupied": 51, "requested_units": 1, "max_overbooking_rate": 0.05}
    )
    assert decision == PolicyDecision.ALLOW
    assert reason_code == "WITHIN_OVERBOOKING_BUFFER"
    assert "dentro do limite máximo permitido" in explanation


def test_overbooking_denied():
    """RN-006: Overbooking acima do teto de 5% é negado"""
    # 50 * 1.05 = 52 vagas max. 52 ocupados + 1 pedido = 53 > 52 (DENY)
    decision, reason_code, explanation = evaluate_policy(
        PolicyType.OVERBOOKING_LIMIT,
        {"total_capacity": 50, "current_occupied": 52, "requested_units": 1, "max_overbooking_rate": 0.05}
    )
    assert decision == PolicyDecision.DENY
    assert reason_code == "OVERBOOKING_CAPACITY_EXCEEDED"
    assert "excede o teto máximo permitido" in explanation


# --- Testes de Integração via API FastAPI (POST /v1/policy-evaluations) ---

def test_api_minimum_stay():
    payload = {
        "policy": "MINIMUM_STAY",
        "facts": {
            "requested_nights": 1,
            "required_minimum_nights": 2,
            "room_type": "deluxe"
        }
    }
    headers = {"X-Correlation-ID": "test-corr-001"}
    response = client.post("/v1/policy-evaluations", json=payload, headers=headers)
    assert response.status_code == 200
    data = response.json()
    assert data["correlation_id"] == "test-corr-001"
    assert data["policy"] == "MINIMUM_STAY"
    assert data["decision"] == "DENY"
    assert data["reason_code"] == "NIGHTS_BELOW_MINIMUM"


def test_api_advance_booking():
    payload = {
        "policy": "ADVANCE_BOOKING",
        "facts": {
            "days_in_advance": 0,
            "min_advance_days": 3
        }
    }
    headers = {"X-Correlation-ID": "test-corr-002"}
    response = client.post("/v1/policy-evaluations", json=payload, headers=headers)
    assert response.status_code == 200
    data = response.json()
    assert data["correlation_id"] == "test-corr-002"
    assert data["policy"] == "ADVANCE_BOOKING"
    assert data["decision"] == "DENY"
    assert data["reason_code"] == "ADVANCE_WINDOW_VIOLATED"


def test_api_overbooking():
    payload = {
        "policy": "OVERBOOKING_LIMIT",
        "facts": {
            "total_capacity": 50,
            "current_occupied": 51,
            "requested_units": 1,
            "max_overbooking_rate": 0.05
        }
    }
    headers = {"X-Correlation-ID": "test-corr-003"}
    response = client.post("/v1/policy-evaluations", json=payload, headers=headers)
    assert response.status_code == 200
    data = response.json()
    assert data["correlation_id"] == "test-corr-003"
    assert data["policy"] == "OVERBOOKING_LIMIT"
    assert data["decision"] == "ALLOW"
    assert data["reason_code"] == "WITHIN_OVERBOOKING_BUFFER"


def test_api_invalid_policy():
    payload = {
        "policy": "NON_EXISTING_POLICY",
        "facts": {}
    }
    response = client.post("/v1/policy-evaluations", json=payload)
    assert response.status_code == 422


def test_api_invalid_facts_negative_value():
    """Valida rejeição de valores negativos e formato de erro RFC 7807"""
    payload = {
        "policy": "MINIMUM_STAY",
        "facts": {
            "requested_nights": -1,
            "required_minimum_nights": 2
        }
    }
    response = client.post("/v1/policy-evaluations", json=payload)
    assert response.status_code == 400
    data = response.json()
    assert data["status"] == 400
    assert data["type"] == "https://hotel.local/errors/invalid-policy-facts"
    assert data["title"] == "Fatos de Política Inválidos"
    assert data["instance"] == "/v1/policy-evaluations"
    assert "greater than or equal to 1" in data["detail"] or "Input should be greater than or equal to 1" in data["detail"]


def test_api_missing_required_facts():
    """Valida rejeição de campos obrigatórios ausentes e formato RFC 7807"""
    payload = {
        "policy": "ADVANCE_BOOKING",
        "facts": {}
    }
    response = client.post("/v1/policy-evaluations", json=payload)
    assert response.status_code == 400
    data = response.json()
    assert data["status"] == 400
    assert data["title"] == "Fatos de Política Inválidos"
    assert "Field required" in data["detail"]

