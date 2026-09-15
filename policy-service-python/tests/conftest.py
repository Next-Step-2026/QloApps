"""
Pytest Fixtures Globais e Configurações de Teste.
Compartilha contexto, clientes de teste e geradores de carga entre as suítes.
"""

from collections.abc import Generator
from typing import Any

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.schemas import PolicyType


@pytest.fixture(scope="session")
def client() -> Generator[TestClient, None, None]:
    """Fixture de cliente HTTP de teste reutilizável para integração"""
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture
def correlation_headers() -> dict[str, str]:
    """Headers padrão de rastreabilidade para requisições"""
    return {"X-Correlation-ID": "test-corr-fixture-001"}


@pytest.fixture
def valid_minimum_stay_payload() -> dict[str, Any]:
    """Payload válido para avaliação de estadia mínima"""
    return {
        "policy": PolicyType.MINIMUM_STAY.value,
        "facts": {
            "requested_nights": 4,
            "required_minimum_nights": 2,
            "room_type": "standard",
        },
    }


@pytest.fixture
def valid_advance_booking_payload() -> dict[str, Any]:
    """Payload válido para avaliação de antecedência"""
    return {
        "policy": PolicyType.ADVANCE_BOOKING.value,
        "facts": {
            "days_in_advance": 10,
            "min_advance_days": 3,
        },
    }


@pytest.fixture
def valid_overbooking_payload() -> dict[str, Any]:
    """Payload válido para avaliação de teto de overbooking"""
    return {
        "policy": PolicyType.OVERBOOKING_LIMIT.value,
        "facts": {
            "total_capacity": 50,
            "current_occupied": 48,
            "requested_units": 2,
            "max_overbooking_rate": 0.05,
        },
    }
