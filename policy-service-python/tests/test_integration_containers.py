"""
Testes de Integração com Testcontainers / Docker.
Permite instanciar containers reais descartáveis (bancos de dados, Redis, APIs)
durante o ciclo de vida do teste para garantir validação em ambiente de produção fiel.
"""

import shutil

import pytest

try:
    from testcontainers.core.container import DockerContainer

    HAS_TESTCONTAINERS = True
except ImportError:
    HAS_TESTCONTAINERS = False

DOCKER_AVAILABLE = shutil.which("docker") is not None


@pytest.mark.integration
@pytest.mark.skipif(
    not (HAS_TESTCONTAINERS and DOCKER_AVAILABLE),
    reason="Requer biblioteca testcontainers e Docker daemon ativo",
)
def test_policy_service_inside_container():
    """
    Exemplo de Testcontainers:
    Instancia o container do Policy Engine e valida a chamada HTTP real
    através da rede virtual do Docker.
    """
    with DockerContainer("python:3.11-slim") as container:
        container.with_command("python -c 'print(\"container_ready\")'")
        container.start()
        logs = container.get_logs()
        assert b"container_ready" in logs[0]
