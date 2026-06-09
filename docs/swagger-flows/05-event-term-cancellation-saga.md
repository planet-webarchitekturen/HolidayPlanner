# Event Term Cancellation Saga

## Goal

Cancel an event term and observe the choreography saga.

## What Happens

1. Event Service sets `EventTerm.status = CANCELLED`.
2. `EventTermCancellationSaga` publishes `EventTermCancelled`.
3. Booking Service consumes the event and cancels all bookings for the term.
4. Booking Service publishes `BookingCancelled` per booking.
5. Notification Service reacts to cancellation events.
6. Payment Service can react to `BookingCancelled` and refund paid payments.

This is eventually consistent. The response from Event Service only confirms the local event-term cancellation and event publishing.

## Required Role

Use `ADMIN`, `EVENT_OWNER`, or `ORGANIZATION_TEAM_MEMBER`.

Authorize Event Swagger:

```text
http://localhost:8081/swagger-ui.html
```

## 1. Cancel Event Term

Endpoint:

```http
PATCH /api/events/terms/{eventTermId}/status
```

Body:

```json
{
  "newStatus": "CANCELLED"
}
```

Expected Event response:

```text
status = CANCELLED
```

## 2. Verify Event Term

Endpoint:

```http
GET /api/events/terms/{eventTermId}
```

## 3. Verify Booking Cancellation

Open Booking Swagger:

```text
http://localhost:8082/swagger-ui.html
```

Endpoint:

```http
GET /api/bookings/event-term/{eventTermId}
```

Expected after Kafka processing:

```text
booking.status = CANCELLED
```

## 4. Verify Payment

Open Payment Swagger:

```text
http://localhost:8085/swagger-ui.html
```

Endpoint:

```http
GET /api/payments/booking/{bookingId}
```

If the payment was paid, check whether it was refunded by the cancellation chain.

## 5. Kafka Topics To Watch

Kafka UI:

```text
http://localhost:5001
```

Topics:

```text
holiday-planner.event.term-cancelled
holiday-planner.booking.cancelled
holiday-planner.payment.refunded
```

## Important

There is no global rollback. If a downstream service fails, the intended handling is retry/idempotent reprocessing, not reverting the event term to `ACTIVE`.
