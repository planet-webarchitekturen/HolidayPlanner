#!/usr/bin/env bash
#
# End-to-end demo/test script for User Story 7: Family Member Veto.
#
# Flow:
#   create parent + family member
#   create active event term
#   create booking for the family member
#   try to delete the family member -> expect 409
#   cancel the booking
#   delete the family member -> expect 204
#
# Run:
#   bash scripts/demo-story-7-family-member-veto.sh
#
# Needs:
#   docker compose stack running with rebuilt booking-service and identity-service,
#   plus curl and Python 3.
set -euo pipefail

JWT_SECRET="${JWT_SECRET:-holidayplanner-super-secret-key-that-is-at-least-256-bits-long}"
IDENTITY_URL="${IDENTITY_URL:-http://localhost:8083}"
ORGANIZATION_URL="${ORGANIZATION_URL:-http://localhost:8084}"
EVENT_URL="${EVENT_URL:-http://localhost:8081}"
BOOKING_URL="${BOOKING_URL:-http://localhost:8082}"

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
    "email": "admin@story7-demo.test",
    "roles": ["ADMIN", "EVENT_OWNER", "ORGANIZATION_TEAM_MEMBER"],
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
eligible_birth = (event_start - timedelta(days=(365 * 10) + 3)).date()

sys.stdout.write("|".join([
    event_start.isoformat(timespec="seconds"),
    event_end.isoformat(timespec="seconds"),
    booking_open.isoformat(timespec="seconds"),
    eligible_birth.isoformat(),
]))
PY
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

require_has_active_endpoint() {
  local token="$1"
  local family_member_id="$2"
  local output_file="$TMP_DIR/has-active-preflight.json"
  local code

  code=$(request_status "$output_file" \
    "$BOOKING_URL/api/bookings/family-member/$family_member_id/has-active" \
    -H "Authorization: Bearer $token")

  if [ "$code" != "200" ]; then
    echo "FAIL: booking-service did not serve the Story 7 /has-active endpoint." >&2
    echo "Expected HTTP 200 from:" >&2
    echo "  $BOOKING_URL/api/bookings/family-member/$family_member_id/has-active" >&2
    echo "Got HTTP $code." >&2
    echo >&2
    echo "This usually means Docker is still running the old ghcr.io image." >&2
    echo "Rebuild/restart the changed services, for example:" >&2
    echo "  docker compose up -d --build --pull never booking-service identity-service" >&2
    echo >&2
    echo "Response body:" >&2
    cat "$output_file" >&2
    echo >&2
    exit 1
  fi

  echo "OK: booking-service /has-active endpoint is available"
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
    -d "lastName=StorySeven" \
    -d "birthDate=$birth_date" \
    -d "zip=6900"
}

create_event() {
  local token="$1"
  local organization_id="$2"
  local event_owner_id="$3"
  local title="$4"

  expect_json_field 201 id "create event" \
    -X POST "$EVENT_URL/api/events" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d '{
      "organizationId":"'"$organization_id"'",
      "eventOwnerId":"'"$event_owner_id"'",
      "shortTitle":"'"$title"'",
      "description":"Story 7 demo event",
      "location":"Demo Park",
      "meetingPoint":"Main gate",
      "price":10.00,
      "paymentMethod":"BANK_TRANSFER",
      "minimalAge":8,
      "maximalAge":14,
      "pictureUrl":""
    }'
}

create_active_term() {
  local token="$1"
  local event_id="$2"

  local term_id
  term_id=$(expect_json_field 201 id "create event term" \
    -X POST "$EVENT_URL/api/events/$event_id/terms" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d '{
      "startDateTime":"'"$EVENT_START"'",
      "endDateTime":"'"$EVENT_END"'",
      "minParticipants":1,
      "maxParticipants":5
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
echo "OK: services are reachable"

IFS='|' read -r EVENT_START EVENT_END BOOKING_OPEN ELIGIBLE_BIRTH <<< "$(build_dates)"

ADMIN_TOKEN=$(mint_admin)
RUN_ID=$(date +%s)

echo
echo "=== SETUP ==="
ORG_ID=$(create_organization "$ADMIN_TOKEN" "Story7 Demo $RUN_ID" "$BOOKING_OPEN")
assert_not_empty "$ORG_ID" "organization id"

ORG_ADMIN_TOKEN=$(mint_admin "$ORG_ID")
PARENT_EMAIL="story7-parent-$RUN_ID@example.test"
PARENT_ID=$(register_parent "$ORG_ID" "$PARENT_EMAIL")
assert_not_empty "$PARENT_ID" "parent user id"

PARENT_TOKEN=$(login_parent "$PARENT_EMAIL")
assert_not_empty "$PARENT_TOKEN" "parent JWT"

CHILD_ID=$(create_family_member "$PARENT_TOKEN" "$PARENT_ID" "VetoChild" "$ELIGIBLE_BIRTH")
assert_not_empty "$CHILD_ID" "family member id"

EVENT_ID=$(create_event "$ORG_ADMIN_TOKEN" "$ORG_ID" "$PARENT_ID" "Story 7 Veto Demo $RUN_ID")
assert_not_empty "$EVENT_ID" "event id"

TERM_ID=$(create_active_term "$ORG_ADMIN_TOKEN" "$EVENT_ID")
assert_not_empty "$TERM_ID" "event term id"

echo
echo "=== CREATE ACTIVE BOOKING ==="
BOOKING_BODY="$TMP_DIR/booking.json"
expect_status 200 "$BOOKING_BODY" \
  -X POST "$BOOKING_URL/api/bookings" \
  -H "Authorization: Bearer $PARENT_TOKEN" \
  -d "familyMemberId=$CHILD_ID" \
  -d "eventTermId=$TERM_ID"

BOOKING_ID=$(json_field_from_file id "$BOOKING_BODY")
BOOKING_STATUS=$(json_field_from_file status "$BOOKING_BODY")
assert_not_empty "$BOOKING_ID" "booking id"
assert_equals "$BOOKING_STATUS" "CONFIRMED" "booking status before veto"
require_has_active_endpoint "$PARENT_TOKEN" "$CHILD_ID"

echo
echo "=== VETO: DELETE FAMILY MEMBER WITH ACTIVE BOOKING ==="
VETO_BODY="$TMP_DIR/veto.json"
expect_status 409 "$VETO_BODY" \
  -X DELETE "$IDENTITY_URL/api/identity/family-members/$CHILD_ID" \
  -H "Authorization: Bearer $PARENT_TOKEN"
echo "OK: active booking veto rejected deletion"

echo
echo "=== CANCEL BOOKING ==="
CANCEL_BODY="$TMP_DIR/cancel.json"
expect_status 200 "$CANCEL_BODY" \
  -X DELETE "$BOOKING_URL/api/bookings/$BOOKING_ID" \
  -H "Authorization: Bearer $PARENT_TOKEN"

CANCEL_STATUS=$(json_field_from_file status "$CANCEL_BODY")
assert_equals "$CANCEL_STATUS" "CANCELLED" "booking status after cancellation"

echo
echo "=== DELETE FAMILY MEMBER AFTER CANCELLATION ==="
DELETE_BODY="$TMP_DIR/delete.json"
expect_status 204 "$DELETE_BODY" \
  -X DELETE "$IDENTITY_URL/api/identity/family-members/$CHILD_ID" \
  -H "Authorization: Bearer $PARENT_TOKEN"
echo "OK: family member deleted after active bookings were removed"

echo
echo "=== STORY 7 RESULT ==="
echo "PASS: Family member veto is working end-to-end."
echo
echo "Useful IDs for Swagger:"
echo "  organizationId = $ORG_ID"
echo "  parentEmail    = $PARENT_EMAIL"
echo "  familyMemberId = $CHILD_ID"
echo "  eventTermId    = $TERM_ID"
echo "  bookingId      = $BOOKING_ID"
echo
echo "Swagger:"
echo "  Identity: $IDENTITY_URL/swagger-ui/index.html"
echo "  Booking:  $BOOKING_URL/swagger-ui/index.html"
