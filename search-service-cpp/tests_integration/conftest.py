import os
import subprocess
import time
import pytest
import httpx

SEARCH_SERVICE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SEARCH_SERVICE_BIN = os.path.join(SEARCH_SERVICE_DIR, "search_service")
HOST = os.getenv("SEARCH_SERVICE_HOST", "127.0.0.1")
PORT = os.getenv("SEARCH_SERVICE_PORT", "8108")
BASE_URL = f"http://{HOST}:{PORT}"

@pytest.fixture(scope="session")
def server():
    """Garante que o microsserviço C++ esteja rodando durante os testes."""
    proc = None
    already_running = False

    # 1. Verifica se o servico ja esta ativo
    try:
        with httpx.Client(base_url=BASE_URL, timeout=1.0) as check_client:
            res = check_client.get("/healthz")
            if res.status_code == 200 and res.json().get("status") == "UP":
                already_running = True
    except Exception:
        already_running = False

    # 2. Se nao estiver ativo, compila (se necessario) e inicia o processo
    if not already_running:
        if not os.path.exists(SEARCH_SERVICE_BIN):
            subprocess.run(["make", "build"], cwd=SEARCH_SERVICE_DIR, check=True)

        env = os.environ.copy()
        env["SEARCH_SERVICE_HOST"] = HOST
        env["SEARCH_SERVICE_PORT"] = PORT
        proc = subprocess.Popen(
            [SEARCH_SERVICE_BIN],
            cwd=SEARCH_SERVICE_DIR,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )

        # Aguarda readiness probe (/healthz)
        ready = False
        start_time = time.time()
        while time.time() - start_time < 5.0:
            try:
                with httpx.Client(base_url=BASE_URL, timeout=1.0) as check_client:
                    res = check_client.get("/healthz")
                    if res.status_code == 200 and res.json().get("status") == "UP":
                        ready = True
                        break
            except Exception:
                time.sleep(0.1)

        if not ready:
            if proc:
                proc.terminate()
                proc.wait()
            pytest.fail(f"Falha ao inicializar o microsserviço search_service em {BASE_URL}")

    yield BASE_URL

    # 3. Finaliza o processo se tiver sido iniciado por este runner
    if proc:
        proc.terminate()
        try:
            proc.wait(timeout=2.0)
        except subprocess.TimeoutExpired:
            proc.kill()


@pytest.fixture(scope="session")
def client(server):
    """Cliente HTTPX conectado ao servidor para a sessão de testes."""
    with httpx.Client(base_url=server, timeout=5.0) as http_client:
        yield http_client
