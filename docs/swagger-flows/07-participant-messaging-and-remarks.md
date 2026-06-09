# Participant Messaging And Remarks

## Goal

Send a message to confirmed participants and create/list remarks.

## Required Setup

You need:

```text
eventTermId with status ACTIVE
at least one CONFIRMED booking
familyMemberId
eventOwnerId
```

## 1. Send Message To Participants

Event Swagger:

```text
http://localhost:8081/swagger-ui.html
```

Endpoint:

```http
POST /api/events/terms/{eventTermId}/messages
```

Body:

```json
{
  "subject": "Important information",
  "message": "Please bring a helmet and a water bottle."
}
```

Event Service asks Booking Service for participant parent emails, then calls Notification Service.

If there are no participant emails, the command is skipped.

## 2. Create Remark

Endpoint:

```http
POST /api/events/terms/{eventTermId}/remarks
```

Body:

```json
{
  "familyMemberId": "<familyMemberId>",
  "eventOwnerId": "<eventOwnerId>",
  "description": "Max needs a vegetarian lunch."
}
```

## 3. List Remarks

Endpoint:

```http
GET /api/events/terms/{eventTermId}/remarks
```
