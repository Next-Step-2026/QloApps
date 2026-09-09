#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BIN="$DIR/assistant_service"
PORT=8101
BASE_URL="http://127.0.0.1:$PORT"

echo "=== [TEST] Iniciando Bateria de Testes da API C++ ==="

# 1. Garantir que o binario existe
if [ ! -f "$BIN" ]; then
    echo "[TEST] Binário não encontrado. Compilando..."
    make -C "$DIR"
fi

# 2. Iniciar servico em background
echo "[TEST] Iniciando serviço em $BASE_URL..."
"$BIN" &
PID=$!

# Garantir finalizacao do processo ao sair
cleanup() {
    echo "[TEST] Finalizando processo $PID..."
    kill -9 "$PID" 2>/dev/null || true
}
trap cleanup EXIT

# Aguardar subida do servico
sleep 0.5

# Teste 1: Healthcheck GET /healthz
echo -n "[TEST] 1. Validando GET /healthz ... "
RES_HEALTH=$(curl -s -w "\n%{http_code}" "$BASE_URL/healthz")
BODY_HEALTH=$(echo "$RES_HEALTH" | head -n -1)
STATUS_HEALTH=$(echo "$RES_HEALTH" | tail -n 1)

if [ "$STATUS_HEALTH" -eq 200 ] && echo "$BODY_HEALTH" | grep -q '"status":"UP"'; then
    echo "OK (HTTP 200)"
else
    echo "FALHOU (HTTP $STATUS_HEALTH: $BODY_HEALTH)"
    exit 1
fi

# Teste 2: POST /v1/assist/interpret sem payload obrigatorio (espera 400)
echo -n "[TEST] 2. Validando POST /v1/assist/interpret (payload incompleto) ... "
RES_BAD=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -d '{"query": "tem quarto suite?"}')
BODY_BAD=$(echo "$RES_BAD" | head -n -1)
STATUS_BAD=$(echo "$RES_BAD" | tail -n 1)

if [ "$STATUS_BAD" -eq 400 ] && echo "$BODY_BAD" | grep -q "INVALID_PAYLOAD"; then
    echo "OK (HTTP 400)"
else
    echo "FALHOU (HTTP $STATUS_BAD: $BODY_BAD)"
    exit 1
fi

# Teste 3: POST /v1/assist/interpret com AVAILABILITY_QUERY
echo -n "[TEST] 3. Validando POST /v1/assist/interpret (AVAILABILITY_QUERY) ... "
RES_AVAIL=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: test-ci-1" \
    -d '{"query": "tem quarto suite para depois de amanha?", "reference_date": "2026-08-27"}')
BODY_AVAIL=$(echo "$RES_AVAIL" | head -n -1)
STATUS_AVAIL=$(echo "$RES_AVAIL" | tail -n 1)

if [ "$STATUS_AVAIL" -eq 200 ] && echo "$BODY_AVAIL" | grep -q "AVAILABILITY_QUERY"; then
    echo "OK (HTTP 200 - AVAILABILITY_QUERY)"
else
    echo "FALHOU (HTTP $STATUS_AVAIL: $BODY_AVAIL)"
    exit 1
fi

# Teste 4: POST /v1/assist/interpret com POLICY_QUERY
echo -n "[TEST] 4. Validando POST /v1/assist/interpret (POLICY_QUERY) ... "
RES_POLICY=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -d '{"query": "qual a taxa de cancelamento?", "reference_date": "2026-08-27"}')
BODY_POLICY=$(echo "$RES_POLICY" | head -n -1)
STATUS_POLICY=$(echo "$RES_POLICY" | tail -n 1)

if [ "$STATUS_POLICY" -eq 200 ] && echo "$BODY_POLICY" | grep -q "POLICY_QUERY"; then
    echo "OK (HTTP 200 - POLICY_QUERY)"
else
    echo "FALHOU (HTTP $STATUS_POLICY: $BODY_POLICY)"
    exit 1
fi

# Teste 5: POST /v1/assist/interpret com RESERVATION_LOOKUP
echo -n "[TEST] 5. Validando POST /v1/assist/interpret (RESERVATION_LOOKUP) ... "
RES_LOOKUP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -d '{"query": "verificar status da reserva RES-9941", "reference_date": "2026-08-27"}')
BODY_LOOKUP=$(echo "$RES_LOOKUP" | head -n -1)
STATUS_LOOKUP=$(echo "$RES_LOOKUP" | tail -n 1)

if [ "$STATUS_LOOKUP" -eq 200 ] && echo "$BODY_LOOKUP" | grep -q "RESERVATION_LOOKUP" && echo "$BODY_LOOKUP" | grep -q "RES-9941"; then
    echo "OK (HTTP 200 - RESERVATION_LOOKUP)"
else
    echo "FALHOU (HTTP $STATUS_LOOKUP: $BODY_LOOKUP)"
    exit 1
fi

# Teste 6: POST /v1/assist/interpret com UNKNOWN (Fallback seguro)
echo -n "[TEST] 6. Validando POST /v1/assist/interpret (UNKNOWN fallback) ... "
RES_UNKNOWN=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -d '{"query": "qual o cardapio do almoco?", "reference_date": "2026-08-27"}')
BODY_UNKNOWN=$(echo "$RES_UNKNOWN" | head -n -1)
STATUS_UNKNOWN=$(echo "$RES_UNKNOWN" | tail -n 1)

if [ "$STATUS_UNKNOWN" -eq 200 ] && echo "$BODY_UNKNOWN" | grep -q "UNKNOWN"; then
    echo "OK (HTTP 200 - UNKNOWN)"
else
    echo "FALHOU (HTTP $STATUS_UNKNOWN: $BODY_UNKNOWN)"
    exit 1
fi

echo "=== [SUCESSO] Todos os 6 testes da API passaram com 100% de êxito! ==="
