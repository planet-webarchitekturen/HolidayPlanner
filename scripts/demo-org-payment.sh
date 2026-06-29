#!/usr/bin/env bash
#
# DEMO: OrganizationService (:8084) + PaymentService (:8085)
# ---------------------------------------------------------------------------
# Two short use cases that together cover most of what both services do.
# You press Enter before each step; after each step a one-line summary explains
# what just happened.
#
#   Use case 1 — A new municipality joins Holiday Planner   (OrganizationService)
#   Use case 2 — Collecting and refunding event fees        (PaymentService)
#
# Run:   bash scripts/demo-org-payment.sh
# Needs: python3, jq, curl, and organization-service + payment-service running
#        (docker compose up -d).
# ---------------------------------------------------------------------------
set -uo pipefail

JWT_SECRET="${JWT_SECRET:-holidayplanner-super-secret-key-that-is-at-least-256-bits-long}"
ORG_URL="${ORG_URL:-http://localhost:8084}"
PAY_URL="${PAY_URL:-http://localhost:8085}"
RUN="$(date +%s)"

# --- Pretty-printing --------------------------------------------------------
BOLD=$'\033[1m'; DIM=$'\033[2m'; RED=$'\033[31m'; GREEN=$'\033[32m'
YELLOW=$'\033[33m'; BLUE=$'\033[34m'; CYAN=$'\033[36m'; RESET=$'\033[0m'
LINE="${BLUE}───────────────────────────────────────────────────────────────────────${RESET}"

usecase() { printf '\n\n%s%s╔═══ USE CASE %s ═══════════════════════════════════════════════════════╗%s\n%s%s  %s%s\n%s%s╚══════════════════════════════════════════════════════════════════════╝%s\n' \
  "$BOLD" "$CYAN" "$1" "$RESET" "$BOLD" "$CYAN" "$2" "$RESET" "$BOLD" "$CYAN" "$RESET"; }

STEP=0
step() {  # title : description-of-what-we-will-do
  STEP=$((STEP+1))
  printf '\n%s\n%sStep %d — %s%s\n' "$LINE" "$BOLD" "$STEP" "$1" "$RESET"
  [ -n "${2:-}" ] && printf '%s%s%s\n' "$DIM" "$2" "$RESET"
  printf '%s  ▸ press Enter to run…%s' "$DIM" "$RESET"; read -r _
}
happened() { printf '%s✓ %s%s\n' "$GREEN" "$1" "$RESET"; }
warn()     { printf '%s! %s%s\n' "$YELLOW" "$1" "$RESET"; }

# Mint an HS256 JWT.  Usage: mint '["ROLE_A"]' <organizationId|"">
mint() {
  python3 - "$JWT_SECRET" "$1" "$2" <<'PY'
import hmac, hashlib, base64, json, time, uuid, sys
secret = sys.argv[1].encode(); roles = json.loads(sys.argv[2]); org = sys.argv[3]
b = lambda d: base64.urlsafe_b64encode(d).rstrip(b'=')
header = b(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
payload = {"sub": str(uuid.uuid4()), "email": "demo@demo.test", "roles": roles,
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

# API call — params go in the query string (works for every HTTP verb).
# Usage: api METHOD URL TOKEN [key=value ...]
LAST_BODY=""; LAST_CODE=""
api() {
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

# ---------------------------------------------------------------------------
for t in python3 jq curl; do command -v "$t" >/dev/null || { echo "Missing tool: $t"; exit 1; }; done

printf '%sHoliday Planner — Organization & Payment demo%s\n' "$BOLD" "$RESET"
printf '%sOrganizationService %s   |   PaymentService %s%s\n' "$DIM" "$ORG_URL" "$PAY_URL" "$RESET"

# quick reachability check
if [ "$(curl -s -o /dev/null -w '%{http_code}' "$ORG_URL/api/organizations/health")" != "200" ] ||
   [ "$(curl -s -o /dev/null -w '%{http_code}' "$PAY_URL/api/payments/health")" != "200" ]; then
  printf '%sServices not reachable. Start them first: docker compose up -d%s\n' "$RED" "$RESET"; exit 1
fi

ADMIN_TOK="$(mint '["ADMIN","ORGANIZATION_OWNER"]' "")"

# ===========================================================================
usecase 1 "A new municipality joins Holiday Planner  (OrganizationService)"
# ===========================================================================

step "Create the organization" \
  "An ADMIN registers the municipality 'Gemeinde Demo' with a bank account and a booking start time."
api POST "$ORG_URL/api/organizations" "$ADMIN_TOK" \
  "name=Gemeinde Demo $RUN" "bankAccount=AT611904300234573201" "bookingStartTime=2026-06-01T08:00:00"
ORG_ID="$(jqr '.id')"
[ -n "$ORG_ID" ] || { warn "could not create organization"; exit 1; }
happened "Organization created with id $ORG_ID (no team members or sponsors yet)."

step "Add team members" \
  "Add two staff: one TEAM_MEMBER and one ACCOUNTANT (the accountant will handle payments later)."
api POST "$ORG_URL/api/organizations/$ORG_ID/team-members" "$ADMIN_TOK" \
  "userId=$(uuidgen_)" "firstName=Jan" "lastName=Burtscher" "email=jan@demo.test" "role=TEAM_MEMBER"
api POST "$ORG_URL/api/organizations/$ORG_ID/team-members" "$ADMIN_TOK" \
  "userId=$(uuidgen_)" "firstName=Aleksander" "lastName=Lukic" "email=aleksander@demo.test" "role=ACCOUNTANT"
happened "Two team members added to the organization."

step "Add sponsors" \
  "Local businesses sponsor the holiday programme; each sponsor has a name and a contribution amount."
api POST "$ORG_URL/api/organizations/$ORG_ID/sponsors" "$ADMIN_TOK" "name=Raiffeisenbank Vorarlberg" "amount=500.00"
api POST "$ORG_URL/api/organizations/$ORG_ID/sponsors" "$ADMIN_TOK" "name=Illwerke VKW" "amount=750.50"
happened "Two sponsors added."

step "Update the organization" \
  "The municipality changes its bank account and the date bookings open."
api PUT "$ORG_URL/api/organizations/$ORG_ID" "$ADMIN_TOK" \
  "bankAccount=AT483200000012345864" "bookingStartTime=2026-05-15T06:00:00"
happened "Bank account and booking start time updated."

step "Read the full overview" \
  "Fetch the organization with its enriched team members and sponsors in one call."
api GET "$ORG_URL/api/organizations/$ORG_ID/overview" "$ADMIN_TOK"
happened "Overview shows the org plus its 2 team members and 2 sponsors — Organization service done."

# ===========================================================================
usecase 2 "Collecting and refunding event fees  (PaymentService)"
# ===========================================================================
# The accountant token is scoped to THIS organization (payments are org-scoped).
ACC_TOK="$(mint '["ACCOUNTANT"]' "$ORG_ID")"

step "Two confirmed bookings create two payments" \
  "When a booking is confirmed a payment is opened (here via the direct endpoint). Both start as PENDING."
B1="$(uuidgen_)"; B2="$(uuidgen_)"
api POST "$PAY_URL/api/payments" "$ACC_TOK" "bookingId=$B1" "organizationId=$ORG_ID" "amount=15.00" "parentEmail=anna@demo.test" "eventName=Bicycle Tour"
P1="$(jqr '.id')"
api POST "$PAY_URL/api/payments" "$ACC_TOK" "bookingId=$B2" "organizationId=$ORG_ID" "amount=25.50" "parentEmail=ben@demo.test" "eventName=Climbing Day"
P2="$(jqr '.id')"
happened "Two PENDING payments created (15.00 and 25.50)."

step "List the open (PENDING) payments" \
  "The accountant looks at everything still waiting to be paid for this organization."
api GET "$PAY_URL/api/payments/organization/$ORG_ID/pending" "$ACC_TOK"
happened "Both payments are listed as PENDING."

step "Mark both payments as PAID" \
  "The accountant confirms the two bank transfers arrived."
api PATCH "$PAY_URL/api/payments/$P1/pay" "$ACC_TOK" "note=Bank transfer ref #1001"
api PATCH "$PAY_URL/api/payments/$P2/pay" "$ACC_TOK" "note=Bank transfer ref #1002"
happened "Both payments moved PENDING → PAID (paidAt + note set)."

step "Check the organization balance" \
  "The balance is the sum of all PAID payments for the organization."
api GET "$PAY_URL/api/payments/organization/$ORG_ID/balance" "$ACC_TOK"
happened "Balance is 40.50 (15.00 + 25.50)."

step "Refund one payment" \
  "A parent cancels late, so the accountant refunds payment #1. This also publishes a PaymentRefunded event to Kafka."
api PATCH "$PAY_URL/api/payments/$P1/refund" "$ACC_TOK" "note=Customer cancelled"
happened "Payment #1 moved PAID → REFUNDED."

step "Balance reflects the refund" \
  "Refunded money no longer counts towards the balance."
api GET "$PAY_URL/api/payments/organization/$ORG_ID/balance" "$ACC_TOK"
happened "Balance is now 25.50 (only the remaining PAID payment counts)."

step "Business rule check" \
  "Try to refund a payment that is not PAID — the service must reject it."
api PATCH "$PAY_URL/api/payments/$P1/refund" "$ACC_TOK" "note=again"
api PATCH "$PAY_URL/api/payments/$P1/pay" "$ACC_TOK" "note=cannot pay a refunded one"
happened "Re-refund was a safe no-op (200) and paying a REFUNDED payment was rejected (409) — Payment service done."

printf '\n%s\n%s✓ Demo complete.%s Both services exercised end-to-end.\n' "$LINE" "$GREEN$BOLD" "$RESET"
printf '%sExplore live: %s/swagger-ui/index.html  and  %s/swagger-ui/index.html%s\n' \
  "$DIM" "$ORG_URL" "$PAY_URL" "$RESET"
