"""
Testes de Integração com Pytest Fixtures.
Valida o ciclo de vida completo de requisições HTTP e serialização via FastAPI.
"""

from typing import Any

import pytest
from fastapi.testclient import TestClient

from app.schemas import PolicyDecision


@pytest.mark.integration
def test_integration_minimum_stay_with_fixtures(
    client: TestClient,
    valid_minimum_stay_payload: dict[str, Any],
    correlation_headers: dict[str, str],
):
    """Integração: Avalia estadia mínima usando fixtures compartilhadas"""
    response = client.post(
        "/v1/policy-evaluations",
        json=valid_minimum_stay_payload,
        headers=correlation_headers,
    )
    assert response.status_code == 200
    data = response.json()
    assert data["policy"] == "MINIMUM_STAY"
    assert data["decision"] == PolicyDecision.ALLOW.value
    assert data["reason_code"] == "MINIMUM_STAY_MET"
    assert data["correlation_id"] == correlation_headers["X-Correlation-ID"]


@pytest.mark.integration
def test_integration_overbooking_with_fixtures(
    client: TestClient,
    valid_overbooking_payload: dict[str, Any],
    correlation_headers: dict[str, str],
):
    """Integração: Avalia limite de overbooking usando fixtures compartilhadas"""
    response = client.post(
        "/v1/policy-evaluations",
        json=valid_overbooking_payload,
        headers=correlation_headers,
    )
    assert response.status_code == 200
    data = response.json()
    assert data["policy"] == "OVERBOOKING_LIMIT"
    assert data["decision"] == PolicyDecision.ALLOW.value
    assert data["reason_code"] == "WITHIN_OVERBOOKING_BUFFER"
