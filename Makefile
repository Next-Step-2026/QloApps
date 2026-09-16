SHELL := /bin/bash
.DEFAULT_GOAL := help

# Diretórios dos Serviços Kotlin (Sidecars)
DIR_RFC003 := contact-health-service-kotlin
DIR_RFC004 := location-service-kotlin
DIR_RFC006 := converter-service-kotlin

.PHONY: help test-all test-kotlin test-parallel test-rfc003 test-rfc004 test-rfc006

help: ## Exibe a lista de comandos disponíveis
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-20s %s\n", $$1, $$2}'

# ------------------------------------------------------------------------------
# Testes Individuais (JUnit 5 via Gradle)
# ------------------------------------------------------------------------------
test-rfc003: ## Roda os testes da RFC-003 (contact-health-service)
	@echo "========================================================"
	@echo ">>> [RFC-003] Contact Health Service (Porta 8103)"
	@echo "========================================================"
	@cd $(DIR_RFC003) && ./gradlew test --no-daemon --rerun-tasks

test-rfc004: ## Roda os testes da RFC-004 (location-service)
	@echo "========================================================"
	@echo ">>> [RFC-004] Location & Geofencing Service (Porta 8104)"
	@echo "========================================================"
	@cd $(DIR_RFC004) && ./gradlew test --no-daemon --rerun-tasks

test-rfc006: ## Roda os testes da RFC-006 (converter-service)
	@echo "========================================================"
	@echo ">>> [RFC-006] Canonical Converter Service (Porta 8106)"
	@echo "========================================================"
	@cd $(DIR_RFC006) && ./gradlew test --no-daemon --rerun-tasks

# ------------------------------------------------------------------------------
# Suíte Kotlin
# ------------------------------------------------------------------------------
test-kotlin: test-rfc003 test-rfc004 test-rfc006 ## Roda as 3 features em sequencia

test-parallel: ## Roda as 3 features em paralelo sem misturar saidas (--output-sync)
	@$(MAKE) -j3 --output-sync=target test-rfc003 test-rfc004 test-rfc006

# ------------------------------------------------------------------------------
# Ponto de Entrada Geral (Extensivel para PHP, Python, C++)
# ------------------------------------------------------------------------------
test-all: test-kotlin ## Ponto de entrada unificado para execucao de testes
