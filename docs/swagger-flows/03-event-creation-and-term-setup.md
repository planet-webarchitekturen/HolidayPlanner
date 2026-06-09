# Event Creation And Event Term Setup

## Goal

Create an event, create a term, assign a caregiver, and activate the term.

## Required Role

Use `ADMIN`, `EVENT_OWNER`, or `ORGANIZATION_TEAM_MEMBER`.

After changing role in SQL, log in again and authorize Event Swagger.

```text
http://localhost:8081/swagger-ui.html
```

## 1. Create Event

Endpoint:

```http
POST /api/events
```

Body:

```json
{
  "organizationId": "11111111-1111-1111-1111-111111111111",
  "eventOwnerId": "<userId>",
  "shortTitle": "Bike Tour",
  "description": "A nice bike tour for kids",
  "location": "Dornbirn",
  "meetingPoint": "Main station",
  "price": 25.00,
  "paymentMethod": "BANK_TRANSFER",
  "minimalAge": 8,
  "maximalAge": 14,
  "pictureUrl": "https://example.com/bike.jpg"
}
```

Save:

```text
eventId = ...
```

## 2. Create Event Term

Endpoint:

```http
POST /api/events/{eventId}/terms
```

Body:

```json
{
  "startDateTime": "2026-06-10T09:00:00",
  "endDateTime": "2026-06-10T15:00:00",
  "minParticipants": 1,
  "maxParticipants": 10
}
```

Save:

```text
eventTermId = ...
```

## 3. Assign Caregiver

Create a caregiver first in Identity Service if needed.

Endpoint:

```http
POST /api/events/terms/{eventTermId}/caregivers/{caregiverId}
```

## 4. Activate Term

Endpoint:

```http
PATCH /api/events/terms/{eventTermId}/status
```

Body:

```json
{
  "newStatus": "ACTIVE"
}
```

## 5. Verify

Endpoint:

```http
GET /api/events/terms/{eventTermId}
```

Expected:

```text
status = ACTIVE
```

## Common Mistake

`POST /api/events/{eventId}/terms` needs an `eventId` from the `events` table.

`PATCH /api/events/terms/{eventTermId}/status` needs an `eventTermId` from the `event_terms` table.
