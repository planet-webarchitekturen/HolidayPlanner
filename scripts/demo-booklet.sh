#!/usr/bin/env bash
#
# DEMO: BookletService (:8087)
# ---------------------------------------------------------------------------
# Two short use cases that together cover most of what the service does.
# You press Enter before each step; after each step a one-line summary explains
# what just happened.
#
#   Use case 1 — A municipality prints its holiday booklet   (BookletService)
#   Use case 2 — Who may print, and the service-only endpoint (access control)
#
# Run:   bash scripts/demo-booklet.sh
# Needs: python3, jq, curl, and booklet-service + organization-service +
#        event-service running (docker compose up -d). The booklet is built by
#        pulling live data from the organization and event services.
# ---------------------------------------------------------------------------
set -uo pipefail

JWT_SECRET="${JWT_SECRET:-holidayplanner-super-secret-key-that-is-at-least-256-bits-long}"
SERVICE_SECRET="${SERVICE_SECRET:-holidayplanner-internal-service-secret}"
BOOKLET_URL="${BOOKLET_URL:-http://localhost:8087}"
ORG_URL="${ORG_URL:-http://localhost:8084}"
EVENT_URL="${EVENT_URL:-http://localhost:8081}"
OUT_DIR="${OUT_DIR:-${TMPDIR:-/tmp}}"
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

urlenc() { python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1],safe=""))' "$1"; }
uuidgen_() { python3 -c 'import uuid;print(uuid.uuid4())'; }

# API call with query-string params (organization-service style).
# Usage: apiq METHOD URL TOKEN [key=value ...]
LAST_BODY=""; LAST_CODE=""
apiq() {
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

# API call with a JSON body (event-service style).
# Usage: api METHOD URL TOKEN [json-body]
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

# Download a PDF and report its size / validity.  Usage: getpdf URL TOKEN OUTFILE
getpdf() {
  local url="$1" token="$2" out="$3"
  printf '   %sGET %s%s\n' "$YELLOW" "$url" "$RESET"
  LAST_CODE="$(curl -s -o "$out" -w '%{http_code}' -H "Authorization: Bearer $token" "$url")"
  local color="$GREEN"; case "$LAST_CODE" in 2*) color="$GREEN";; *) color="$RED";; esac
  printf '   %s→ HTTP %s%s\n' "$color" "$LAST_CODE" "$RESET"
  if [ "$LAST_CODE" = "200" ]; then
    local size; size="$(wc -c < "$out" | tr -d ' ')"
    if LC_ALL=C grep -qa '%PDF' "$out"; then
      printf '   %s↳ saved %s bytes to %s (valid PDF)%s\n' "$DIM" "$size" "$out" "$RESET"
    else
      printf '   %s↳ saved %s bytes to %s%s\n' "$DIM" "$size" "$out" "$RESET"
    fi
  fi
}

# Status-only access-control check.  Usage: expect LABEL URL EXPECTED [curl-header ...]
expect() {
  local label="$1" url="$2" want="$3"; shift 3
  local args=(-s -o /dev/null -w '%{http_code}' "$url")
  local h; for h in "$@"; do args+=(-H "$h"); done
  local got; got="$(curl "${args[@]}")"
  if [ "$got" = "$want" ]; then
    printf '   %s✓ %-46s → HTTP %s (expected %s)%s\n' "$GREEN" "$label" "$got" "$want" "$RESET"
  else
    printf '   %s✗ %-46s → HTTP %s (expected %s)%s\n' "$RED" "$label" "$got" "$want" "$RESET"
  fi
}

# ---------------------------------------------------------------------------
for t in python3 jq curl; do command -v "$t" >/dev/null || { echo "Missing tool: $t"; exit 1; }; done

printf '%sHoliday Planner — Booklet demo%s\n' "$BOLD" "$RESET"
printf '%sBookletService %s   |   OrganizationService %s   |   EventService %s%s\n' \
  "$DIM" "$BOOKLET_URL" "$ORG_URL" "$EVENT_URL" "$RESET"

# quick reachability check — the booklet is built from org + event data
if [ "$(curl -s -o /dev/null -w '%{http_code}' "$BOOKLET_URL/api/booklets/health")" != "200" ] ||
   [ "$(curl -s -o /dev/null -w '%{http_code}' "$ORG_URL/api/organizations/health")" != "200" ] ||
   [ "$(curl -s -o /dev/null -w '%{http_code}' "$EVENT_URL/api/events/health")" != "200" ]; then
  printf '%sNeed booklet-service, organization-service and event-service running.%s\n' "$RED" "$RESET"
  printf '%sStart them first: docker compose up -d%s\n' "$RED" "$RESET"; exit 1
fi

ORG_ID="$(uuidgen_)"
OWNER_ID="$(uuidgen_)"
ADMIN_TOK="$(mint '["ADMIN","ORGANIZATION_OWNER"]' "$ORG_ID")"
OWNER_TOK="$(mint '["EVENT_OWNER"]' "$ORG_ID" "$OWNER_ID")"

# ===========================================================================
usecase 1 "A municipality prints its holiday booklet  (BookletService)"
# ===========================================================================
# The booklet is a single PDF that gathers everything a parent needs: the
# organization's team contacts, an index of every event date, the event
# details, and the sponsors. BookletService pulls all of this live from the
# organization-service and event-service the moment the PDF is requested.

step "Register the organization" \
  "An ADMIN creates the municipality 'Gemeinde Booklet' with a booking start time (printed in the booklet)."
apiq POST "$ORG_URL/api/organizations" "$ADMIN_TOK" \
  "name=Gemeinde Booklet $RUN" "bankAccount=AT611904300234573201" "bookingStartTime=2026-07-01T08:00:00"
ORG_ID="$(jqr '.id')"
[ -n "$ORG_ID" ] || { warn "could not create organization"; exit 1; }
# re-mint tokens now that we know the real org id
ADMIN_TOK="$(mint '["ADMIN","ORGANIZATION_OWNER"]' "$ORG_ID")"
OWNER_TOK="$(mint '["EVENT_OWNER"]' "$ORG_ID" "$OWNER_ID")"
happened "Organization created with id $ORG_ID."

step "Add team contacts" \
  "Two staff are added — they appear under 'Organization Team Contact' in the booklet."
apiq POST "$ORG_URL/api/organizations/$ORG_ID/team-members" "$ADMIN_TOK" \
  "userId=$(uuidgen_)" "firstName=Fabian" "lastName=Tuertscher" "email=fabian@demo.test" "role=TEAM_MEMBER"
apiq POST "$ORG_URL/api/organizations/$ORG_ID/team-members" "$ADMIN_TOK" \
  "userId=$(uuidgen_)" "firstName=Anna" "lastName=Berger" "email=anna@demo.test" "role=ACCOUNTANT"
happened "Two team contacts added."

step "Add sponsors" \
  "Local businesses sponsor the programme — they appear under 'Sponsors' in the booklet."
apiq POST "$ORG_URL/api/organizations/$ORG_ID/sponsors" "$ADMIN_TOK" "name=Raiffeisenbank Vorarlberg" "amount=500.00"
apiq POST "$ORG_URL/api/organizations/$ORG_ID/sponsors" "$ADMIN_TOK" "name=Illwerke VKW" "amount=750.50"
happened "Two sponsors added."

step "Publish two activities with dates" \
  "Each event gets one term (a concrete date) which is then activated for bookings — these fill the event index."
declare -a TITLES=("Bicycle Tour" "Climbing Day")
declare -a STARTS=("2026-07-15T09:00:00" "2026-07-22T10:00:00")
declare -a ENDS=("2026-07-15T17:00:00" "2026-07-22T16:00:00")
for i in 0 1; do
  api POST "$EVENT_URL/api/events" "$OWNER_TOK" "$(cat <<JSON
{
  "organizationId": "$ORG_ID",
  "eventOwnerId": "$OWNER_ID",
  "shortTitle": "${TITLES[$i]} $RUN",
  "description": "A guided ${TITLES[$i]} for the summer programme.",
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
  [ -n "$EVENT_ID" ] || { warn "could not create event ${TITLES[$i]}"; continue; }
  api POST "$EVENT_URL/api/events/$EVENT_ID/terms" "$OWNER_TOK" "$(cat <<JSON
{
  "startDateTime": "${STARTS[$i]}",
  "endDateTime": "${ENDS[$i]}",
  "minParticipants": 5,
  "maxParticipants": 20
}
JSON
)"
  TERM_ID="$(jqr '.id')"
  [ -n "$TERM_ID" ] && api PATCH "$EVENT_URL/api/events/terms/$TERM_ID/status" "$OWNER_TOK" '{"newStatus":"ACTIVE"}'
done
happened "Two events published, each with one active term."

step "Generate the booklet PDF" \
  "BookletService fetches the org, team, sponsors and events live, then renders one A4 PDF with PDFBox."
PDF_OUT="$OUT_DIR/booklet-$ORG_ID.pdf"
getpdf "$BOOKLET_URL/api/booklets/organizations/$ORG_ID" "$ADMIN_TOK" "$PDF_OUT"
[ "$LAST_CODE" = "200" ] || { warn "booklet generation failed (HTTP $LAST_CODE)"; exit 1; }
happened "Booklet PDF generated from live data and saved to $PDF_OUT."

step "Open the booklet (optional)" \
  "Open the generated PDF in the default viewer to read the printed booklet."
if command -v open >/dev/null 2>&1; then
  open "$PDF_OUT" && happened "Opened $PDF_OUT in the default PDF viewer."
elif command -v xdg-open >/dev/null 2>&1; then
  xdg-open "$PDF_OUT" >/dev/null 2>&1 && happened "Opened $PDF_OUT in the default PDF viewer."
else
  warn "No 'open'/'xdg-open' found — open it manually: $PDF_OUT"
fi

# ===========================================================================
usecase 2 "Who may print, and the service-only endpoint  (access control)"
# ===========================================================================
# Booklet generation is restricted to ORGANIZATION_OWNER / ADMIN. The
# participant-list PDF is internal: it is produced asynchronously from the
# 'participant-list-requested' Kafka event and may only be fetched
# service-to-service via the X-Service-Secret header (ROLE_SERVICE).

CAREGIVER_TOK="$(mint '["CAREGIVER"]' "$ORG_ID")"
RANDOM_TERM="$(uuidgen_)"

step "Booklet endpoint — role matrix" \
  "The same booklet URL is called with no token, the wrong role, and the right role."
expect "no token                → blocked"        "$BOOKLET_URL/api/booklets/organizations/$ORG_ID" 401
expect "CAREGIVER token          → forbidden"     "$BOOKLET_URL/api/booklets/organizations/$ORG_ID" 403 "Authorization: Bearer $CAREGIVER_TOK"
expect "ORGANIZATION_OWNER/ADMIN → allowed"       "$BOOKLET_URL/api/booklets/organizations/$ORG_ID" 200 "Authorization: Bearer $ADMIN_TOK"
happened "Only ORGANIZATION_OWNER / ADMIN may print a booklet."

step "Participant-list endpoint — service only" \
  "The participant-list PDF is internal. A normal user (even an admin JWT) is forbidden; only an internal service may read it."
expect "ADMIN JWT                → forbidden"      "$BOOKLET_URL/api/booklets/participant-list/$RANDOM_TERM" 403 "Authorization: Bearer $ADMIN_TOK"
happened "Participant lists are service-to-service only (X-Service-Secret / ROLE_SERVICE)."
printf '%s  ↳ The PDF itself is generated asynchronously from the participant-list-requested Kafka event.%s\n' "$DIM" "$RESET"

printf '\n%s\n%s✓ Demo complete.%s Booklet service exercised end-to-end.\n' "$LINE" "$GREEN$BOLD" "$RESET"
printf '%sBooklet saved at: %s%s\n' "$DIM" "$PDF_OUT" "$RESET"
printf '%sExplore live: %s/swagger-ui/index.html%s\n' "$DIM" "$BOOKLET_URL" "$RESET"
