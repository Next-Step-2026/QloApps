#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BIN="$DIR/assistant_service"
PORT=8101
BASE_URL="http://127.0.0.1:$PORT"
DEFAULT_CORR="ci-corr-test-01"

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
RES_HEALTH=$(curl -s -i "$BASE_URL/healthz")
if echo "$RES_HEALTH" | grep -q "200 OK" && echo "$RES_HEALTH" | grep -q '"status":"UP"' && echo "$RES_HEALTH" | grep -iq "content-type: application/json; charset=utf-8"; then
    echo "OK (HTTP 200, UTF-8 Content-Type)"
else
    echo "FAILED ($RES_HEALTH)"
    exit 1
fi

# Test 2: POST /v1/assist/interpret missing reference_date (expects 400 + RFC 7807 + problem+json)
echo -n "[TEST] 2. Validating missing reference_date (RFC 7807 400) ... "
RES_BAD=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d '{"query": "tem quarto suite?"}')

if echo "$RES_BAD" | grep -q "400 Bad Request" && echo "$RES_BAD" | grep -q "MISSING_REFERENCE_DATE" && echo "$RES_BAD" | grep -iq "content-type: application/problem+json"; then
    echo "OK (HTTP 400 - MISSING_REFERENCE_DATE, application/problem+json)"
else
    echo "FAILED ($RES_BAD)"
    exit 1
fi

# Test 3: POST /v1/assist/interpret invalid date format (expects 400)
echo -n "[TEST] 3. Validating invalid date format ... "
RES_INVDATE=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d '{"query": "tem quarto suite?", "reference_date": "27-08-2026"}')

if echo "$RES_INVDATE" | grep -q "400 Bad Request" && echo "$RES_INVDATE" | grep -q "INVALID_REFERENCE_DATE"; then
    echo "OK (HTTP 400 - INVALID_REFERENCE_DATE)"
else
    echo "FAILED ($RES_INVDATE)"
    exit 1
fi

# Test 4: POST /v1/assist/interpret AVAILABILITY_QUERY with relative date 'depois de amanhã' (+2 days)
echo -n "[TEST] 4. Validating AVAILABILITY_QUERY + 'depois de amanhã' (+2 days) ... "
RES_AVAIL=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: test-ci-1" \
    -d '{"query": "tem quarto suíte para depois de amanhã para 2 pessoas?", "reference_date": "2026-08-27"}')

if echo "$RES_AVAIL" | grep -q "200 OK" && \
   echo "$RES_AVAIL" | grep -q '"intent":"AVAILABILITY_QUERY"' && \
   echo "$RES_AVAIL" | grep -q '"check_in":"2026-08-29"' && \
   echo "$RES_AVAIL" | grep -q '"check_out":"2026-08-30"' && \
   echo "$RES_AVAIL" | grep -q '"guests":2' && \
   echo "$RES_AVAIL" | grep -q '"room_type":"suite"' && \
   echo "$RES_AVAIL" | grep -iq "content-type: application/json; charset=utf-8"; then
    echo "OK (HTTP 200 - check_in 2026-08-29, 2 guests, suite)"
else
    echo "FAILED ($RES_AVAIL)"
    exit 1
fi

# Test 5: Month Turnover Calculation (Aug 31 + 1 day = Sep 01)
echo -n "[TEST] 5. Validating month turnover arithmetic (2026-08-31 + amanhã) ... "
RES_MONTH=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d '{"query": "preciso de um quarto deluxe para amanhã para 4 pessoas", "reference_date": "2026-08-31"}')

if echo "$RES_MONTH" | grep -q "200 OK" && \
   echo "$RES_MONTH" | grep -q '"check_in":"2026-09-01"' && \
   echo "$RES_MONTH" | grep -q '"check_out":"2026-09-02"' && \
   echo "$RES_MONTH" | grep -q '"guests":4' && \
   echo "$RES_MONTH" | grep -q '"room_type":"deluxe"'; then
    echo "OK (HTTP 200 - check_in 2026-09-01, check_out 2026-09-02, 4 guests, deluxe)"
else
    echo "FAILED ($RES_MONTH)"
    exit 1
fi

# Test 6: Year Turnover Calculation (Dec 31 + 1 day = Jan 01 next year)
echo -n "[TEST] 6. Validating year turnover arithmetic (2026-12-31 + amanhã) ... "
RES_YEAR=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d '{"query": "tem quarto standard para amanhã", "reference_date": "2026-12-31"}')

if echo "$RES_YEAR" | grep -q "200 OK" && \
   echo "$RES_YEAR" | grep -q '"check_in":"2027-01-01"' && \
   echo "$RES_YEAR" | grep -q '"check_out":"2027-01-02"' && \
   echo "$RES_YEAR" | grep -q '"room_type":"standard"'; then
    echo "OK (HTTP 200 - check_in 2027-01-01, check_out 2027-01-02, standard)"
else
    echo "FAILED ($RES_YEAR)"
    exit 1
fi

# Test 7: POST /v1/assist/interpret POLICY_QUERY (cancellation)
echo -n "[TEST] 7. Validating POLICY_QUERY (cancellation) ... "
RES_POLICY=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d '{"query": "qual a taxa de cancelamento e multa por no-show?", "reference_date": "2026-08-27"}')

if echo "$RES_POLICY" | grep -q "200 OK" && \
   echo "$RES_POLICY" | grep -q '"intent":"POLICY_QUERY"' && \
   echo "$RES_POLICY" | grep -q '"policy_category":"cancellation"'; then
    echo "OK (HTTP 200 - POLICY_QUERY cancellation)"
else
    echo "FAILED ($RES_POLICY)"
    exit 1
fi

# Test 8: POST /v1/assist/interpret POLICY_QUERY (checkin_rules)
echo -n "[TEST] 8. Validating POLICY_QUERY (checkin_rules) ... "
RES_CHECKIN=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d '{"query": "qual o horario limite para check-in tardio?", "reference_date": "2026-08-27"}')

if echo "$RES_CHECKIN" | grep -q "200 OK" && \
   echo "$RES_CHECKIN" | grep -q '"intent":"POLICY_QUERY"' && \
   echo "$RES_CHECKIN" | grep -q '"policy_category":"checkin_rules"'; then
    echo "OK (HTTP 200 - POLICY_QUERY checkin_rules)"
else
    echo "FAILED ($RES_CHECKIN)"
    exit 1
fi

# Test 9: POST /v1/assist/interpret RESERVATION_LOOKUP
echo -n "[TEST] 9. Validating RESERVATION_LOOKUP ... "
RES_LOOKUP=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d '{"query": "gostaria de verificar status da reserva RES-9941", "reference_date": "2026-08-27"}')

if echo "$RES_LOOKUP" | grep -q "200 OK" && \
   echo "$RES_LOOKUP" | grep -q '"intent":"RESERVATION_LOOKUP"' && \
   echo "$RES_LOOKUP" | grep -q '"reservation_code":"RES-9941"'; then
    echo "OK (HTTP 200 - RESERVATION_LOOKUP RES-9941)"
else
    echo "FAILED ($RES_LOOKUP)"
    exit 1
fi

# Test 10: POST /v1/assist/interpret UNKNOWN fallback
echo -n "[TEST] 10. Validating UNKNOWN fallback ... "
RES_UNKNOWN=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d '{"query": "qual o cardapio do almoco de hoje no restaurante?", "reference_date": "2026-08-27"}')

if echo "$RES_UNKNOWN" | grep -q "200 OK" && echo "$RES_UNKNOWN" | grep -q '"intent":"UNKNOWN"'; then
    echo "OK (HTTP 200 - UNKNOWN fallback)"
else
    echo "FAILED ($RES_UNKNOWN)"
    exit 1
fi

# Test 11: Malformed JSON payload (expects 400 + problem+json)
echo -n "[TEST] 11. Validating malformed JSON handling ... "
RES_MALFORMED=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d '{not-a-valid-json}')

if echo "$RES_MALFORMED" | grep -q "400 Bad Request" && echo "$RES_MALFORMED" | grep -q "MALFORMED_JSON" && echo "$RES_MALFORMED" | grep -iq "content-type: application/problem+json"; then
    echo "OK (HTTP 400 - MALFORMED_JSON)"
else
    echo "FAILED ($RES_MALFORMED)"
    exit 1
fi

# Test 12: Query exceeding 256 chars (expects 400 QUERY_TOO_LONG)
echo -n "[TEST] 12. Validating query exceeding 256 chars (RFC 7807 400) ... "
LONG_QUERY=$(python3 -c 'print("tem quarto " + "a" * 250)')
RES_LONG=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d "{\"query\": \"$LONG_QUERY\", \"reference_date\": \"2026-08-27\"}")

if echo "$RES_LONG" | grep -q "400 Bad Request" && echo "$RES_LONG" | grep -q "QUERY_TOO_LONG"; then
    echo "OK (HTTP 400 - QUERY_TOO_LONG)"
else
    echo "FAILED ($RES_LONG)"
    exit 1
fi

# Test 13: Query with null value (expects 400 INVALID_QUERY_TYPE)
echo -n "[TEST] 13. Validating query with null value (RFC 7807 400) ... "
RES_NULL=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d '{"query": null, "reference_date": "2026-08-27"}')

if echo "$RES_NULL" | grep -q "400 Bad Request" && echo "$RES_NULL" | grep -q "INVALID_QUERY_TYPE"; then
    echo "OK (HTTP 400 - INVALID_QUERY_TYPE)"
else
    echo "FAILED ($RES_NULL)"
    exit 1
fi

# Test 14: Query with empty/whitespace-only string (expects 400 EMPTY_QUERY)
echo -n "[TEST] 14. Validating query with empty/whitespace-only string ... "
RES_EMPTY=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d '{"query": "   ", "reference_date": "2026-08-27"}')

if echo "$RES_EMPTY" | grep -q "400 Bad Request" && echo "$RES_EMPTY" | grep -q "EMPTY_QUERY"; then
    echo "OK (HTTP 400 - EMPTY_QUERY)"
else
    echo "FAILED ($RES_EMPTY)"
    exit 1
fi

# Test 15: Missing query field (expects 400 MISSING_QUERY)
echo -n "[TEST] 15. Validating missing query field ... "
RES_NOQUERY=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d '{"reference_date": "2026-08-27"}')

if echo "$RES_NOQUERY" | grep -q "400 Bad Request" && echo "$RES_NOQUERY" | grep -q "MISSING_QUERY"; then
    echo "OK (HTTP 400 - MISSING_QUERY)"
else
    echo "FAILED ($RES_NOQUERY)"
    exit 1
fi

# Test 16: Boundary test: query with exactly 256 characters (expects 200)
echo -n "[TEST] 16. Validating boundary query with exactly 256 characters ... "
EXACT_256_QUERY=$(python3 -c 'prefix = "tem quarto suite "; print(prefix + "x" * (256 - len(prefix)))')
RES_EXACT=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d "{\"query\": \"$EXACT_256_QUERY\", \"reference_date\": \"2026-08-27\"}")

if echo "$RES_EXACT" | grep -q "200 OK" && echo "$RES_EXACT" | grep -q "AVAILABILITY_QUERY"; then
    echo "OK (HTTP 200 - boundary 256 chars)"
else
    echo "FAILED ($RES_EXACT)"
    exit 1
fi

# Test 17: Missing Content-Type header (expects 415 UNSUPPORTED_MEDIA_TYPE)
echo -n "[TEST] 17. Validating missing Content-Type header (RFC 7807 415) ... "
RES_NOCT=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d '{"query": "tem quarto suite?", "reference_date": "2026-08-27"}')

if echo "$RES_NOCT" | grep -q "415 Unsupported Media Type" && echo "$RES_NOCT" | grep -q "UNSUPPORTED_MEDIA_TYPE" && echo "$RES_NOCT" | grep -iq "content-type: application/problem+json"; then
    echo "OK (HTTP 415 - UNSUPPORTED_MEDIA_TYPE, application/problem+json)"
else
    echo "FAILED ($RES_NOCT)"
    exit 1
fi

# Test 18: Incompatible Content-Type text/plain (expects 415 UNSUPPORTED_MEDIA_TYPE)
echo -n "[TEST] 18. Validating incompatible Content-Type: text/plain (RFC 7807 415) ... "
RES_BADCT=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: text/plain" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d '{"query": "tem quarto suite?", "reference_date": "2026-08-27"}')

if echo "$RES_BADCT" | grep -q "415 Unsupported Media Type" && echo "$RES_BADCT" | grep -q "UNSUPPORTED_MEDIA_TYPE"; then
    echo "OK (HTTP 415 - UNSUPPORTED_MEDIA_TYPE)"
else
    echo "FAILED ($RES_BADCT)"
    exit 1
fi

# Test 19: Content-Type with substring text/application/json (expects 415 UNSUPPORTED_MEDIA_TYPE)
echo -n "[TEST] 19. Validating invalid substring Content-Type: text/application/json ... "
RES_SUBCT=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: text/application/json" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d '{"query": "tem quarto suite?", "reference_date": "2026-08-27"}')

if echo "$RES_SUBCT" | grep -q "415 Unsupported Media Type" && echo "$RES_SUBCT" | grep -q "UNSUPPORTED_MEDIA_TYPE"; then
    echo "OK (HTTP 415 - rejected substring media type)"
else
    echo "FAILED ($RES_SUBCT)"
    exit 1
fi

# Test 20: Content-Type with valid charset: application/json; charset=utf-8 (expects 200 OK)
echo -n "[TEST] 20. Validating Content-Type: application/json; charset=utf-8 ... "
RES_CHARSET=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json; charset=utf-8" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d '{"query": "tem quarto suite para amanha?", "reference_date": "2026-08-27"}')

if echo "$RES_CHARSET" | grep -q "200 OK" && echo "$RES_CHARSET" | grep -q "AVAILABILITY_QUERY"; then
    echo "OK (HTTP 200 - accepted application/json; charset=utf-8)"
else
    echo "FAILED ($RES_CHARSET)"
    exit 1
fi

# Test 21: Missing X-Correlation-ID header (expects 400 MISSING_CORRELATION_ID)
echo -n "[TEST] 21. Validating missing X-Correlation-ID header ... "
RES_NOCORR=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -d '{"query": "tem quarto suite?", "reference_date": "2026-08-27"}')

if echo "$RES_NOCORR" | grep -q "400 Bad Request" && echo "$RES_NOCORR" | grep -q "MISSING_CORRELATION_ID"; then
    echo "OK (HTTP 400 - MISSING_CORRELATION_ID)"
else
    echo "FAILED ($RES_NOCORR)"
    exit 1
fi

# Test 22: X-Correlation-ID propagation in response header
echo -n "[TEST] 22. Validating X-Correlation-ID response header propagation ... "
TEST_CORR_UUID="trace-uuid-8899-xyz"
RES_PROP=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $TEST_CORR_UUID" \
    -d '{"query": "tem quarto suite para amanha?", "reference_date": "2026-08-27"}')

if echo "$RES_PROP" | grep -iq "x-correlation-id: $TEST_CORR_UUID"; then
    echo "OK (X-Correlation-ID echoed in response headers)"
else
    echo "FAILED ($RES_PROP)"
    exit 1
fi

# Test 23: Text normalization with control characters (\n, \r, \t, \0)
echo -n "[TEST] 23. Validating text normalization removing control characters ... "
CTRL_QUERY=$(python3 -c 'import sys; sys.stdout.write("tem\\nquarto\\tsuite\\rpara\\x00amanha")')
RES_CTRL=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d "{\"query\": \"tem\nquarto\tsuite\rpara amanhã\", \"reference_date\": \"2026-08-27\"}")

if echo "$RES_CTRL" | grep -q "200 OK" && echo "$RES_CTRL" | grep -q '"intent":"AVAILABILITY_QUERY"' && echo "$RES_CTRL" | grep -q '"check_in":"2026-08-28"'; then
    echo "OK (HTTP 200 - successfully normalized control characters)"
else
    echo "FAILED ($RES_CTRL)"
    exit 1
fi

# Test 24: Availability query without temporal keywords (must return check_in: null, check_out: null)
echo -n "[TEST] 24. Validating availability query without temporal expression (null dates) ... "
RES_NODATE=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d '{"query": "tem quarto deluxe disponivel?", "reference_date": "2026-08-27"}')

if echo "$RES_NODATE" | grep -q "200 OK" && \
   echo "$RES_NODATE" | grep -q '"intent":"AVAILABILITY_QUERY"' && \
   echo "$RES_NODATE" | grep -q '"check_in":null' && \
   echo "$RES_NODATE" | grep -q '"check_out":null' && \
   ! echo "$RES_NODATE" | grep -q "data padrao"; then
    echo "OK (HTTP 200 - check_in and check_out are null, no invented dates)"
else
    echo "FAILED ($RES_NODATE)"
    exit 1
fi

# Test 25: Content-Type with invalid parameter: application/json; foo=bar (expects 415 UNSUPPORTED_MEDIA_TYPE)
echo -n "[TEST] 25. Validating Content-Type with unknown parameter: application/json; foo=bar ... "
RES_FOOBAR=$(curl -s -i -X POST "$BASE_URL/v1/assist/interpret" \
    -H "Content-Type: application/json; foo=bar" \
    -H "X-Correlation-ID: $DEFAULT_CORR" \
    -d '{"query": "tem quarto suite para amanha?", "reference_date": "2026-08-27"}')

if echo "$RES_FOOBAR" | grep -q "415 Unsupported Media Type" && echo "$RES_FOOBAR" | grep -q "UNSUPPORTED_MEDIA_TYPE"; then
    echo "OK (HTTP 415 - rejected unknown parameter foo=bar)"
else
    echo "FAILED ($RES_FOOBAR)"
    exit 1
fi

echo "=== [SUCCESS] All 25 API tests passed successfully! ==="
