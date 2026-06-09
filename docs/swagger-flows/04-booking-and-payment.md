# Booking And Automatic Payment Creation

## Goal

Create a booking for a family member. If Kafka and Payment Service are running, a payment is created asynchronously.

## Required Role

For booking creation use `USER`, `EVENT_OWNER`, or `ORGANIZATION_TEAM_MEMBER`.

Important: `ADMIN` is not allowed for `POST /api/bookings`.

## 1. Prepare Role

In `identity_db`:

```sql
UPDATE users
SET role = 'USER'
WHERE email = 'demo@example.com';
```

Log in again and authorize Booking Swagger:

```text
http://localhost:8082/swagger-ui.html
```

## 2. Create Booking

Endpoint:

```http
POST /api/bookings
```

Query parameters:

```text
familyMemberId = <familyMemberId>
eventTermId = <eventTermId>
```

Save:

```text
bookingId = ...
status = CONFIRMED
```

If capacity is full, status may be `WAITLISTED`.

## 3. Check Booking

Endpoint:

```http
GET /api/bookings/{bookingId}
```

## 4. Check Bookings For Term

Endpoint:

```http
GET /api/bookings/event-term/{eventTermId}
```

## 5. Check Payment Created By Kafka

Open Payment Swagger:

```text
http://localhost:8085/swagger-ui.html
```

Endpoint:

```http
GET /api/payments/booking/{bookingId}
```

If payment is not found immediately, wait a few seconds and check Kafka/Payment logs.

## 6. Mark Payment Paid

Required role: `ACCOUNTANT`.

In `identity_db`:

```sql
UPDATE users
SET role = 'ACCOUNTANT'
WHERE email = 'demo@example.com';
```

Log in again and authorize Payment Swagger.

Endpoint:

```http
PATCH /api/payments/{paymentId}/pay
```

Query parameter:

```text
note = Paid by bank transfer
```
