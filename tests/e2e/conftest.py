import os
from pathlib import Path
import subprocess
import time
import urllib.request
import pytest
from playwright.sync_api import Browser, BrowserContext, Page

AUTH_DIR = Path(__file__).parent / ".auth"
AUTH_FILE = AUTH_DIR / "admin_auth.json"
SEARCH_SERVICE_DIR = Path(__file__).resolve().parents[2] / "search-service-cpp"
SEARCH_SERVICE_BIN = SEARCH_SERVICE_DIR / "search_service"

@pytest.fixture(scope="session", autouse=True)
def ensure_search_service():
    """Garante que o microsserviço C++ de busca esteja ativo durante a suíte de testes E2E."""
    def is_up():
        try:
            req = urllib.request.Request("http://127.0.0.1:8108/healthz")
            with urllib.request.urlopen(req, timeout=1.0) as res:
                return res.status == 200
        except Exception:
            return False

    proc = None
    if not is_up():
        if not SEARCH_SERVICE_BIN.exists():
            subprocess.run(["make", "build"], cwd=str(SEARCH_SERVICE_DIR), check=True)
        env = os.environ.copy()
        env["SEARCH_SERVICE_HOST"] = "0.0.0.0"
        env["SEARCH_SERVICE_PORT"] = "8108"
        proc = subprocess.Popen(
            [str(SEARCH_SERVICE_BIN)],
            cwd=str(SEARCH_SERVICE_DIR),
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        start = time.time()
        while time.time() - start < 5.0:
            if is_up():
                break
            time.sleep(0.1)

    yield

    if proc:
        proc.terminate()
        try:
            proc.wait(timeout=2.0)
        except subprocess.TimeoutExpired:
            proc.kill()

@pytest.fixture(scope="session")
def base_url() -> str:
    """Retorna a URL base do QloApps (padrão local Docker)."""
    return os.getenv("QLOAPPS_BASE_URL", "http://127.0.0.1:8080").rstrip("/")

@pytest.fixture(scope="session")
def admin_email() -> str:
    """Email do administrador para testes."""
    return os.getenv("QLOAPPS_ADMIN_EMAIL", "joaolisboa@google.com")

@pytest.fixture(scope="session")
def admin_password() -> str:
    """Senha do administrador para testes."""
    return os.getenv("QLOAPPS_ADMIN_PASSWORD", "Password")

@pytest.fixture(scope="session")
def admin_storage_state(browser: Browser, base_url: str, admin_email: str, admin_password: str) -> str:
    """
    Realiza o login uma única vez por sessão e salva os cookies/sessão em .auth/admin_auth.json.
    Permite que os demais testes acessem o painel já autenticados de forma instantânea.
    """
    AUTH_DIR.mkdir(parents=True, exist_ok=True)

    context = browser.new_context()
    page = context.new_page()

    login_url = f"{base_url}/admin-dev/"
    page.goto(login_url)

    # Verifica se a tela de login foi exibida
    if page.locator("#email").is_visible():
        page.fill("#email", admin_email)
        page.fill("#passwd", admin_password)
        page.click("button[name=submitLogin]")

        # Aguarda o redirecionamento para o Dashboard
        page.wait_for_url("**/index.php?controller=AdminDashboard*", timeout=15000)

    # Salva os cookies e storage de autenticação
    context.storage_state(path=str(AUTH_FILE))
    context.close()

    return str(AUTH_FILE)

@pytest.fixture
def admin_context(browser: Browser, admin_storage_state: str) -> BrowserContext:
    """Contexto do navegador já autenticado com a sessão do administrador."""
    context = browser.new_context(storage_state=admin_storage_state)
    yield context
    context.close()

@pytest.fixture
def admin_page(admin_context: BrowserContext, base_url: str) -> Page:
    """Página do navegador autenticada e posicionada no Dashboard administrativo."""
    page = admin_context.new_page()
    page.goto(f"{base_url}/admin-dev/")
    page.wait_for_load_state("domcontentloaded")
    return page
