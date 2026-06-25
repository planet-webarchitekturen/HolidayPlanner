#!/usr/bin/env bash
#
# Live demo for Stories 4 & 5 (Muhi + Tarik).
#   Story 5: raise an event term's capacity -> waitlisted booking auto-promoted (Kafka)
#   Story 4: cancel the term -> all bookings cancelled (Kafka cascade)
#
# Prereqs:
#   1) Free port 5001 + RAM (stop other docker projects):
#        docker stop $(docker ps -q --filter name=hotelreservation) \
#                    $(docker ps -q --filter name=hotel_reserv) \
#                    $(docker ps -q --filter name=gravix) 2>/dev/null
#   2) docker compose up -d        # full Holiday Planner stack
#   3) Kafka UI open at http://localhost:5001 (watch the topics light up)
#
# Run:  bash scripts/demo-stories-4-5.sh
# Needs: python3, jq, curl.  NOTE: run with bash, not zsh ($UID/$USER are reserved in zsh).
set -euo pipefail

JWT_SECRET="holidayplanner-super-secret-key-that-is-at-least-256-bits-long"

mint_admin() {
  python3 - "$JWT_SECRET" <<'PY'
import hmac,hashlib,base64,json,time,uuid,sys
s=sys.argv[1].encode()
b=lambda d:base64.urlsafe_b64encode(d).rstrip(b'=')
h=b(json.dumps({"alg":"HS256","typ":"JWT"}).encode())
p=b(json.dumps({"sub":str(uuid.uuid4()),"organizationId":str(uuid.uuid4()),
 "email":"admin@demo.test","roles":["ADMIN","EVENT_OWNER"],
 "iat":int(time.time()),"exp":int(time.time())+14400}).encode())
print((h+b"."+p+b"."+b(hmac.new(s,h+b"."+p,hashlib.sha256).digest())).decode())
PY
}

ADMINTOK=$(mint_admin)
RUN=$(date +%s)

echo "════════ SETUP ════════"
ORG=$(curl -s -X POST localhost:8084/api/organizations -H "Authorization: Bearer $ADMINTOK" \
  -d "name=Demo $RUN&bankAccount=AT611904300234573201&bookingStartTime=2026-06-01T00:00:00" | jq -r .id)
echo "org           = $ORG"
EMAIL="parent-$RUN@demo.test"
PUSER=$(curl -s -X POST localhost:8083/api/auth/register \
  -d "email=$EMAIL&password=Password123!&phoneNumber=+430000&organizationId=$ORG" | jq -r .id)
echo "parent userId = $PUSER"
UTOK=$(curl -s -X POST localhost:8083/api/auth/login -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"Password123!\"}" | jq -r .token)
echo "login token   = (acquired, USER role)"
ANNA=$(curl -s -X POST localhost:8083/api/identity/users/$PUSER/family-members -H "Authorization: Bearer $UTOK" \
  -d "firstName=Anna&lastName=D&birthDate=2016-01-01&zip=6900" | jq -r .id)
BEN=$(curl -s -X POST localhost:8083/api/identity/users/$PUSER/family-members -H "Authorization: Bearer $UTOK" \
  -d "firstName=Ben&lastName=D&birthDate=2017-01-01&zip=6900" | jq -r .id)
echo "kids: Anna=${ANNA:0:8}  Ben=${BEN:0:8}"
EVENT=$(curl -s -X POST localhost:8081/api/events -H "Authorization: Bearer $ADMINTOK" -H "Content-Type: application/json" \
  -d '{"organizationId":"'$ORG'","eventOwnerId":"'$PUSER'","shortTitle":"Bike Tour","description":"demo","location":"Park","meetingPoint":"Gate","price":15.00,"paymentMethod":"BANK_TRANSFER","minimalAge":6,"maximalAge":16,"pictureUrl":""}' | jq -r .id)
TERM=$(curl -s -X POST localhost:8081/api/events/$EVENT/terms -H "Authorization: Bearer $ADMINTOK" -H "Content-Type: application/json" \
  -d '{"startDateTime":"2026-07-10T09:00:00","endDateTime":"2026-07-10T12:00:00","minParticipants":1,"maxParticipants":1}' | jq -r .id)
curl -s -X PATCH localhost:8081/api/events/terms/$TERM/status -H "Authorization: Bearer $ADMINTOK" -H "Content-Type: application/json" \
  -d '{"newStatus":"ACTIVE"}' >/dev/null
echo "event=${EVENT:0:8}  term=${TERM:0:8} (max=1, ACTIVE)"

echo; echo "════════ BOOKINGS ════════"
echo "Anna books  -> $(curl -s -X POST localhost:8082/api/bookings -H "Authorization: Bearer $UTOK" \
  -d "familyMemberId=$ANNA&eventTermId=$TERM" | jq -r .status)"
BENB=$(curl -s -X POST localhost:8082/api/bookings -H "Authorization: Bearer $UTOK" \
  -d "familyMemberId=$BEN&eventTermId=$TERM" | jq -r .id)
echo "Ben books   -> $(curl -s localhost:8082/api/bookings/$BENB -H "Authorization: Bearer $UTOK" | jq -r .status)"

echo; echo "════════ STORY 5: raise capacity 1 -> 2 ════════"
curl -s -X PATCH localhost:8081/api/events/terms/$TERM/capacity -H "Authorization: Bearer $ADMINTOK" -H "Content-Type: application/json" \
  -d '{"minParticipants":1,"maxParticipants":2}' >/dev/null
echo "capacity PATCHed; waiting for Kafka (CapacityIncreased -> promote)..."
sleep 4
echo "Ben now     -> $(curl -s localhost:8082/api/bookings/$BENB -H "Authorization: Bearer $UTOK" | jq -r .status)   (expect CONFIRMED)"

echo; echo "════════ STORY 4: cancel the term ════════"
curl -s -X PATCH localhost:8081/api/events/terms/$TERM/status -H "Authorization: Bearer $ADMINTOK" -H "Content-Type: application/json" \
  -d '{"newStatus":"CANCELLED"}' >/dev/null
echo "term CANCELLED; waiting for cascade..."
sleep 4
echo "bookings on term:"
curl -s localhost:8082/api/bookings/event-term/$TERM -H "Authorization: Bearer $UTOK" \
  | jq -r '.[] | "  - "+.familyMemberId[0:8]+"  "+.status'

echo; echo "✅ done — also check the topics in Kafka UI: http://localhost:5001"
