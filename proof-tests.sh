#!/usr/bin/env bash
#
# Slotify proof scripts - run these against a locally running instance
# (docker compose up -d && ./mvnw spring-boot:run) before recording the demo.
#
# Usage:
#   chmod +x proof-tests.sh
#   ./proof-tests.sh concurrency   # fires N parallel bookings at ONE slot
#   ./proof-tests.sh cancellation  # book -> cancel -> confirm slot reopens
#   ./proof-tests.sh broker-kill   # stop RabbitMQ mid-flow, confirm no lost event
#   ./proof-tests.sh all           # runs all three in order

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
CONCURRENT_REQUESTS="${CONCURRENT_REQUESTS:-50}"

green() { printf "\033[32m%s\033[0m\n" "$1"; }
red()   { printf "\033[31m%s\033[0m\n" "$1"; }
info()  { printf "\033[36m%s\033[0m\n" "$1"; }

get_available_slot_id() {
    curl -s "${BASE_URL}/api/v1/slots/available" \
        | jq -r '.data[0].slotId // .data[0].id // empty'
}

# ---------------------------------------------------------------------------
concurrency_test() {
    info "=== CONCURRENCY TEST: ${CONCURRENT_REQUESTS} parallel bookings at ONE slot ==="

    SLOT_ID=$(get_available_slot_id)
    if [ -z "$SLOT_ID" ]; then
        red "No available slot found. Restart the app to reseed, or free one up first."
        exit 1
    fi
    info "Target slot ID: ${SLOT_ID}"

    RESULTS_DIR=$(mktemp -d)
    VALID_CLIENT_ID="${CLIENT_ID:-3}" # Existing DB User ID (e.g., Charlie Client)

    for i in $(seq 1 "$CONCURRENT_REQUESTS"); do
        RATE_LIMIT_USER_ID=$((100 + i)) # Dynamic header to bypass rate limiting
        IDEMPOTENCY_KEY=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || echo "key-${i}-${RANDOM}")
        (
            HTTP_CODE=$(curl -s -m 5 -o "${RESULTS_DIR}/body_${i}.json" -w "%{http_code}" \
                -X POST "${BASE_URL}/api/v1/bookings" \
                -H "Content-Type: application/json" \
                -H "X-User-Id: ${RATE_LIMIT_USER_ID}" \
                -H "X-Idempotency-Key: ${IDEMPOTENCY_KEY}" \
                -d "{\"slotId\":${SLOT_ID},\"clientId\":${VALID_CLIENT_ID},\"idempotencyKey\":\"${IDEMPOTENCY_KEY}\"}")
            echo "$HTTP_CODE" > "${RESULTS_DIR}/status_${i}.txt"
        ) &
    done
    wait

    SUCCESS_COUNT=$(grep -l "^201$" "${RESULTS_DIR}"/status_*.txt 2>/dev/null | wc -l | tr -d ' ')
    CONFLICT_COUNT=$(grep -l "^409$" "${RESULTS_DIR}"/status_*.txt 2>/dev/null | wc -l | tr -d ' ')
    OTHER_COUNT=$((CONCURRENT_REQUESTS - SUCCESS_COUNT - CONFLICT_COUNT))

    echo ""
    info "Results out of ${CONCURRENT_REQUESTS} concurrent requests:"
    green  "  201 Created (booking succeeded): ${SUCCESS_COUNT}"
    info   "  409 Conflict (slot already booked): ${CONFLICT_COUNT}"
    [ "$OTHER_COUNT" -ne 0 ] && red "  Other/unexpected status codes: ${OTHER_COUNT}"

    if [ "$SUCCESS_COUNT" -eq 1 ]; then
        green "PASS: exactly one booking succeeded. Double-booking prevented under concurrent load."
    else
        red "FAIL: expected exactly 1 success, got ${SUCCESS_COUNT}. Investigate the lock/version logic."
    fi
    rm -rf "$RESULTS_DIR"
}

# ---------------------------------------------------------------------------
cancellation_test() {
    info "=== CANCELLATION FLOW TEST: book -> cancel -> slot reopens ==="

    # Flush Redis cache so get_available_slot_id fetches fresh state from PostgreSQL
    docker compose exec -T redis redis-cli flushall > /dev/null 2>&1 || true

    SLOT_ID=$(get_available_slot_id)
    if [ -z "$SLOT_ID" ]; then
        red "No available slot found."
        exit 1
    fi
    info "Booking slot ID: ${SLOT_ID}"

    VALID_CLIENT_ID="${CLIENT_ID:-3}"
    IDEMPOTENCY_KEY=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || echo "cancel-${RANDOM}")

    BOOKING_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/bookings" \
        -H "Content-Type: application/json" \
        -H "X-User-Id: cancel_test_user" \
        -H "X-Idempotency-Key: ${IDEMPOTENCY_KEY}" \
        -d "{\"slotId\":${SLOT_ID},\"clientId\":${VALID_CLIENT_ID},\"idempotencyKey\":\"${IDEMPOTENCY_KEY}\"}")

    BOOKING_ID=$(echo "$BOOKING_RESPONSE" | jq -r '.data.bookingId // .data.id // empty')
    if [ -z "$BOOKING_ID" ]; then
        red "FAIL: booking creation didn't return a bookingId. Response was:"
        echo "$BOOKING_RESPONSE"
        exit 1
    fi
    green "Booked. bookingId=${BOOKING_ID}"

    STILL_AVAILABLE=$(curl -s "${BASE_URL}/api/v1/slots/available" | jq -r --arg sid "$SLOT_ID" '.data[]? | select((.slotId // .id) == ($sid | tonumber)) | (.slotId // .id)')
    if [ -n "$STILL_AVAILABLE" ]; then
        red "FAIL: slot ${SLOT_ID} still shows AVAILABLE right after booking it."
        exit 1
    fi
    green "Confirmed: slot ${SLOT_ID} no longer appears in available list."

    info "Cancelling booking ${BOOKING_ID}..."
    CANCEL_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "${BASE_URL}/api/v1/bookings/${BOOKING_ID}/cancel" \
        -H "X-User-Id: ${VALID_CLIENT_ID}")
    CANCEL_STATUS=$(echo "$CANCEL_RESPONSE" | tail -n1)
    if [ "$CANCEL_STATUS" != "200" ]; then
        red "FAIL: cancellation returned HTTP ${CANCEL_STATUS}"
        exit 1
    fi
    green "Cancellation returned 200 OK."

    REOPENED=$(curl -s "${BASE_URL}/api/v1/slots/available" | jq -r --arg sid "$SLOT_ID" '.data[]? | select((.slotId // .id) == ($sid | tonumber)) | (.slotId // .id)')
    if [ -n "$REOPENED" ]; then
        green "PASS: slot ${SLOT_ID} is AVAILABLE again after cancellation."
    else
        red "FAIL: slot ${SLOT_ID} did not reappear as available after cancellation."
        exit 1
    fi
}

# ---------------------------------------------------------------------------
broker_kill_test() {
    info "=== OUTBOX DURABILITY TEST: kill RabbitMQ mid-flow ==="
    info "(uses 'docker compose' - swap to 'docker-compose' below if you're on Compose v1)"

    SLOT_ID=$(get_available_slot_id)
    if [ -z "$SLOT_ID" ]; then
        red "No available slot found."
        exit 1
    fi

    VALID_CLIENT_ID="${CLIENT_ID:-3}"
    info "Stopping RabbitMQ..."
    docker compose stop rabbitmq

    info "Creating a booking while the broker is down (slot ${SLOT_ID})..."
    IDEMPOTENCY_KEY=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || echo "broker-${RANDOM}")
    HTTP_CODE=$(curl -s -o /tmp/broker_kill_response.json -w "%{http_code}" \
        -X POST "${BASE_URL}/api/v1/bookings" \
        -H "Content-Type: application/json" \
        -H "X-User-Id: broker_test_user" \
        -H "X-Idempotency-Key: ${IDEMPOTENCY_KEY}" \
        -d "{\"slotId\":${SLOT_ID},\"clientId\":${VALID_CLIENT_ID},\"idempotencyKey\":\"${IDEMPOTENCY_KEY}\"}")

    if [ "$HTTP_CODE" != "201" ]; then
        red "FAIL: booking did not succeed while broker was down (got HTTP ${HTTP_CODE})."
        docker compose start rabbitmq
        exit 1
    fi
    green "PASS (step 1): booking succeeded with HTTP 201 even though RabbitMQ is down."
    info "The outbox row for this booking should now be PENDING - check with:"
    echo '   docker compose exec postgres psql -U slotify_user -d slotify_db -c "SELECT id, event_type, status, retry_count FROM outbox_messages ORDER BY id DESC LIMIT 5;"'

    info "Waiting 5s, then restarting RabbitMQ..."
    sleep 5
    docker compose start rabbitmq

    info "Waiting for the outbox poller to pick the pending row back up (~5s)..."
    sleep 5
    green "Restart complete. Re-run the psql query above and confirm the row's status flipped PENDING -> PROCESSED."
    info "Also worth checking app logs for a 'Found N PENDING outbox events to publish' line right after the restart."
}
# ---------------------------------------------------------------------------
case "${1:-}" in
    concurrency)  concurrency_test ;;
    cancellation) cancellation_test ;;
    broker-kill)  broker_kill_test ;;
    all)
        concurrency_test
        echo ""
        cancellation_test
        echo ""
        broker_kill_test
        ;;
    *)
        echo "Usage: $0 {concurrency|cancellation|broker-kill|all}"
        exit 1
        ;;
esac