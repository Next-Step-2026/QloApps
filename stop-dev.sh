#!/usr/bin/env bash
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ "$(basename "$ROOT_DIR")" = "scripts" ]; then
  ROOT_DIR="$(cd "$ROOT_DIR/.." && pwd)"
fi

echo "Parando QloApps e servicos sidecar..."

if [ -f "$ROOT_DIR/.logs/sidecars.pid" ]; then
  xargs kill 2>/dev/null < "$ROOT_DIR/.logs/sidecars.pid" || true
  rm -f "$ROOT_DIR/.logs/sidecars.pid"
fi

pkill -f "assistant_service|overlap_service|search_service|location-service-kotlin|contact-health-service-kotlin|converter-service-kotlin|app/main.py" 2>/dev/null || true
podman stop qloapps 2>/dev/null || true

echo "Ambiente encerrado com sucesso."
