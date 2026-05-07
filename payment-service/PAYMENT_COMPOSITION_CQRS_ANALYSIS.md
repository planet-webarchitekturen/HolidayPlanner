# Payment Service: Composition & CQRS Analysis

## Composition Queries

### Current Composition Queries in Payment Service

#### Implemented: `getEventTermPaymentOverview(eventTermId)`
- **Endpoint:** `GET /api/payments/event-terms/{eventTermId}/overview`
- **Source:** `PaymentQueryService.java`
- **Controller:** `PaymentController.java`
- **What it does:**
  - Fetches event term details from `event-service`
  - Fetches all bookings for the event term from `booking-service`
  - Fetches all matching payments from the `payment-service` database
  - Combines the data into one accountant-facing payment overview
  - Calculates payment statistics like paid amount, open amount and missing payments
- **Why:** Accountants need to verify which participant bookings for a specific event term have already been paid.
- **Pattern:** Synchronous API composition
- **Benefit:** 1 frontend call instead of separate calls to payment-service, booking-service and event-service

#### Data Sources

| Field | Source |
|---|---|
| `paymentId`, `amount`, `paymentStatus`, `paidAt`, `refundedAt`, `note` | payment-service database |
| `bookingId`, `familyMemberId`, `bookingStatus`, `bookedAt` | booking-service via `GET /api/bookings/event-term/{eventTermId}` |
| `eventTermId`, `eventId`, `eventName`, `eventLocation`, `price`, `startDateTime`, `endDateTime`, `minParticipants`, `maxParticipants`, `eventTermStatus` | event-service via `GET /api/events/terms/{eventTermId}` |
| `bookingCount`, `billableBookingCount`, `paidCount`, `pendingCount`, `refundedCount`, `missingPaymentCount`, `totalExpectedAmount`, `totalOpenAmount` | computed in payment-service |

#### Response Purpose

The response is designed for an accountant overview page.

It answers questions like:

- How many bookings exist for this event term?
- How many confirmed bookings should be paid?
- Which bookings already have a payment?
- Which payments are still pending?
- Which confirmed bookings do not have a payment record yet?
- How much money was expected, received, refunded or is still open?

#### Example Use Case

An accountant opens an event term and wants to verify the payment state of all participant bookings.

Without this composition query, the frontend would need to:

1. Call event-service to get event term details
2. Call booking-service to get all bookings for the event term
3. Call payment-service for each booking or for all related payments
4. Merge the result manually
5. Calculate totals and missing payments

With the composition query, the frontend only calls:

```http
GET /api/payments/event-terms/{eventTermId}/overview
```

---

### Candidate Composition Queries

#### 1. `getOrganizationFinancialOverview(organizationId)`
- **Query:** Financial overview for one organization
- **Sources:**
  - payment-service: participant payments, paid amount, pending amount, refunds
  - organization-service: organization name, bank account, sponsors
  - optionally event-service: event costs
- **Use Case:** Accountant checks the financial state of a municipality or region.
- **Effort:** Medium — requires organization-service client and possibly sponsor/cost model support

#### 2. `getPendingPaymentsWithContactDetails(organizationId)` 
- **Query:** All pending payments with parent/contact information
- **Sources:**
  - payment-service: pending payments
  - booking-service: booking and family member references
  - identity-service: parent email and phone number
  - event-service: event name and event term date
- **Use Case:** Accountant sends reminders to participants who have not paid yet.
- **Effort:** High — requires identity-service and event-service enrichment

#### 3. `getPaymentDetailsByBooking(bookingId)`
- **Query:** Single booking payment details enriched with event and participant context
- **Sources:**
  - payment-service: payment status and amount
  - booking-service: booking status and family member reference
  - event-service: event name, date and price
  - identity-service: participant/parent data
- **Use Case:** Support or accountant view for one specific booking/payment.
- **Effort:** Medium to high — depends on available booking and identity endpoints

---

## CQRS Pattern

### Current CQRS Implementation

The Payment Service follows CQRS by separating write operations from read operations.

| Component | Responsibility | Files |
|---|---|---|
| **Command Service** | State-changing operations | `PaymentCommandService.java` |
| **Query Service** | Read-only operations and composition query | `PaymentQueryService.java` |
| **Controller** | Routes HTTP requests to command or query side | `PaymentController.java` |
| **Kafka Consumer** | Converts incoming domain events into commands | `BookingCreatedConsumer.java` |
| **Legacy Facade** | Transitional wrapper around command/query services | `PaymentService.java` |
| **Clients** | Synchronous HTTP calls for read composition | `BookingServiceClient.java`, `EventServiceClient.java` |

---

### Command Side

`PaymentCommandService` contains all operations that modify payment state.

| Operation | Type | Reason |
|---|---|---|
| `createPayment(bookingId, organizationId, amount)` | Command | Creates a new payment record with status `PENDING` |
| `markAsPaid(paymentId, note)` | Command | Changes payment status to `PAID` and stores `paidAt` |
| `refundPayment(paymentId, note)` | Command | Changes payment status to `REFUNDED`, stores `refundedAt` and publishes a refund event |

#### Kafka as Command Trigger

`BookingCreatedConsumer` listens to booking events. If the booking status is `CONFIRMED`, it calls:

```java
paymentCommandService.createPayment(...)
```

This is correct CQRS usage because an incoming event causes a state change in the payment-service.

---

### Query Side

`PaymentQueryService` contains all read-only operations.

| Operation | Type | Reason |
|---|---|---|
| `getPaymentsByOrganization(organizationId)` | Query | Reads payments for an organization |
| `getPendingPayments(organizationId)` | Query | Reads pending payments only |
| `getPaymentByBooking(bookingId)` | Query | Reads one payment by booking reference |
| `calculateBalance(organizationId)` | Query | Aggregates paid payments |
| `getEventTermPaymentOverview(eventTermId)` | Query + Composition | Reads and combines payment, booking and event term data |

The query side does not publish Kafka events and does not change payment state.

---

### Why CQRS Makes Sense in Payment Service

Payment operations have two different responsibilities:

#### Commands
Commands must protect the correctness of payment state.

Examples:

- A payment is created after a confirmed booking
- An accountant marks a payment as paid
- A refund changes the payment state and emits a domain event

These operations may need validation, transactions and side effects.

#### Queries
Queries are optimized for accountant-facing views and reporting.

Examples:

- List open payments
- Calculate an organization's balance
- Build an event term payment overview
- Detect confirmed bookings without a payment record

These operations should be read-only and should not trigger side effects.

By separating both sides, the code is easier to understand and safer to extend.




