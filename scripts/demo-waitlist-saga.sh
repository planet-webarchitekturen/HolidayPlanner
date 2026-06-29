#!/usr/bin/env bash
#
# DEMO: Waitlist lifecycle & event-term cancellation saga
# ---------------------------------------------------------------------------
# Cross-service walkthrough aligned with the team/service split in README.md.
# Each use case is owned by the team that built the involved service(s).
#
#   UC 1 — Organization setup              (OrganizationService)
#   UC 2 — Parent & children register      (IdentityService)
#   UC 3 — Event term with capacity 1      (EventService — Amir)
#   UC 4 — Two bookings → waitlist         (BookingService — Muhammed)
#   UC 5 — Capacity increase → promotion   (EventService + BookingService — Tarik)
#   UC 6 — Collect event fees              (PaymentService — Aleksander)
#   UC 7 — Term cancellation & refunds     (EventService Samir + Booking + Payment)
#
# Run:   bash scripts/demo-waitlist-saga.sh
# Needs: python3, jq, curl, full stack (docker compose up -d).
#        Keep Kafka UI open: http://localhost:5001
# ---------------------------------------------------------------------------
set -uo pipefail

JWT_SECRET="${JWT_SECRET:-holidayplanner-super-secret-key-that-is-at-least-256-bits-long}"
ORG_URL="${ORG_URL:-http://localhost:8084}"
ID_URL="${ID_URL:-http://localhost:8083}"
EVENT_URL="${EVENT_URL:-http://localhost:8081}"
BOOKING_URL="${BOOKING_URL:-http://localhost:8082}"
PAY_URL="${PAY_URL:-http://localhost:8085}"
KAFKA_UI="${KAFKA_UI:-http://localhost:5001}"
RUN="$(date +%s)"

# --- Pretty-printing --------------------------------------------------------
BOLD=$'\033[1m'; DIM=$'\033[2m'; RED=$'\033[31m'; GREEN=$'\033[32m'
YELLOW=$'\033[33m'; BLUE=$'\033[34m'; CYAN=$'\033[36m'; MAGENTA=$'\033[35m'; RESET=$'\033[0m'
LINE="${BLUE}───────────────────────────────────────────────────────────────────────${RESET}"

team() {  # SERVICE : member1 ; member2
  local svc="$1" m1="$2" m2="${3:-}"
  printf '\n%s%s┏━━ TEAM: %-20s ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓%s\n' \
    "$BOLD" "$MAGENTA" "$svc" "$RESET"
  printf '%s%s┃  %-68s┃%s\n' "$BOLD" "$MAGENTA" "$m1" "$RESET"
  [ -n "$m2" ] && printf '%s%s┃  %-68s┃%s\n' "$BOLD" "$MAGENTA" "$m2" "$RESET"
  printf '%s%s┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛%s\n' \
    "$BOLD" "$MAGENTA" "$RESET"
}

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

kafka_watch() {
  printf '\n%s%s  Kafka UI → %s%s\n' "$BOLD" "$YELLOW" "$KAFKA_UI" "$RESET"
  printf '%s  Topics to watch:%s\n' "$DIM" "$RESET"
  for t in "$@"; do printf '    • %s%s%s\n' "$CYAN" "$t" "$RESET"; done
}

mint() {
  python3 - "$JWT_SECRET" "$1" "$2" "${3:-}" <<'PY'
import hmac, hashlib, base64, json, time, uuid, sys
secret = sys.argv[1].encode(); roles = json.loads(sys.argv[2]); org = sys.argv[3]
user_id = sys.argv[4] if len(sys.argv) > 4 and sys.argv[4] else str(uuid.uuid4())
b = lambda d: base64.urlsafe_b64encode(d).rstrip(b'=')
header = b(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
payload = {"sub": user_id, "email": "demo@demo.test", "roles": roles,
           "iat": int(time.time()), "exp": int(time.time()) + 14400}
if org:
    payload["organizationId"] = org
body = header + b"." + b(json.dumps(payload).encode())
sig = b(hmac.new(secret, body, hashlib.sha256).digest())
print((body + b"." + sig).decode())
PY
}

urlenc() { python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1],safe=""))' "$1"; }
uuidgen_() { python3 -c 'import uuid;print(uuid.uuid4())'; }

LAST_BODY=""; LAST_CODE=""
api() {  # METHOD URL TOKEN [json-body]
  local method="$1" url="$2" token="$3" body="${4:-}"
  local curl_args=(-s -w $'\n%{http_code}' -X "$method" "$url")
  [ -n "$token" ] && curl_args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && curl_args+=(-H "Content-Type: application/json" -d "$body")

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

api_form() {  # METHOD URL TOKEN [key=value ...]
  local method="$1" url="$2" token="$3"; shift 3
  local d k v query="" shown=""
  for d in "$@"; do
    k="${d%%=*}"; v="${d#*=}"
    query+="${query:+&}${k}=$(urlenc "$v")"
    shown+="${shown:+&}$d"
  done
  local full="$url"; [ -n "$query" ] && full="$url?$query"
  local curl_args=(-s -w $'\n%{http_code}' -X "$method" "$full")
  [ -n "$token" ] && curl_args+=(-H "Authorization: Bearer $token")

  printf '   %s%s %s%s\n' "$YELLOW" "$method" "$url" "$RESET"
  [ -n "$shown" ] && printf '   %s↳ %s%s\n' "$DIM" "$shown" "$RESET"

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

# Poll GET until jq expression matches expected value (max ~30s).
wait_for() {
  local label="$1" url="$2" token="$3" jq_expr="$4" expected="$5"
  local i=0 val=""
  while [ "$i" -lt 30 ]; do
    api GET "$url" "$token"
    val="$(jqr "$jq_expr")"
    [ "$val" = "$expected" ] && { happened "$label → $expected"; return 0; }
    sleep 1; i=$((i+1))
  done
  warn "$label — timed out (last value: ${val:-<empty>}, expected: $expected)"
  return 1
}

# ---------------------------------------------------------------------------
for t in python3 jq curl; do command -v "$t" >/dev/null || { echo "Missing tool: $t"; exit 1; }; done

printf '%sHoliday Planner — Waitlist & Cancellation Saga demo%s\n' "$BOLD" "$RESET"
printf '%sCross-service flow · team ownership from README.md%s\n' "$DIM" "$RESET"
printf '%sKafka UI: %s%s\n' "$DIM" "$KAFKA_UI" "$RESET"

for entry in \
  "OrganizationService|$ORG_URL/api/organizations/health" \
  "IdentityService|$ID_URL/api/identity/health" \
  "EventService|$EVENT_URL/api/events/health" \
  "BookingService|$BOOKING_URL/api/bookings/health" \
  "PaymentService|$PAY_URL/api/payments/health"; do
  name="${entry%%|*}"; url="${entry#*|}"
  if [ "$(curl -s -o /dev/null -w '%{http_code}' "$url")" != "200" ]; then
    printf '%s%s not reachable. Start the stack: docker compose up -d%s\n' "$RED" "$name" "$RESET"
    exit 1
  fi
done
happened "All required services are healthy."

ADMIN_TOK="$(mint '["ADMIN","EVENT_OWNER"]' "")"

# ===========================================================================
team "OrganizationService" \
  "Jan Burtscher — organizations, team members, booking start time" \
  "Aleksander Lukic — sponsors, org lifecycle"
usecase 1 "A municipality opens bookings  (OrganizationService)"
# ===========================================================================

step "Create the organization" \
  "BookingService checks bookingStartTime before accepting bookings — set it in the past."
api_form POST "$ORG_URL/api/organizations" "$ADMIN_TOK" \
  "name=Gemeinde Waitlist Demo $RUN" \
  "bankAccount=AT611904300234573201" \
  "bookingStartTime=2020-01-01T08:00:00"
ORG_ID="$(jqr '.id')"
[ -n "$ORG_ID" ] || { warn "could not create organization"; exit 1; }
OWNER_ID="$(uuidgen_)"
OWNER_TOK="$(mint '["EVENT_OWNER"]' "$ORG_ID" "$OWNER_ID")"
ACC_TOK="$(mint '["ACCOUNTANT"]' "$ORG_ID")"
happened "Organization $ORG_ID created — bookings are open."

# ===========================================================================
team "IdentityService" \
  "Büsra Aydemir — registration, authentication, JWT" \
  "Denise Müller — family members, authorization"
usecase 2 "A parent registers two children  (IdentityService)"
# ===========================================================================

PARENT_EMAIL="parent-$RUN@demo.test"
step "Register and log in the parent" \
  "A parent account is required before any child can be booked."
api_form POST "$ID_URL/api/auth/register" "" \
  "email=$PARENT_EMAIL" "password=Password123!" "phoneNumber=+43664123456" \
  "organizationId=$ORG_ID"
PARENT_ID="$(jqr '.id')"
api POST "$ID_URL/api/auth/login" "" "$(cat <<JSON
{"email":"$PARENT_EMAIL","password":"Password123!"}
JSON
)"
PARENT_TOK="$(jqr '.token')"
[ -n "$PARENT_TOK" ] || { warn "login failed"; exit 1; }
happened "Parent $PARENT_ID registered and logged in."

step "Add Anna and Ben as family members" \
  "Two children — we will try to book both onto a term that only has one seat."
api_form POST "$ID_URL/api/identity/users/$PARENT_ID/family-members" "$PARENT_TOK" \
  "firstName=Anna" "lastName=Demo" "birthDate=2016-05-01" "zip=6900"
ANNA_ID="$(jqr '.id')"
api_form POST "$ID_URL/api/identity/users/$PARENT_ID/family-members" "$PARENT_TOK" \
  "firstName=Ben" "lastName=Demo" "birthDate=2017-08-15" "zip=6900"
BEN_ID="$(jqr '.id')"
happened "Family members Anna ($ANNA_ID) and Ben ($BEN_ID) ready."

# ===========================================================================
team "EventService" \
  "Amir Hodzic — events, event terms, status lifecycle, capacity" \
  "Samir Hodzic — remarks, caregivers, cancellation saga"
usecase 3 "One seat only: event term with maxParticipants = 1  (EventService — Amir)"
# ===========================================================================

step "Create the event" \
  "The event owner publishes a holiday activity for the municipality."
api POST "$EVENT_URL/api/events" "$OWNER_TOK" "$(cat <<JSON
{
  "organizationId": "$ORG_ID",
  "eventOwnerId": "$OWNER_ID",
  "shortTitle": "Kayak Tour $RUN",
  "description": "Introductory kayak session on Lake Constance.",
  "location": "Bregenz harbour",
  "meetingPoint": "Boat ramp",
  "price": 20.00,
  "paymentMethod": "BANK_TRANSFER",
  "minimalAge": 8,
  "maximalAge": 16,
  "pictureUrl": ""
}
JSON
)"
EVENT_ID="$(jqr '.id')"
[ -n "$EVENT_ID" ] || { warn "could not create event"; exit 1; }
happened "Event created: $EVENT_ID."

step "Create an event term with maxParticipants = 1" \
  "Only one child can be CONFIRMED; any further booking enters the waiting list."
api POST "$EVENT_URL/api/events/$EVENT_ID/terms" "$OWNER_TOK" "$(cat <<JSON
{
  "startDateTime": "2026-08-10T09:00:00",
  "endDateTime": "2026-08-10T15:00:00",
  "minParticipants": 1,
  "maxParticipants": 1
}
JSON
)"
TERM_ID="$(jqr '.id')"
happened "Event term $TERM_ID created (status: $(jqr '.status'))."

step "Activate the term" \
  "DRAFT → ACTIVE opens the term for parent bookings."
api PATCH "$EVENT_URL/api/events/terms/$TERM_ID/status" "$OWNER_TOK" '{"newStatus":"ACTIVE"}'
happened "Term is ACTIVE — parents can book now."

# ===========================================================================
team "BookingService" \
  "Muhammed Güzel — booking creation, waiting list, API composition" \
  "Tarik Pasalic — cancellations, capacity-increase promotion"
usecase 4 "Two bookings on a one-seat term  (BookingService — Muhammed)"
# ===========================================================================

step "Anna books first → CONFIRMED" \
  "The only available seat is taken. BookingService publishes booking.created (CONFIRMED) to Kafka."
api_form POST "$BOOKING_URL/api/bookings" "$PARENT_TOK" \
  "familyMemberId=$ANNA_ID" "eventTermId=$TERM_ID"
ANNA_BOOKING="$(jqr '.id')"
ANNA_STATUS="$(jqr '.status')"
happened "Anna's booking $ANNA_BOOKING is $ANNA_STATUS."
kafka_watch "holiday-planner.booking.created"

step "Ben books second → WAITLISTED" \
  "Capacity is full. Ben joins the FIFO waiting list — no payment is opened yet."
api_form POST "$BOOKING_URL/api/bookings" "$PARENT_TOK" \
  "familyMemberId=$BEN_ID" "eventTermId=$TERM_ID"
BEN_BOOKING="$(jqr '.id')"
BEN_STATUS="$(jqr '.status')"
happened "Ben's booking $BEN_BOOKING is $BEN_STATUS (waiting list)."

step "List all bookings for the term" \
  "One CONFIRMED, one WAITLISTED — the state we need before raising capacity."
api GET "$BOOKING_URL/api/bookings/event-term/$TERM_ID" "$PARENT_TOK"
happened "Term snapshot: Anna CONFIRMED, Ben WAITLISTED."

# ===========================================================================
team "EventService + BookingService" \
  "Amir Hodzic — capacity PATCH, CapacityIncreased event" \
  "Tarik Pasalic — CapacityIncreasedConsumer, FIFO promotion"
usecase 5 "Raise capacity → Kafka promotes Ben  (EventService + BookingService — Tarik)"
# ===========================================================================

step "Show Ben is still WAITLISTED" \
  "Baseline before the trigger — nothing has changed yet."
api GET "$BOOKING_URL/api/bookings/$BEN_BOOKING" "$PARENT_TOK"
happened "Ben is WAITLISTED — ready for promotion."

step "Increase maxParticipants from 1 → 2" \
  "EventService publishes holiday-planner.event.capacity-increased. BookingService consumes it and promotes the oldest waitlisted child (FIFO)."
api PATCH "$EVENT_URL/api/events/terms/$TERM_ID/capacity" "$OWNER_TOK" \
  '{"minParticipants":1,"maxParticipants":2}'
happened "Capacity raised — Kafka event published."
kafka_watch \
  "holiday-planner.event.capacity-increased" \
  "holiday-planner.booking.waitlist-promoted"

step "Wait for Ben to become CONFIRMED" \
  "BookingService promoted Ben automatically — we never PATCHed his booking directly."
wait_for "Ben's booking" "$BOOKING_URL/api/bookings/$BEN_BOOKING" "$PARENT_TOK" '.status' 'CONFIRMED'
api GET "$BOOKING_URL/api/bookings/event-term/$TERM_ID" "$PARENT_TOK"
happened "Ben promoted via Kafka — both children are now CONFIRMED."

# ===========================================================================
team "PaymentService" \
  "Aleksander Lukic — payments, refunds, balance sheet" \
  "Jan Burtscher — organization-scoped payment operations"
usecase 6 "Event fees collected before cancellation  (PaymentService — Aleksander)"
# ===========================================================================

step "Wait for Anna's payment (auto-created on CONFIRMED booking)" \
  "PaymentService consumes booking.created and opens a PENDING payment for confirmed bookings only."
wait_for "Anna's payment" "$PAY_URL/api/payments/booking/$ANNA_BOOKING" "$ACC_TOK" '.status' 'PENDING'
ANNA_PAYMENT="$(jqr '.id')"
happened "Payment $ANNA_PAYMENT opened for Anna (PENDING)."

step "Open Ben's payment manually" \
  "Waitlist promotion does not re-emit booking.created — the accountant opens Ben's payment so both seats have a fee on record."
api_form POST "$PAY_URL/api/payments" "$ACC_TOK" \
  "bookingId=$BEN_BOOKING" "organizationId=$ORG_ID" "amount=20.00" \
  "parentEmail=$PARENT_EMAIL" "eventName=Kayak Tour $RUN"
BEN_PAYMENT="$(jqr '.id')"
happened "Payment $BEN_PAYMENT opened for Ben (PENDING)."

step "Mark both payments as PAID" \
  "Parents transferred the fees. PAID payments will be refunded when the term is cancelled."
api_form PATCH "$PAY_URL/api/payments/$ANNA_PAYMENT/pay" "$ACC_TOK" "note=Bank transfer ref #A-$RUN"
api_form PATCH "$PAY_URL/api/payments/$BEN_PAYMENT/pay" "$ACC_TOK" "note=Bank transfer ref #B-$RUN"
happened "Both payments are PAID."

step "Check organization balance" \
  "Balance = sum of all PAID payments for this municipality."
api GET "$PAY_URL/api/payments/organization/$ORG_ID/balance" "$ACC_TOK"
happened "Balance reflects both collected fees (40.00)."

# ===========================================================================
team "EventService + BookingService + PaymentService" \
  "Samir Hodzic — EventTermCancellationSaga, term-cancelled event" \
  "Tarik Pasalic — cancelAllBookings, booking.cancelled per booking" \
  "Aleksander Lukic — auto-refund on booking.cancelled"
usecase 7 "Cancel the term → bookings cancelled & payments refunded  (cancellation saga)"
# ===========================================================================

step "Cancel the event term" \
  "ACTIVE → CANCELLED. EventTermCancellationSaga publishes holiday-planner.event.term-cancelled."
api PATCH "$EVENT_URL/api/events/terms/$TERM_ID/status" "$OWNER_TOK" '{"newStatus":"CANCELLED"}'
happened "Term cancelled — saga started."
kafka_watch \
  "holiday-planner.event.term-cancelled" \
  "holiday-planner.booking.cancelled" \
  "holiday-planner.payment.refunded"

step "Wait for all bookings to be CANCELLED" \
  "BookingService consumes term-cancelled and cancels every booking for the term."
wait_for "Anna's booking" "$BOOKING_URL/api/bookings/$ANNA_BOOKING" "$PARENT_TOK" '.status' 'CANCELLED'
wait_for "Ben's booking" "$BOOKING_URL/api/bookings/$BEN_BOOKING" "$PARENT_TOK" '.status' 'CANCELLED'
api GET "$BOOKING_URL/api/bookings/event-term/$TERM_ID" "$PARENT_TOK"
happened "Both bookings are CANCELLED."

step "Verify payments were refunded" \
  "PaymentService consumes booking.cancelled and refunds PAID payments → publishes payment.refunded."
wait_for "Anna's payment" "$PAY_URL/api/payments/booking/$ANNA_BOOKING" "$ACC_TOK" '.status' 'REFUNDED'
wait_for "Ben's payment" "$PAY_URL/api/payments/booking/$BEN_BOOKING" "$ACC_TOK" '.status' 'REFUNDED'
api GET "$PAY_URL/api/payments/booking/$ANNA_BOOKING" "$ACC_TOK"
api GET "$PAY_URL/api/payments/booking/$BEN_BOOKING" "$ACC_TOK"
happened "Both payments moved PAID → REFUNDED."

step "Balance is zero after refunds" \
  "Refunded money no longer counts towards the organization balance."
api GET "$PAY_URL/api/payments/organization/$ORG_ID/balance" "$ACC_TOK"
happened "Balance is 0.00 — cancellation saga complete."

printf '\n%s\n%s✓ Demo complete.%s Full waitlist → promotion → cancellation saga exercised.\n' \
  "$LINE" "$GREEN$BOLD" "$RESET"
printf '%s\n%sTeam coverage:%s\n' "$LINE" "$BOLD" "$RESET"
printf '  OrganizationService  → UC 1\n'
printf '  IdentityService      → UC 2\n'
printf '  EventService         → UC 3, 5 (capacity), 7 (cancellation saga)\n'
printf '  BookingService       → UC 4, 5 (promotion), 7 (mass cancel)\n'
printf '  PaymentService       → UC 6, 7 (auto-refund)\n'
printf '  NotificationService  → consumes Kafka events (watch emails in service logs)\n'
printf '\n%sExplore:%s  Event %s/swagger-ui  ·  Booking %s/swagger-ui  ·  Kafka %s%s\n' \
  "$DIM" "$RESET" "$EVENT_URL" "$BOOKING_URL" "$KAFKA_UI" "$RESET"
