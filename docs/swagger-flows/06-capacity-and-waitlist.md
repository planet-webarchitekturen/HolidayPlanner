# Capacity Increase And Waitlist Promotion

## Goal

Create a waitlisted booking, increase capacity, and let Booking Service promote the waitlist entry via Kafka.

## Required Setup

Create an event term with low capacity:

```json
{
  "startDateTime": "2026-06-12T09:00:00",
  "endDateTime": "2026-06-12T15:00:00",
  "minParticipants": 1,
  "maxParticipants": 1
}
```

Activate it:

```json
{
  "newStatus": "ACTIVE"
}
```

Create two different family members in Identity Service.

## 1. Create First Booking

Booking Swagger:

```text
http://localhost:8082/swagger-ui.html
```

Endpoint:

```http
POST /api/bookings
```

Use first `familyMemberId`.

Expected:

```text
status = CONFIRMED
```

## 2. Create Second Booking

Use second `familyMemberId`.

Expected:

```text
status = WAITLISTED
```

## 3. Increase Capacity

Event Swagger:

```text
http://localhost:8081/swagger-ui.html
```

Endpoint:

```http
PATCH /api/events/terms/{eventTermId}/capacity
```

Body:

```json
{
  "minParticipants": 1,
  "maxParticipants": 2
}
```

Event Service publishes:

```text
holiday-planner.event.capacity-increased
```

## 4. Verify Waitlist Promotion

Booking Swagger:

```http
GET /api/bookings/event-term/{eventTermId}
```

Expected after Kafka processing:

```text
second booking status = CONFIRMED
```

Kafka topic:

```text
holiday-planner.booking.waitlist-promoted
```
