# Manual Payment Operations

## Goal

Inspect payments, create a manual payment, mark it as paid, refund it, and check balances.

## Required Role

Use `ACCOUNTANT` for `pay` and `refund`.

Use `ACCOUNTANT` or `ORGANIZATION_OWNER` for manual payment creation.

After changing role in SQL, log in again and authorize Payment Swagger:

```text
http://localhost:8085/swagger-ui.html
```

## 1. Get Payment By Booking

Endpoint:

```http
GET /api/payments/booking/{bookingId}
```

Save:

```text
paymentId = ...
```

## 2. List Organization Payments

Endpoint:

```http
GET /api/payments/organization/{organizationId}
```

## 3. List Pending Payments

Endpoint:

```http
GET /api/payments/organization/{organizationId}/pending
```

## 4. Event Term Payment Overview

Endpoint:

```http
GET /api/payments/event-terms/{eventTermId}/overview
```

## 5. Mark Payment Paid

Endpoint:

```http
PATCH /api/payments/{paymentId}/pay
```

Query parameter:

```text
note = Paid by bank transfer
```

## 6. Refund Payment

Endpoint:

```http
PATCH /api/payments/{paymentId}/refund
```

Query parameter:

```text
note = Refunded after cancellation
```

This publishes:

```text
holiday-planner.payment.refunded
```

## 7. Organization Balance

Endpoint:

```http
GET /api/payments/organization/{organizationId}/balance
```
