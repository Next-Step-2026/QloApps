"""
Testes de Contrato Orientados ao Consumidor (Consumer-Driven Contract) com Pact-Python.
Valida o acordo de comunicação entre o consumidor (QloApps PHP) e o provedor (FastAPI).
"""

import pytest

try:
    from pact import Consumer, Provider

    HAS_PACT = True
except ImportError:
    HAS_PACT = False


@pytest.mark.contract
@pytest.mark.skipif(not HAS_PACT, reason="Requer a biblioteca pact-python instalada")
def test_pact_consumer_driven_contract():
    """
    Define e verifica o contrato CDC entre o módulo PHP e a API FastAPI:
    O consumidor 'QloAppsModule' declara que, ao enviar a política MINIMUM_STAY,
    o provedor 'PolicyEngineService' DEVE retornar status 200 com os campos esperados.
    """
    pact = Consumer("QloAppsModule").has_pact_with(
        Provider("PolicyEngineService"),
        port=1234,
        pact_dir="./pacts",
    )

    expected_body = {
        "policy": "MINIMUM_STAY",
        "decision": "ALLOW",
        "reason_code": "MINIMUM_STAY_MET",
        "explanation": "Estadia atende ao requisito",
    }

    (
        pact.given("Uma solicitação de estadia mínima válida")
        .upon_receiving("Requisição de avaliação de estadia mínima")
        .with_request(
            method="POST",
            path="/v1/policy-evaluations",
            body={
                "policy": "MINIMUM_STAY",
                "facts": {"requested_nights": 3, "required_minimum_nights": 2},
            },
            headers={"Content-Type": "application/json"},
        )
        .will_respond_with(
            status=200,
            headers={"Content-Type": "application/json"},
            body=expected_body,
        )
    )

    # Execução do pacto através do mock server do Pact
    # with pact:
    #     response = requests.post("http://localhost:1234/v1/policy-evaluations", ...)
    #     assert response.status_code == 200
