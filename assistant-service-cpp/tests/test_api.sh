#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BIN="$DIR/assistant_service"
PORT=8101
BASE_URL="http://127.0.0.1:$PORT"

echo "=== [TEST] Starting Comprehensive C++ API Test Suite ==="

# 1. Ensure binary exists
if [ ! -f "$BIN" ]; then
    echo "[TEST] Binary not found. Building..."
    make -C "$DIR"
fi

# 2. Start service in background
echo "[TEST] Starting service at $BASE_URL..."
"$BIN" &
PID=$!

# Ensure process termination on exit
cleanup() {
    echo "[TEST] Stopping process $PID..."
    kill -9 "$PID" 2>/dev/null || true
}
trap cleanup EXIT

# Wait for service startup
sleep 0.5

# Test 1: Healthcheck GET /healthz
echo -n "[TEST] 1. Validating GET /healthz ... "
RES_HEALTH=$(curl -s -w "\n%{http_code}" "$BASE_URL/healthz")
BODY_HEALTH=$(echo "$RES_HEALTH" | head -n -1)
STATUS_HEALTH=$(echo "$RES_HEALTH" | tail -n 1)

if [ "$STATUS_HEALTH" -eq 200 ] && echo "$BODY_HEALTH" | grep -q '"status":"UP"'; then
    echo "OK (HTTP 200)"
else
    echo "FAILED (HTTP $STATUS_HEALTH: $BODY_HEALTH)"
    exit 1
fi

# Test 2: POST /v1/assist/interpret missing reference_date (expects 400 + RFC 7807)
echo -n "[TEST] 2. Validating missing reference_date (RFC 7807 400) ... "
RES_BAD=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -d '{"query": "tem quarto suite?"}')
BODY_BAD=$(echo "$RES_BAD" | head -n -1)
STATUS_BAD=$(echo "$RES_BAD" | tail -n 1)

if [ "$STATUS_BAD" -eq 400 ] && echo "$BODY_BAD" | grep -q "MISSING_REFERENCE_DATE"; then
    echo "OK (HTTP 400 - MISSING_REFERENCE_DATE)"
else
    echo "FAILED (HTTP $STATUS_BAD: $BODY_BAD)"
    exit 1
fi

# Test 3: POST /v1/assist/interpret invalid date format (expects 400)
echo -n "[TEST] 3. Validating invalid date format ... "
RES_INVDATE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -d '{"query": "tem quarto suite?", "reference_date": "27-08-2026"}')
BODY_INVDATE=$(echo "$RES_INVDATE" | head -n -1)
STATUS_INVDATE=$(echo "$RES_INVDATE" | tail -n 1)

if [ "$STATUS_INVDATE" -eq 400 ] && echo "$BODY_INVDATE" | grep -q "INVALID_REFERENCE_DATE"; then
    echo "OK (HTTP 400 - INVALID_REFERENCE_DATE)"
else
    echo "FAILED (HTTP $STATUS_INVDATE: $BODY_INVDATE)"
    exit 1
fi

# Test 4: POST /v1/assist/interpret AVAILABILITY_QUERY with relative date 'depois de amanhã' (+2 days)
echo -n "[TEST] 4. Validating AVAILABILITY_QUERY + 'depois de amanhã' (+2 days) ... "
RES_AVAIL=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: test-ci-1" \
    -d '{"query": "tem quarto suíte para depois de amanhã para 2 pessoas?", "reference_date": "2026-08-27"}')
BODY_AVAIL=$(echo "$RES_AVAIL" | head -n -1)
STATUS_AVAIL=$(echo "$RES_AVAIL" | tail -n 1)

if [ "$STATUS_AVAIL" -eq 200 ] && \
   echo "$BODY_AVAIL" | grep -q '"intent":"AVAILABILITY_QUERY"' && \
   echo "$BODY_AVAIL" | grep -q '"check_in":"2026-08-29"' && \
   echo "$BODY_AVAIL" | grep -q '"check_out":"2026-08-30"' && \
   echo "$BODY_AVAIL" | grep -q '"guests":2' && \
   echo "$BODY_AVAIL" | grep -q '"room_type":"suite"'; then
    echo "OK (HTTP 200 - check_in 2026-08-29, 2 guests, suite)"
else
    echo "FAILED (HTTP $STATUS_AVAIL: $BODY_AVAIL)"
    exit 1
fi

# Test 5: Month Turnover Calculation (Aug 31 + 1 day = Sep 01)
echo -n "[TEST] 5. Validating month turnover arithmetic (2026-08-31 + amanhã) ... "
RES_MONTH=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -d '{"query": "preciso de um quarto deluxe para amanhã para 4 pessoas", "reference_date": "2026-08-31"}')
BODY_MONTH=$(echo "$RES_MONTH" | head -n -1)
STATUS_MONTH=$(echo "$RES_MONTH" | tail -n 1)

if [ "$STATUS_MONTH" -eq 200 ] && \
   echo "$BODY_MONTH" | grep -q '"check_in":"2026-09-01"' && \
   echo "$BODY_MONTH" | grep -q '"check_out":"2026-09-02"' && \
   echo "$BODY_MONTH" | grep -q '"guests":4' && \
   echo "$BODY_MONTH" | grep -q '"room_type":"deluxe"'; then
    echo "OK (HTTP 200 - check_in 2026-09-01, check_out 2026-09-02, 4 guests, deluxe)"
else
    echo "FAILED (HTTP $STATUS_MONTH: $BODY_MONTH)"
    exit 1
fi

# Test 6: Year Turnover Calculation (Dec 31 + 1 day = Jan 01 next year)
echo -n "[TEST] 6. Validating year turnover arithmetic (2026-12-31 + amanhã) ... "
RES_YEAR=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -d '{"query": "tem quarto standard para amanhã", "reference_date": "2026-12-31"}')
BODY_YEAR=$(echo "$RES_YEAR" | head -n -1)
STATUS_YEAR=$(echo "$RES_YEAR" | tail -n 1)

if [ "$STATUS_YEAR" -eq 200 ] && \
   echo "$BODY_YEAR" | grep -q '"check_in":"2027-01-01"' && \
   echo "$BODY_YEAR" | grep -q '"check_out":"2027-01-02"' && \
   echo "$BODY_YEAR" | grep -q '"room_type":"standard"'; then
    echo "OK (HTTP 200 - check_in 2027-01-01, check_out 2027-01-02, standard)"
else
    echo "FAILED (HTTP $STATUS_YEAR: $BODY_YEAR)"
    exit 1
fi

# Test 7: POST /v1/assist/interpret POLICY_QUERY (cancellation)
echo -n "[TEST] 7. Validating POLICY_QUERY (cancellation) ... "
RES_POLICY=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -d '{"query": "qual a taxa de cancelamento e multa por no-show?", "reference_date": "2026-08-27"}')
BODY_POLICY=$(echo "$RES_POLICY" | head -n -1)
STATUS_POLICY=$(echo "$RES_POLICY" | tail -n 1)

if [ "$STATUS_POLICY" -eq 200 ] && \
   echo "$BODY_POLICY" | grep -q '"intent":"POLICY_QUERY"' && \
   echo "$BODY_POLICY" | grep -q '"policy_category":"cancellation"'; then
    echo "OK (HTTP 200 - POLICY_QUERY cancellation)"
else
    echo "FAILED (HTTP $STATUS_POLICY: $BODY_POLICY)"
    exit 1
fi

# Test 8: POST /v1/assist/interpret POLICY_QUERY (checkin_rules)
echo -n "[TEST] 8. Validating POLICY_QUERY (checkin_rules) ... "
RES_CHECKIN=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -d '{"query": "qual o horario limite para check-in tardio?", "reference_date": "2026-08-27"}')
BODY_CHECKIN=$(echo "$RES_CHECKIN" | head -n -1)
STATUS_CHECKIN=$(echo "$RES_CHECKIN" | tail -n 1)

if [ "$STATUS_CHECKIN" -eq 200 ] && \
   echo "$BODY_CHECKIN" | grep -q '"intent":"POLICY_QUERY"' && \
   echo "$BODY_CHECKIN" | grep -q '"policy_category":"checkin_rules"'; then
    echo "OK (HTTP 200 - POLICY_QUERY checkin_rules)"
else
    echo "FAILED (HTTP $STATUS_CHECKIN: $BODY_CHECKIN)"
    exit 1
fi

# Test 9: POST /v1/assist/interpret RESERVATION_LOOKUP
echo -n "[TEST] 9. Validating RESERVATION_LOOKUP ... "
RES_LOOKUP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -d '{"query": "gostaria de verificar status da reserva RES-9941", "reference_date": "2026-08-27"}')
BODY_LOOKUP=$(echo "$RES_LOOKUP" | head -n -1)
STATUS_LOOKUP=$(echo "$RES_LOOKUP" | tail -n 1)

if [ "$STATUS_LOOKUP" -eq 200 ] && \
   echo "$BODY_LOOKUP" | grep -q '"intent":"RESERVATION_LOOKUP"' && \
   echo "$BODY_LOOKUP" | grep -q '"reservation_code":"RES-9941"'; then
    echo "OK (HTTP 200 - RESERVATION_LOOKUP RES-9941)"
else
    echo "FAILED (HTTP $STATUS_LOOKUP: $BODY_LOOKUP)"
    exit 1
fi

# Test 10: POST /v1/assist/interpret UNKNOWN fallback
echo -n "[TEST] 10. Validating UNKNOWN fallback ... "
RES_UNKNOWN=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -d '{"query": "qual o cardapio do almoco de hoje no restaurante?", "reference_date": "2026-08-27"}')
BODY_UNKNOWN=$(echo "$RES_UNKNOWN" | head -n -1)
STATUS_UNKNOWN=$(echo "$RES_UNKNOWN" | tail -n 1)

if [ "$STATUS_UNKNOWN" -eq 200 ] && echo "$BODY_UNKNOWN" | grep -q '"intent":"UNKNOWN"'; then
    echo "OK (HTTP 200 - UNKNOWN fallback)"
else
    echo "FAILED (HTTP $STATUS_UNKNOWN: $BODY_UNKNOWN)"
    exit 1
fi

# Test 11: Malformed JSON payload (expects 400)
echo -n "[TEST] 11. Validating malformed JSON handling ... "
RES_MALFORMED=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -d '{not-a-valid-json}')
BODY_MALFORMED=$(echo "$RES_MALFORMED" | head -n -1)
STATUS_MALFORMED=$(echo "$RES_MALFORMED" | tail -n 1)

if [ "$STATUS_MALFORMED" -eq 400 ] && echo "$BODY_MALFORMED" | grep -q "MALFORMED_JSON"; then
    echo "OK (HTTP 400 - MALFORMED_JSON)"
else
    echo "FAILED (HTTP $STATUS_MALFORMED: $BODY_MALFORMED)"
    exit 1
fi

echo "=== [SUCCESS] All 11 API tests passed successfully! ==="
