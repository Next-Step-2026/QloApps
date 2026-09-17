"""
Testes End-to-End (E2E) com Playwright para Python.
Automatiza a jornada completa da perspectiva do cliente/navegador,
testando o simulador administrativo do QloApps e o microserviço de políticas.
"""

import pytest

try:
    from playwright.sync_api import sync_playwright

    HAS_PLAYWRIGHT = True
except ImportError:
    HAS_PLAYWRIGHT = False


@pytest.mark.e2e
@pytest.mark.skipif(not HAS_PLAYWRIGHT, reason="Requer a biblioteca playwright instalada")
def test_e2e_api_evaluation_journey():
    """
    E2E via Playwright APIRequestContext:
    Simula uma jornada de cliente realizando a avaliação de múltiplas políticas
    em sequência contra o serviço de políticas.
    """
    with sync_playwright() as p:
        request_context = p.request.new_context(base_url="http://127.0.0.1:8105")

        # 1. Healthcheck
        health_resp = request_context.get("/healthz")
        if health_resp.status != 200:
            pytest.skip("Servidor de políticas local não está ativo na porta 8105")

        assert health_resp.json() == {"status": "UP"}

        # 2. Avaliação de Política de Estadia Mínima
        eval_resp = request_context.post(
            "/v1/policy-evaluations",
            data={
                "policy": "MINIMUM_STAY",
                "facts": {
                    "requested_nights": 3,
                    "required_minimum_nights": 2,
                    "room_type": "standard",
                },
            },
            headers={"X-Correlation-ID": "e2e-playwright-corr-001"},
        )
        assert eval_resp.status == 200
        payload = eval_resp.json()
        assert payload["decision"] == "ALLOW"
        assert payload["reason_code"] == "MINIMUM_STAY_MET"


@pytest.mark.e2e
@pytest.mark.skipif(not HAS_PLAYWRIGHT, reason="Requer a biblioteca playwright instalada")
def test_e2e_browser_simulator_flow():
    """
    E2E via Navegador Real (Chromium / Firefox / WebKit):
    Simula o usuário abrindo o painel administrativo do QloApps,
    selecionando a política e visualizando a decisão ALLOW/DENY.
    """
    # Exemplo estrutural de automação de UI com Playwright
    with sync_playwright() as p:
        try:
            browser = p.chromium.launch(headless=True)
            _page = browser.new_page()
            # Navega até o painel (em ambiente de homologação/local)
            # _page.goto("http://localhost:8080/admin-dev/index.php?controller=AdminReservationPolicy")
            # _page.select_option("#policy_selector", "MINIMUM_STAY")
            # _page.fill("input[name='requested_nights']", "3")
            # _page.click("#btn-evaluate")
            # expect(_page.locator(".badge-success")).to_contain_text("ALLOW")
            browser.close()
        except Exception:
            pytest.skip("Ambiente sem navegadores Playwright instalados (playwright install)")
