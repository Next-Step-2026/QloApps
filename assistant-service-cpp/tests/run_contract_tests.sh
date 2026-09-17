#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
CPP_DIR="$ROOT_DIR/assistant-service-cpp"
OPENAPI_SPEC="$CPP_DIR/api/openapi.yaml"
TARGET_URL="http://127.0.0.1:8101"

echo "=== [CONTRACT] Iniciando Testes de Contrato OpenAPI 3.1 com Schemathesis ==="

# Verifica se o servico C++ esta ativo
if ! curl -s -f "$TARGET_URL/healthz" > /dev/null 2>&1; then
    echo "[AVISO] Servico C++ nao encontrado em $TARGET_URL. Iniciando temporariamente para o teste..."
    cd "$CPP_DIR"
    make clean && make
    ./assistant_service &
    CPP_PID=$!
    trap 'echo "[CLEANUP] Encerrando servico C++ (PID: $CPP_PID)..."; kill -15 $CPP_PID 2>/dev/null || true' EXIT
    sleep 1
fi

echo "[CONTRACT] Executando Schemathesis contra $TARGET_URL..."
uvx --from "schemathesis>=4.0.0" schemathesis run \
    "$OPENAPI_SPEC" \
    --url "$TARGET_URL" \
    -c not_a_server_error,status_code_conformance,content_type_conformance,response_schema_conformance \
    --max-examples=25

echo "=== [SUCESSO] Todos os testes de contrato foram validados com conformidade total! ==="
