#!/usr/bin/env bash
set -e

# Runner helper for Cypress E2E Tests - QloApps Visual Inspection
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Load .env file if available (from tests/e2e-cypress/.env or repository root)
if [ -f "${SCRIPT_DIR}/.env" ]; then
    echo "Loading environment variables from ${SCRIPT_DIR}/.env..."
    set -a
    # shellcheck disable=SC1090
    source "${SCRIPT_DIR}/.env"
    set +a
elif [ -f "${REPO_DIR}/.env" ]; then
    echo "Loading environment variables from ${REPO_DIR}/.env..."
    set -a
    # shellcheck disable=SC1090
    source "${REPO_DIR}/.env"
    set +a
fi

echo "=== [1/4] Checking Node.js runtime ==="
if ! command -v node >/dev/null 2>&1; then
    echo "ERROR: Node.js is required to execute Cypress."
    echo "Please install Node.js (v18+) via your package manager or nvm."
    echo "Example: curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash && nvm install 20"
    exit 1
fi
echo "Node $(node -v) detected."

echo "=== [2/4] Checking QloApps web server (port 8080) ==="
if ! curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/ >/dev/null 2>&1; then
    echo "WARNING: QloApps does not seem to respond at http://localhost:8080/"
else
    echo "QloApps server is accessible on port 8080."
fi

echo "=== [3/4] Checking Python inspection service (port 8102) ==="
if ! curl -s http://127.0.0.1:8102/healthz 2>/dev/null | grep -q '"UP"'; then
    echo "Python inspection service is NOT running on port 8102."
    echo "Starting Python inspection service in background..."
    cd "${REPO_DIR}/inspection-service-python"
    uvicorn app.main:app --port 8102 >/dev/null 2>&1 &
    PYTHON_PID=$!
    sleep 2
    if curl -s http://127.0.0.1:8102/healthz 2>/dev/null | grep -q '"UP"'; then
        echo "Python inspection service started successfully (PID: ${PYTHON_PID})."
    else
        echo "WARNING: Could not automatically start Python service. Please run 'cd inspection-service-python && uvicorn app.main:app --port 8102' manually."
    fi
else
    echo "Python inspection service is already UP on port 8102."
fi

echo "=== [4/4] Executing Cypress E2E Tests ==="
cd "${SCRIPT_DIR}"

if [ ! -d "node_modules" ]; then
    echo "Installing Cypress dependencies (first run)..."
    npm install
fi

# Browser selection (defaults to chrome, can be overridden by CYPRESS_BROWSER or --electron)
BROWSER="${CYPRESS_BROWSER:-chrome}"
if [ "$1" = "--electron" ] || [ "$2" = "--electron" ]; then
    BROWSER="electron"
fi

# Run cypress headless or interactive based on argument
if [ "$1" = "--open" ] || [ "$2" = "--open" ]; then
    npm run cypress:open
else
    echo "Running Cypress with browser: ${BROWSER}"
    npx cypress run --browser "${BROWSER}" --headless
fi
