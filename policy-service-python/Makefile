# ==============================================================================
# Pipeline de Qualidade e DevSecOps - Reservation Policy Engine
# Execução local das 39 categorias via CLI de alta performance (Rust / Go)
# ==============================================================================

PYTHON ?= python3
VENV ?= venv
BIN = $(VENV)/bin
RUFF ?= $(shell command -v ruff 2>/dev/null || ( [ -f $(BIN)/ruff ] && echo $(BIN)/ruff ) || echo ruff)
PYRIGHT ?= $(shell command -v pyright 2>/dev/null || ( [ -f $(BIN)/pyright ] && echo $(BIN)/pyright ) || echo pyright)
RIPGREP ?= rg
GITLEAKS ?= gitleaks
TRIVY ?= trivy
K6 ?= k6

.PHONY: help fast lint format check-syntax typecheck test test-cov security contract mutate load-test all

help:
	@echo "Alvos disponíveis:"
	@echo "  make fast         - Executa o Tier 1: Syntax, Format, Lint, Types, Tests (< 10s)"
	@echo "  make lint         - Executa Ruff Linter e Formatter check"
	@echo "  make format       - Formata o código automaticamente com Ruff"
	@echo "  make typecheck    - Análise estática de tipos com Pyright"
	@echo "  make test         - Executa suíte de testes com Pytest"
	@echo "  make test-cov     - Testes com relatório de cobertura de código"
	@echo "  make security     - Executa SAST, Secrets (Gitleaks) e auditoria de dependências"
	@echo "  make contract     - Validação de contrato OpenAPI com Schemathesis"
	@echo "  make mutate       - Teste de mutação com Mutmut"
	@echo "  make load-test    - Teste de carga e stress com k6"
	@echo "  make docker-build - Constrói imagem Docker otimizada"
	@echo "  make docker-scan  - Escaneia vulnerabilidades na imagem com Trivy"

# ------------------------------------------------------------------------------
# 1. Tier 1: Fast Feedback (Desenvolvimento Local & Pre-commit)
# ------------------------------------------------------------------------------
check-syntax:
	@echo "==> [3/39] Verificando integridade de bytecode e sintaxe..."
	$(PYTHON) -m py_compile app/*.py tests/*.py

lint:
	@echo "==> [2, 4, 5, 9/39] Executando Ruff (AST, Formatter, Linter, McCabe C90)..."
	@if command -v $(RUFF) > /dev/null; then \
		$(RUFF) check app/ tests/; \
		$(RUFF) format --check app/ tests/; \
	else \
		echo "Aviso: ruff não encontrado no PATH. Instale com: pip install ruff ou via uv."; \
	fi

format:
	@echo "==> [4, 13/39] Formatando e refatorando código com Ruff..."
	@if command -v $(RUFF) > /dev/null; then \
		$(RUFF) format app/ tests/; \
		$(RUFF) check --fix app/ tests/; \
	fi

typecheck:
	@echo "==> [6/39] Verificação estática de tipos..."
	@if command -v $(PYRIGHT) > /dev/null; then \
		$(PYRIGHT) app/; \
	elif [ -f "$(BIN)/pyright" ]; then \
		$(BIN)/pyright app/; \
	else \
		echo "Pyright não encontrado. Pulando type check local."; \
	fi

test:
	@echo "==> [15, 16, 22/39] Executando testes com Pytest..."
	PYTHONPATH=. $(BIN)/pytest tests/

test-cov:
	@echo "==> [19/39] Cobertura de código..."
	PYTHONPATH=. $(BIN)/pytest --cov=app --cov-report=term-missing --cov-report=xml

fast: check-syntax lint test

# ------------------------------------------------------------------------------
# 2. Tier 2: Segurança, SAST, SCA e Contrato
# ------------------------------------------------------------------------------
security:
	@echo "==> [29/39] Secrets scanning com Gitleaks..."
	@if command -v $(GITLEAKS) > /dev/null; then \
		$(GITLEAKS) detect --source=. -v; \
	fi
	@echo "==> [7/39] SAST com regras Bandit via Ruff..."
	@if command -v $(RUFF) > /dev/null; then \
		$(RUFF) check app/ --select S; \
	fi
	@echo "==> [27/39] Auditoria de dependências com Trivy..."
	@if command -v $(TRIVY) > /dev/null; then \
		$(TRIVY) fs --scanners vuln requirements.txt; \
	fi

contract:
	@echo "==> [18/39] Testes de Contrato OpenAPI com Schemathesis..."
	PYTHONPATH=. st run http://127.0.0.1:8105/openapi.json --checks all --exclude-checks positive_data_acceptance

# ------------------------------------------------------------------------------
# 3. Tier 3: Verificação Profunda, Performance e Containers
# ------------------------------------------------------------------------------
mutate:
	@echo "==> [20/39] Mutation Testing com Mutmut..."
	PYTHONPATH=. mutmut run --paths-to-mutate=app/engine.py

load-test:
	@echo "==> [25/39] Teste de Carga com k6..."
	@if command -v $(K6) > /dev/null; then \
		$(K6) run scripts/load-test.js; \
	else \
		echo "k6 não encontrado no PATH."; \
	fi

docker-build:
	@echo "==> [31, 38/39] Construindo imagem Docker..."
	docker build -t policy-service:latest .

docker-scan: docker-build
	@echo "==> [30, 31/39] Escaneando imagem Docker com Trivy..."
	$(TRIVY) image --severity HIGH,CRITICAL policy-service:latest

all: fast security test-cov
