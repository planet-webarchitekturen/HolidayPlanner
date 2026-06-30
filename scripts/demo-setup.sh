#!/usr/bin/env bash
#
# SETUP for the Swagger + Kafka demo (Stories 4 & 5).
# Builds the scenario up to: Anna CONFIRMED, Ben WAITLISTED — then stops.
# You do the two TRIGGERS live in Swagger UI (raise capacity, cancel term).
#
# Run:  bash scripts/demo-setup.sh
# Needs: python3, jq, curl, and the full stack up (docker compose up -d).
set -euo pipefail
JWT_SECRET="holidayplanner-super-secret-key-that-is-at-least-256-bits-long"

ADMINTOK=$(python3 - "$JWT_SECRET" <<'PY'
import hmac,hashlib,base64,json,time,uuid,sys
s=sys.argv[1].encode()
b=lambda d:base64.urlsafe_b64encode(d).rstrip(b'=')
h=b(json.dumps({"alg":"HS256","typ":"JWT"}).encode())
p=b(json.dumps({"sub":str(uuid.uuid4()),"organizationId":str(uuid.uuid4()),
 "email":"admin@demo.test","roles":["ADMIN","EVENT_OWNER"],
 "iat":int(time.time()),"exp":int(time.time())+14400}).encode())
print((h+b"."+p+b"."+b(hmac.new(s,h+b"."+p,hashlib.sha256).digest())).decode())
PY
)
RUN=$(date +%s)

ORG=$(curl -s -X POST localhost:8084/api/organizations -H "Authorization: Bearer $ADMINTOK" \
  -d "name=Demo $RUN&bankAccount=AT611904300234573201&bookingStartTime=2026-06-01T00:00:00" | jq -r .id)
EMAIL="parent-$RUN@demo.test"
PUSER=$(curl -s -X POST localhost:8083/api/auth/register \
  -d "email=$EMAIL&password=Password123!&phoneNumber=+430000&organizationId=$ORG" | jq -r .id)
UTOK=$(curl -s -X POST localhost:8083/api/auth/login -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"Password123!\"}" | jq -r .token)
ANNA=$(curl -s -X POST localhost:8083/api/identity/users/$PUSER/family-members -H "Authorization: Bearer $UTOK" \
  -d "firstName=Anna&lastName=D&birthDate=2016-01-01&zip=6900" | jq -r .id)
BEN=$(curl -s -X POST localhost:8083/api/identity/users/$PUSER/family-members -H "Authorization: Bearer $UTOK" \
  -d "firstName=Ben&lastName=D&birthDate=2017-01-01&zip=6900" | jq -r .id)
EVENT=$(curl -s -X POST localhost:8081/api/events -H "Authorization: Bearer $ADMINTOK" -H "Content-Type: application/json" \
  -d '{"organizationId":"'$ORG'","eventOwnerId":"'$PUSER'","shortTitle":"Bike Tour","description":"demo","location":"Park","meetingPoint":"Gate","price":15.00,"paymentMethod":"BANK_TRANSFER","minimalAge":6,"maximalAge":16,"pictureUrl":""}' | jq -r .id)
TERM=$(curl -s -X POST localhost:8081/api/events/$EVENT/terms -H "Authorization: Bearer $ADMINTOK" -H "Content-Type: application/json" \
  -d '{"startDateTime":"2026-07-10T09:00:00","endDateTime":"2026-07-10T12:00:00","minParticipants":1,"maxParticipants":1}' | jq -r .id)
curl -s -X PATCH localhost:8081/api/events/terms/$TERM/status -H "Authorization: Bearer $ADMINTOK" -H "Content-Type: application/json" \
  -d '{"newStatus":"ACTIVE"}' >/dev/null
ANNAB=$(curl -s -X POST localhost:8082/api/bookings -H "Authorization: Bearer $UTOK" -d "familyMemberId=$ANNA&eventTermId=$TERM" | jq -r .id)
BENB=$(curl -s -X POST localhost:8082/api/bookings -H "Authorization: Bearer $UTOK" -d "familyMemberId=$BEN&eventTermId=$TERM" | jq -r .id)

cat <<EOF

✅ Scenario ready:  Anna = CONFIRMED,  Ben = WAITLISTED
═══════════════════════════════════════════════════════════════════════
PASTE THIS INTO SWAGGER "Authorize" (raw token, no "Bearer "):

$ADMINTOK

───────────────────────────────────────────────────────────────────────
IDs you'll need during the demo:
  TERM ID         = $TERM
  BEN booking ID  = $BENB
  (Anna booking   = $ANNAB)
───────────────────────────────────────────────────────────────────────
Open these in the browser:
  Event  Swagger : http://localhost:8081/swagger-ui/index.html
  Booking Swagger: http://localhost:8082/swagger-ui/index.html
  Kafka UI       : http://localhost:5001
═══════════════════════════════════════════════════════════════════════
EOF
