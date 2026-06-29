#!/usr/bin/env bash
#
# DEMO: EventService (:8081)
# ---------------------------------------------------------------------------
# Two short use cases that together cover most of what the service does.
# You press Enter before each step; after each step a one-line summary explains
# what just happened.
#
#   Use case 1 — An event owner publishes a holiday activity
#   Use case 2 — Running and closing an event term
#
# Run:   bash scripts/demo-event.sh
# Needs: python3, jq, curl, and event-service running (docker compose up -d).
#        booking-service is also needed for the capacity-increase step.
# ---------------------------------------------------------------------------
set -uo pipefail

JWT_SECRET="${JWT_SECRET:-holidayplanner-super-secret-key-that-is-at-least-256-bits-long}"
EVENT_URL="${EVENT_URL:-http://localhost:8081}"
BOOKING_URL="${BOOKING_URL:-http://localhost:8082}"
RUN="$(date +%s)"

# --- Pretty-printing --------------------------------------------------------
BOLD=$'\033[1m'; DIM=$'\033[2m'; RED=$'\033[31m'; GREEN=$'\033[32m'
YELLOW=$'\033[33m'; BLUE=$'\033[34m'; CYAN=$'\033[36m'; RESET=$'\033[0m'
LINE="${BLUE}───────────────────────────────────────────────────────────────────────${RESET}"

usecase() { printf '\n\n%s%s╔═══ USE CASE %s ═══════════════════════════════════════════════════════╗%s\n%s%s  %s%s\n%s%s╚══════════════════════════════════════════════════════════════════════╝%s\n' \
  "$BOLD" "$CYAN" "$1" "$RESET" "$BOLD" "$CYAN" "$2" "$RESET" "$BOLD" "$CYAN" "$RESET"; }

STEP=0
step() {
  STEP=$((STEP+1))
  printf '\n%s\n%sStep %d — %s%s\n' "$LINE" "$BOLD" "$STEP" "$1" "$RESET"
  [ -n "${2:-}" ] && printf '%s%s%s\n' "$DIM" "$2" "$RESET"
  printf '%s  ▸ press Enter to run…%s' "$DIM" "$RESET"; read -r _
}
happened() { printf '%s✓ %s%s\n' "$GREEN" "$1" "$RESET"; }
warn()     { printf '%s! %s%s\n' "$YELLOW" "$1" "$RESET"; }

# Mint an HS256 JWT.  Usage: mint '["ROLE_A"]' <organizationId|""> [userId]
mint() {
  python3 - "$JWT_SECRET" "$1" "$2" "${3:-}" <<'PY'
import hmac, hashlib, base64, json, time, uuid, sys
secret = sys.argv[1].encode(); roles = json.loads(sys.argv[2]); org = sys.argv[3]
user_id = sys.argv[4] if len(sys.argv) > 4 and sys.argv[4] else str(uuid.uuid4())
b = lambda d: base64.urlsafe_b64encode(d).rstrip(b'=')
header = b(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
payload = {"sub": user_id, "email": "owner@demo.test", "roles": roles,
           "iat": int(time.time()), "exp": int(time.time()) + 14400}
if org:
    payload["organizationId"] = org
body = header + b"." + b(json.dumps(payload).encode())
sig = b(hmac.new(secret, body, hashlib.sha256).digest())
print((body + b"." + sig).decode())
PY
}

uuidgen_() { python3 -c 'import uuid;print(uuid.uuid4())'; }

# API call with optional JSON body.
# Usage: api METHOD URL TOKEN [json-body]
LAST_BODY=""; LAST_CODE=""
api() {
  local method="$1" url="$2" token="$3" body="${4:-}"
  local curl_args=(-s -w $'\n%{http_code}' -X "$method" "$url")
  [ -n "$token" ] && curl_args+=(-H "Authorization: Bearer $token")
  if [ -n "$body" ]; then
    curl_args+=(-H "Content-Type: application/json" -d "$body")
  fi

  printf '   %s%s %s%s\n' "$YELLOW" "$method" "$url" "$RESET"
  [ -n "$body" ] && printf '   %s↳ %s%s\n' "$DIM" "$body" "$RESET"

  local raw; raw="$(curl "${curl_args[@]}")"
  LAST_CODE="${raw##*$'\n'}"; LAST_BODY="${raw%$'\n'*}"
  [ "$LAST_BODY" = "$LAST_CODE" ] && LAST_BODY=""

  local color="$GREEN"; case "$LAST_CODE" in 2*) color="$GREEN";; 4*|5*) color="$RED";; esac
  printf '   %s→ HTTP %s%s\n' "$color" "$LAST_CODE" "$RESET"
  if [ -n "$LAST_BODY" ]; then
    if echo "$LAST_BODY" | jq . >/dev/null 2>&1; then echo "$LAST_BODY" | jq -C . | sed 's/^/     /'
    else printf '     %s\n' "$LAST_BODY"; fi
  fi
}
jqr() { echo "$LAST_BODY" | jq -r "$1" 2>/dev/null | sed 's/^null$//'; }

# ---------------------------------------------------------------------------
for t in python3 jq curl; do command -v "$t" >/dev/null || { echo "Missing tool: $t"; exit 1; }; done

printf '%sHoliday Planner — EventService demo%s\n' "$BOLD" "$RESET"
printf '%sEventService %s%s\n' "$DIM" "$EVENT_URL" "$RESET"

if [ "$(curl -s -o /dev/null -w '%{http_code}' "$EVENT_URL/api/events/health")" != "200" ]; then
  printf '%sEventService not reachable. Start it first: docker compose up -d%s\n' "$RED" "$RESET"; exit 1
fi

ORG_ID="$(uuidgen_)"
OWNER_ID="$(uuidgen_)"
OWNER_TOK="$(mint '["EVENT_OWNER"]' "$ORG_ID" "$OWNER_ID")"
CAREGIVER_ID="$(uuidgen_)"
FAMILY_MEMBER_ID="$(uuidgen_)"

# ===========================================================================
usecase 1 "An event owner publishes a holiday activity  (EventService)"
# ===========================================================================

step "Create the event" \
  "The event owner adds a new activity — title, location, price, age range, and payment method."
api POST "$EVENT_URL/api/events" "$OWNER_TOK" "$(cat <<JSON
{
  "organizationId": "$ORG_ID",
  "eventOwnerId": "$OWNER_ID",
  "shortTitle": "Bicycle Tour $RUN",
  "description": "A guided bike ride through the lakeside.",
  "location": "Bregenz harbour",
  "meetingPoint": "Main gate",
  "price": 15.00,
  "paymentMethod": "BANK_TRANSFER",
  "minimalAge": 6,
  "maximalAge": 16,
  "pictureUrl": ""
}
JSON
)"
EVENT_ID="$(jqr '.id')"
[ -n "$EVENT_ID" ] || { warn "could not create event"; exit 1; }
happened "Event created with id $EVENT_ID."

step "Update the event details" \
  "The owner tweaks the price and meeting point before publishing any dates."
api PUT "$EVENT_URL/api/events/$EVENT_ID" "$OWNER_TOK" "$(cat <<JSON
{
  "shortTitle": "Bicycle Tour $RUN",
  "description": "Updated: helmets provided, bring a water bottle.",
  "location": "Bregenz harbour",
  "meetingPoint": "Ticket office",
  "price": 18.00,
  "paymentMethod": "BANK_TRANSFER",
  "minimalAge": 6,
  "maximalAge": 16,
  "pictureUrl": ""
}
JSON
)"
happened "Event updated (price now 18.00, new meeting point)."

step "Create an event term" \
  "A concrete date is added. New terms always start in DRAFT status."
api POST "$EVENT_URL/api/events/$EVENT_ID/terms" "$OWNER_TOK" "$(cat <<JSON
{
  "startDateTime": "2026-07-15T09:00:00",
  "endDateTime": "2026-07-15T17:00:00",
  "minParticipants": 5,
  "maxParticipants": 20
}
JSON
)"
TERM_ID="$(jqr '.id')"
TERM_STATUS="$(jqr '.status')"
[ -n "$TERM_ID" ] || { warn "could not create event term"; exit 1; }
happened "Event term created with id $TERM_ID (status: $TERM_STATUS)."

step "Read the term and list all terms for the event" \
  "Fetch a single term and the full list attached to this event."
api GET "$EVENT_URL/api/events/terms/$TERM_ID" "$OWNER_TOK"
api GET "$EVENT_URL/api/events/$EVENT_ID/terms" "$OWNER_TOK"
happened "Term details and the event's term list returned."

step "Activate the term" \
  "DRAFT → ACTIVE opens the term for bookings."
api PATCH "$EVENT_URL/api/events/terms/$TERM_ID/status" "$OWNER_TOK" '{"newStatus":"ACTIVE"}'
happened "Term is now ACTIVE — parents can book."

step "List events for the organization" \
  "The public main-page query: all events for one municipality."
api GET "$EVENT_URL/api/events?organizationId=$ORG_ID" "$OWNER_TOK"
happened "Organization event list includes the new Bicycle Tour — publishing flow done."

# ===========================================================================
usecase 2 "Running and closing an event term  (EventService)"
# ===========================================================================

step "Assign a caregiver" \
  "A caregiver is linked to the term so they receive notifications."
api POST "$EVENT_URL/api/events/terms/$TERM_ID/caregivers/$CAREGIVER_ID" "$OWNER_TOK"
happened "Caregiver $CAREGIVER_ID assigned to the term."

step "Add a remark about a participant" \
  "The event owner records a note (e.g. dietary requirement) for one child."
api POST "$EVENT_URL/api/events/terms/$TERM_ID/remarks" "$OWNER_TOK" "$(cat <<JSON
{
  "familyMemberId": "$FAMILY_MEMBER_ID",
  "eventOwnerId": "$OWNER_ID",
  "description": "Vegetarian lunch requested."
}
JSON
)"
happened "Remark saved for family member $FAMILY_MEMBER_ID."

step "List remarks for the term" \
  "Read back all remarks attached to this event term."
api GET "$EVENT_URL/api/events/terms/$TERM_ID/remarks" "$OWNER_TOK"
happened "Remark list returned (includes the dietary note)."

step "Send a message to participants" \
  "Publishes a ParticipantMessageRequested event to Kafka for notification-service."
api POST "$EVENT_URL/api/events/terms/$TERM_ID/messages" "$OWNER_TOK" "$(cat <<JSON
{
  "subject": "What to bring tomorrow",
  "message": "Please bring a water bottle and sun cream."
}
JSON
)"
[ "$LAST_CODE" = "204" ] && happened "Message queued (HTTP 204) — notification-service will email participants." \
  || happened "Message request sent (HTTP $LAST_CODE)."

step "Increase capacity" \
  "More spots are opened; if max grows, a CapacityIncreased event is published (needs booking-service)."
if [ "$(curl -s -o /dev/null -w '%{http_code}' "$BOOKING_URL/api/bookings/health")" = "200" ]; then
  api PATCH "$EVENT_URL/api/events/terms/$TERM_ID/capacity" "$OWNER_TOK" \
    '{"minParticipants":5,"maxParticipants":25}'
  happened "Capacity raised to 25 (was 20) — waitlist promotions may follow via Kafka."
else
  warn "booking-service not reachable — skipping capacity step."
  warn "Start the full stack for this step: docker compose up -d"
fi

step "Business rule check" \
  "An ACTIVE term cannot go back to DRAFT — the service must reject it."
api PATCH "$EVENT_URL/api/events/terms/$TERM_ID/status" "$OWNER_TOK" '{"newStatus":"DRAFT"}'
[ "$LAST_CODE" = "400" ] && happened "Correctly rejected (HTTP 400 — invalid status transition)." \
  || warn "expected HTTP 400, got $LAST_CODE"

step "Cancel the term" \
  "ACTIVE → CANCELLED starts the cancellation saga (EventTermCancelled → booking/payment/notifications)."
api PATCH "$EVENT_URL/api/events/terms/$TERM_ID/status" "$OWNER_TOK" '{"newStatus":"CANCELLED"}'
happened "Term cancelled — saga published EventTermCancelled to Kafka. EventService demo done."

printf '\n%s\n%s✓ Demo complete.%s EventService exercised end-to-end.\n' "$LINE" "$GREEN$BOLD" "$RESET"
printf '%sExplore live: %s/swagger-ui/index.html%s\n' "$DIM" "$EVENT_URL" "$RESET"
