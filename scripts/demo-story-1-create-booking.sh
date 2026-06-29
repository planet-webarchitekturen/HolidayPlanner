#!/usr/bin/env bash
#
# End-to-end demo/test script for User Story 1: Create Booking.
#
# It recreates the full cross-service flow:
#   identity + organization + event setup
#   booking-service creates a CONFIRMED booking
#   booking-service publishes BookingCreated
#   payment-service consumes it and creates a PENDING payment
#
# It also checks the Story 1 guard rails:
#   age validation -> HTTP 400
#   booking window -> HTTP 409
#   duplicate booking -> HTTP 409
#
# Run:
#   bash scripts/demo-story-1-create-booking.sh
#
# Needs:
#   docker compose stack running, plus curl and Python 3.
set -euo pipefail

JWT_SECRET="${JWT_SECRET:-holidayplanner-super-secret-key-that-is-at-least-256-bits-long}"
IDENTITY_URL="${IDENTITY_URL:-http://localhost:8083}"
ORGANIZATION_URL="${ORGANIZATION_URL:-http://localhost:8084}"
EVENT_URL="${EVENT_URL:-http://localhost:8081}"
BOOKING_URL="${BOOKING_URL:-http://localhost:8082}"
PAYMENT_URL="${PAYMENT_URL:-http://localhost:8085}"

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_cmd curl

detect_python() {
  local candidate

  for candidate in python3 python "py -3"; do
    if $candidate - <<'PY' >/dev/null 2>&1
import sys
sys.exit(0 if sys.version_info >= (3, 8) else 1)
PY
    then
      echo "$candidate"
      return 0
    fi
  done

  echo "Missing required command: Python 3.8+ (tried python3, python, py -3)" >&2
  exit 1
}

PYTHON_CMD="${PYTHON_CMD:-$(detect_python)}"

json_field_from_file() {
  local field="$1"
  local file="$2"

  $PYTHON_CMD - "$field" "$file" <<'PY'
import json
import sys

field = sys.argv[1]
path = sys.argv[2]

with open(path, encoding="utf-8") as f:
    data = json.load(f)

value = data
for part in field.split("."):
    if isinstance(value, dict):
        value = value.get(part)
    else:
        value = None
        break

if value is None:
    sys.stdout.write("")
else:
    sys.stdout.write(str(value))
PY
}

json_field() {
  local field="$1"
  local file="$TMP_DIR/stdin-json-$RANDOM.json"

  cat > "$file"
  json_field_from_file "$field" "$file"
}

mint_admin() {
  local organization_id="${1:-}"

  $PYTHON_CMD - "$JWT_SECRET" "$organization_id" <<'PY'
import hmac
import hashlib
import base64
import json
import time
import uuid
import sys

secret = sys.argv[1].encode()
organization_id = sys.argv[2] or str(uuid.uuid4())

def b64(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=")

header = b64(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
payload = b64(json.dumps({
    "sub": str(uuid.uuid4()),
    "organizationId": organization_id,
    "email": "admin@story1-demo.test",
    "roles": ["ADMIN", "EVENT_OWNER", "ACCOUNTANT", "ORGANIZATION_TEAM_MEMBER"],
    "iat": int(time.time()),
    "exp": int(time.time()) + 14400
}).encode())
signature = b64(hmac.new(secret, header + b"." + payload, hashlib.sha256).digest())
sys.stdout.write((header + b"." + payload + b"." + signature).decode())
PY
}

build_dates() {
  $PYTHON_CMD <<'PY'
import sys
from datetime import datetime, timedelta

now = datetime.now()
event_day = now + timedelta(days=45)
event_start = event_day.replace(hour=9, minute=0, second=0, microsecond=0)
event_end = event_day.replace(hour=13, minute=0, second=0, microsecond=0)
booking_open = now - timedelta(days=1)
booking_future = now + timedelta(days=7)
eligible_birth = (event_start - timedelta(days=(365 * 10) + 3)).date()
too_young_birth = (event_start - timedelta(days=365 * 5)).date()

sys.stdout.write("|".join([
    event_start.isoformat(timespec="seconds"),
    event_end.isoformat(timespec="seconds"),
    booking_open.isoformat(timespec="seconds"),
    booking_future.isoformat(timespec="seconds"),
    eligible_birth.isoformat(),
    too_young_birth.isoformat(),
]))
PY
}

assert_equals() {
  local actual="$1"
  local expected="$2"
  local message="$3"

  if [ "$actual" != "$expected" ]; then
    echo "FAIL: $message" >&2
    echo "  expected: $expected" >&2
    echo "  actual:   $actual" >&2
    exit 1
  fi

  echo "OK: $message -> $actual"
}

assert_not_empty() {
  local value="$1"
  local message="$2"

  if [ -z "$value" ] || [ "$value" = "null" ]; then
    echo "FAIL: $message is empty" >&2
    exit 1
  fi

  echo "OK: $message -> $value"
}

request_status() {
  local output_file="$1"
  shift

  curl -sS -o "$output_file" -w "%{http_code}" "$@" || true
}

expect_status() {
  local expected="$1"
  local output_file="$2"
  shift 2

  local code
  code=$(request_status "$output_file" "$@")
  if [ "$code" != "$expected" ]; then
    echo "FAIL: expected HTTP $expected but got HTTP $code" >&2
    echo "Response body:" >&2
    cat "$output_file" >&2
    echo >&2
    exit 1
  fi

  echo "OK: HTTP $expected"
}

expect_json_field() {
  local expected="$1"
  local field="$2"
  local label="$3"
  shift 3

  local output_file="$TMP_DIR/response-$RANDOM.json"
  local code
  code=$(request_status "$output_file" "$@")

  if [ "$code" != "$expected" ]; then
    echo "FAIL: $label returned HTTP $code, expected HTTP $expected" >&2
    echo "Response body:" >&2
    cat "$output_file" >&2
    echo >&2
    exit 1
  fi

  local value
  value=$(json_field_from_file "$field" "$output_file")

  if [ -z "$value" ] || [ "$value" = "null" ]; then
    echo "FAIL: $label response did not contain field '$field'" >&2
    echo "Response body:" >&2
    cat "$output_file" >&2
    echo >&2
    exit 1
  fi

  echo "$value"
}

wait_for_payment() {
  local booking_id="$1"
  local token="$2"
  local output_file="$3"

  for _ in $(seq 1 30); do
    local code
    code=$(request_status "$output_file" \
      "$PAYMENT_URL/api/payments/booking/$booking_id" \
      -H "Authorization: Bearer $token")

    if [ "$code" = "200" ]; then
      return 0
    fi

    sleep 1
  done

  echo "FAIL: payment was not created for booking $booking_id within 30 seconds" >&2
  echo "Last response:" >&2
  cat "$output_file" >&2
  echo >&2
  exit 1
}

create_organization() {
  local token="$1"
  local name="$2"
  local booking_start_time="$3"

  expect_json_field 200 id "create organization" \
    -X POST "$ORGANIZATION_URL/api/organizations" \
    -H "Authorization: Bearer $token" \
    -d "name=$name" \
    -d "bankAccount=AT611904300234573201" \
    -d "bookingStartTime=$booking_start_time"
}

register_parent() {
  local organization_id="$1"
  local email="$2"

  expect_json_field 201 id "register parent" \
    -X POST "$IDENTITY_URL/api/auth/register" \
    -d "email=$email" \
    -d "password=Password123!" \
    -d "phoneNumber=+430000" \
    -d "organizationId=$organization_id"
}

login_parent() {
  local email="$1"

  expect_json_field 200 token "login parent" \
    -X POST "$IDENTITY_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"Password123!\"}"
}

create_family_member() {
  local token="$1"
  local user_id="$2"
  local first_name="$3"
  local birth_date="$4"

  expect_json_field 200 id "create family member" \
    -X POST "$IDENTITY_URL/api/identity/users/$user_id/family-members" \
    -H "Authorization: Bearer $token" \
    -d "firstName=$first_name" \
    -d "lastName=StoryOne" \
    -d "birthDate=$birth_date" \
    -d "zip=6900"
}

create_event() {
  local token="$1"
  local organization_id="$2"
  local event_owner_id="$3"
  local title="$4"
  local minimal_age="$5"
  local maximal_age="$6"

  expect_json_field 201 id "create event" \
    -X POST "$EVENT_URL/api/events" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d '{
      "organizationId":"'"$organization_id"'",
      "eventOwnerId":"'"$event_owner_id"'",
      "shortTitle":"'"$title"'",
      "description":"Story 1 demo event",
      "location":"Demo Park",
      "meetingPoint":"Main gate",
      "price":25.00,
      "paymentMethod":"BANK_TRANSFER",
      "minimalAge":'"$minimal_age"',
      "maximalAge":'"$maximal_age"',
      "pictureUrl":""
    }'
}

create_active_term() {
  local token="$1"
  local event_id="$2"
  local max_participants="$3"

  local term_id
  term_id=$(expect_json_field 201 id "create event term" \
    -X POST "$EVENT_URL/api/events/$event_id/terms" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d '{
      "startDateTime":"'"$EVENT_START"'",
      "endDateTime":"'"$EVENT_END"'",
      "minParticipants":1,
      "maxParticipants":'"$max_participants"'
    }')

  local status_file="$TMP_DIR/activate-term-$RANDOM.json"
  expect_status 200 "$status_file" \
    -X PATCH "$EVENT_URL/api/events/terms/$term_id/status" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d '{"newStatus":"ACTIVE"}' >/dev/null

  echo "$term_id"
}

echo "Checking service health..."
curl -fsS "$IDENTITY_URL/api/identity/health" >/dev/null
curl -fsS "$ORGANIZATION_URL/api/organizations/health" >/dev/null
curl -fsS "$EVENT_URL/api/events/health" >/dev/null
curl -fsS "$BOOKING_URL/api/bookings/health" >/dev/null
curl -fsS "$PAYMENT_URL/api/payments/health" >/dev/null
echo "OK: services are reachable"

IFS='|' read -r EVENT_START EVENT_END BOOKING_OPEN BOOKING_FUTURE ELIGIBLE_BIRTH TOO_YOUNG_BIRTH <<< "$(build_dates)"

ADMIN_TOKEN=$(mint_admin)
RUN_ID=$(date +%s)

echo
echo "=== SETUP ==="
ORG_ID=$(create_organization "$ADMIN_TOKEN" "Story1 Demo $RUN_ID" "$BOOKING_OPEN")
assert_not_empty "$ORG_ID" "organization id"

PARENT_EMAIL="story1-parent-$RUN_ID@example.test"
PARENT_ID=$(register_parent "$ORG_ID" "$PARENT_EMAIL")
assert_not_empty "$PARENT_ID" "parent user id"

PARENT_TOKEN=$(login_parent "$PARENT_EMAIL")
assert_not_empty "$PARENT_TOKEN" "parent JWT"

CHILD_ID=$(create_family_member "$PARENT_TOKEN" "$PARENT_ID" "EligibleChild" "$ELIGIBLE_BIRTH")
assert_not_empty "$CHILD_ID" "eligible child id"

YOUNG_CHILD_ID=$(create_family_member "$PARENT_TOKEN" "$PARENT_ID" "TooYoungChild" "$TOO_YOUNG_BIRTH")
assert_not_empty "$YOUNG_CHILD_ID" "too-young child id"

ORG_ADMIN_TOKEN=$(mint_admin "$ORG_ID")
EVENT_ID=$(create_event "$ORG_ADMIN_TOKEN" "$ORG_ID" "$PARENT_ID" "Story 1 Booking Demo $RUN_ID" 8 14)
assert_not_empty "$EVENT_ID" "event id"

TERM_ID=$(create_active_term "$ORG_ADMIN_TOKEN" "$EVENT_ID" 2)
assert_not_empty "$TERM_ID" "event term id"

echo
echo "=== HAPPY PATH: create booking ==="
BOOKING_BODY="$TMP_DIR/booking.json"
expect_status 200 "$BOOKING_BODY" \
  -X POST "$BOOKING_URL/api/bookings" \
  -H "Authorization: Bearer $PARENT_TOKEN" \
  -d "familyMemberId=$CHILD_ID" \
  -d "eventTermId=$TERM_ID"

BOOKING_ID=$(json_field_from_file id "$BOOKING_BODY")
BOOKING_STATUS=$(json_field_from_file status "$BOOKING_BODY")
assert_not_empty "$BOOKING_ID" "booking id"
assert_equals "$BOOKING_STATUS" "CONFIRMED" "booking status"

echo
echo "=== ASYNC CHECK: payment created from BookingCreated ==="
PAYMENT_BODY="$TMP_DIR/payment.json"
wait_for_payment "$BOOKING_ID" "$PARENT_TOKEN" "$PAYMENT_BODY"

PAYMENT_STATUS=$(json_field_from_file status "$PAYMENT_BODY")
PAYMENT_PARENT_EMAIL=$(json_field_from_file parentEmail "$PAYMENT_BODY")
PAYMENT_EVENT_NAME=$(json_field_from_file eventName "$PAYMENT_BODY")
assert_equals "$PAYMENT_STATUS" "PENDING" "payment status"
assert_equals "$PAYMENT_PARENT_EMAIL" "$PARENT_EMAIL" "payment parentEmail"
assert_not_empty "$PAYMENT_EVENT_NAME" "payment eventName"

echo
echo "=== ACCEPTANCE: duplicate booking guard ==="
DUPLICATE_BODY="$TMP_DIR/duplicate.json"
expect_status 409 "$DUPLICATE_BODY" \
  -X POST "$BOOKING_URL/api/bookings" \
  -H "Authorization: Bearer $PARENT_TOKEN" \
  -d "familyMemberId=$CHILD_ID" \
  -d "eventTermId=$TERM_ID"
DUPLICATE_MESSAGE=$(json_field_from_file message "$DUPLICATE_BODY")
echo "OK: duplicate rejected -> $DUPLICATE_MESSAGE"

echo
echo "=== ACCEPTANCE: age guard ==="
AGE_BODY="$TMP_DIR/age.json"
expect_status 400 "$AGE_BODY" \
  -X POST "$BOOKING_URL/api/bookings" \
  -H "Authorization: Bearer $PARENT_TOKEN" \
  -d "familyMemberId=$YOUNG_CHILD_ID" \
  -d "eventTermId=$TERM_ID"
AGE_MESSAGE=$(json_field_from_file message "$AGE_BODY")
echo "OK: too-young child rejected -> $AGE_MESSAGE"

echo
echo "=== ACCEPTANCE: booking window guard ==="
FUTURE_ORG_ID=$(create_organization "$ADMIN_TOKEN" "Story1 Future Window $RUN_ID" "$BOOKING_FUTURE")
assert_not_empty "$FUTURE_ORG_ID" "future-window organization id"

FUTURE_EMAIL="story1-future-$RUN_ID@example.test"
FUTURE_PARENT_ID=$(register_parent "$FUTURE_ORG_ID" "$FUTURE_EMAIL")
assert_not_empty "$FUTURE_PARENT_ID" "future-window parent user id"

FUTURE_PARENT_TOKEN=$(login_parent "$FUTURE_EMAIL")
assert_not_empty "$FUTURE_PARENT_TOKEN" "future-window parent JWT"

FUTURE_CHILD_ID=$(create_family_member "$FUTURE_PARENT_TOKEN" "$FUTURE_PARENT_ID" "FutureWindowChild" "$ELIGIBLE_BIRTH")
assert_not_empty "$FUTURE_CHILD_ID" "future-window child id"

FUTURE_ADMIN_TOKEN=$(mint_admin "$FUTURE_ORG_ID")
FUTURE_EVENT_ID=$(create_event "$FUTURE_ADMIN_TOKEN" "$FUTURE_ORG_ID" "$FUTURE_PARENT_ID" "Story 1 Future Window $RUN_ID" 8 14)
assert_not_empty "$FUTURE_EVENT_ID" "future-window event id"

FUTURE_TERM_ID=$(create_active_term "$FUTURE_ADMIN_TOKEN" "$FUTURE_EVENT_ID" 2)
assert_not_empty "$FUTURE_TERM_ID" "future-window event term id"

WINDOW_BODY="$TMP_DIR/window.json"
expect_status 409 "$WINDOW_BODY" \
  -X POST "$BOOKING_URL/api/bookings" \
  -H "Authorization: Bearer $FUTURE_PARENT_TOKEN" \
  -d "familyMemberId=$FUTURE_CHILD_ID" \
  -d "eventTermId=$FUTURE_TERM_ID"
WINDOW_MESSAGE=$(json_field_from_file message "$WINDOW_BODY")
echo "OK: future booking window rejected -> $WINDOW_MESSAGE"

echo
echo "=== STORY 1 RESULT ==="
echo "PASS: Create Booking flow is working end-to-end."
echo
echo "Useful IDs for Swagger:"
echo "  organizationId = $ORG_ID"
echo "  parentEmail    = $PARENT_EMAIL"
echo "  familyMemberId = $CHILD_ID"
echo "  eventId        = $EVENT_ID"
echo "  eventTermId    = $TERM_ID"
echo "  bookingId      = $BOOKING_ID"
echo
echo "Swagger:"
echo "  Booking:  $BOOKING_URL/swagger-ui/index.html"
echo "  Payment:  $PAYMENT_URL/swagger-ui/index.html"
echo "  Kafka UI: http://localhost:5001"
