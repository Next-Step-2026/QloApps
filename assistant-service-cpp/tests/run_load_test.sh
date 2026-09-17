#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
CPP_DIR="$ROOT_DIR/assistant-service-cpp"
K6_SCRIPT="$CPP_DIR/tests/k6_load_test.js"
TARGET_URL="http://127.0.0.1:8101"

echo "=== [LOAD TEST] Iniciando Testes de Carga e SLA com k6 ==="

# Localiza binario do k6
K6_BIN=$(which k6 || echo "$HOME/.local/bin/k6")
if [ ! -x "$K6_BIN" ]; then
    echo "[ERRO] k6 nao encontrado em PATH ou $HOME/.local/bin/k6."
    exit 1
fi

# Verifica se o servico C++ esta ativo
if ! curl -s -f "$TARGET_URL/healthz" > /dev/null 2>&1; then
    echo "[AVISO] Servico C++ nao encontrado em $TARGET_URL. Iniciando temporariamente..."
    cd "$CPP_DIR"
    make clean && make
    ./assistant_service &
    CPP_PID=$!
    trap 'echo "[CLEANUP] Encerrando servico C++ (PID: $CPP_PID)..."; kill -15 $CPP_PID 2>/dev/null || true' EXIT
    sleep 1
fi

echo "[LOAD TEST] Executando k6 com validacao de SLA P95 < 20ms..."
"$K6_BIN" run "$K6_SCRIPT"

echo "=== [SUCESSO] Teste de carga k6 concluido e todos os SLAs foram atingidos! ==="
