#!/usr/bin/env bash
# ==============================================================================
# Smoke Test com cURL para o Microsserviço de Busca C++ (search-service-cpp)
# ==============================================================================
set -euo pipefail

HOST="${SEARCH_SERVICE_HOST:-127.0.0.1}"
PORT="${SEARCH_SERVICE_PORT:-8108}"
BASE_URL="http://${HOST}:${PORT}"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "=== Iniciando Smoke Tests via cURL em ${BASE_URL} ==="

# 1. Healthcheck
echo -n "1. Testando GET /healthz ... "
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/healthz")
if [ "$STATUS" -eq 200 ]; then
    echo -e "${GREEN}[OK]${NC} (HTTP 200)"
else
    echo -e "${RED}[FALHA]${NC} (HTTP ${STATUS})"
    exit 1
fi

# 2. Busca com Sucesso (POST /v1/search/parse)
echo -n "2. Testando POST /v1/search/parse com sucesso ... "
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/v1/search/parse" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: smoke-curl-1" \
    -d '{
        "query": "suite para 2 adultos com vista mar",
        "catalog": [
            {
                "id": "suite-01",
                "title": "Suíte Master Vista Mar",
                "capacity_adults": 2,
                "amenities": ["vista_mar"],
                "aliases": ["suite"]
            }
        ]
    }')

HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ] && echo "$BODY" | grep -q "suite-01"; then
    echo -e "${GREEN}[OK]${NC} (HTTP 200, entidade casada)"
else
    echo -e "${RED}[FALHA]${NC} (HTTP ${HTTP_CODE})"
    echo "$BODY"
    exit 1
fi

# 3. Validação de Erro - Query Vazia (RFC 7807)
echo -n "3. Testando validação de query vazia (esperado HTTP 400) ... "
ERR_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/v1/search/parse" \
    -H "Content-Type: application/json" \
    -d '{"query": "", "catalog": []}')

if [ "$ERR_STATUS" -eq 400 ]; then
    echo -e "${GREEN}[OK]${NC} (HTTP 400 retornado conforme RFC 7807)"
else
    echo -e "${RED}[FALHA]${NC} (HTTP ${ERR_STATUS} em vez de 400)"
    exit 1
fi

echo -e "\n${GREEN}>>> TODOS OS SMOKE TESTS COM CURL PASSARAM COM SUCESSO! <<<${NC}"
