# Notification Service Direct Calls

## Goal

Call Notification Service directly from Swagger.

## Required Role

Use `ADMIN` or `ORGANIZATION_OWNER`.

Authorize Notification Swagger:

```text
http://localhost:8090/swagger-ui.html
```

## 1. Send Single Email

Endpoint:

```http
POST /api/notifications/email
```

Body:

```json
{
  "to": "parent@example.com",
  "recipients": null,
  "subject": "Test mail",
  "body": "Hello from Holiday Planner."
}
```

## 2. Send Bulk Email

Endpoint:

```http
POST /api/notifications/email/bulk
```

Body:

```json
{
  "to": null,
  "recipients": [
    "parent1@example.com",
    "parent2@example.com"
  ],
  "subject": "Bulk test",
  "body": "Hello everyone."
}
```

## 3. Notify Booking Confirmed

Endpoint:

```http
POST /api/notifications/booking-confirmed
```

Query parameters:

```text
parentEmail = parent@example.com
eventName = Bike Tour
termDate = 2026-06-10T09:00
```

## 4. Notify Term Cancelled

Endpoint:

```http
POST /api/notifications/term-cancelled
```

Query parameters:

```text
parentEmail = parent@example.com
eventName = Bike Tour
termDate = 2026-06-10T09:00
```

## 5. Notify Caregiver With Participant List

Endpoint:

```http
POST /api/notifications/caregiver-participants
```

Query parameters:

```text
caregiverEmail = caregiver@example.com
eventName = Bike Tour
termDate = 2026-06-10T09:00
```

Body:

```json
[
  "Max Muster",
  "Lena Beispiel"
]
```

## Note

If mail credentials are not configured, the service may log mail-send failures. Kafka/message flow can still be tested through logs.
