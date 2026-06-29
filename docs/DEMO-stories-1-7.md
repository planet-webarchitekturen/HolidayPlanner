# Stories 1 & 7 - Implementation Summary

This document describes the two implemented user stories: what they do, which services are involved, and how the implementation works.

---

## Story 1 - Create Booking

Story 1 allows a parent to create a booking for a family member on an active event term.

The booking is only created if all required business rules pass:

- the event term exists and is `ACTIVE`
- the family member exists
- the family member matches the event age limits
- the organization's booking window is already open
- the same family member does not already have an active booking for the same term

If the event term still has free capacity, the booking is created as `CONFIRMED`. If the term is full, the booking can be created as `WAITLISTED`.

### Services involved

- **booking-service** owns the booking creation logic.
- **event-service** provides event term and event details.
- **identity-service** provides family member data, including birth date and owner information.
- **organization-service** provides the organization's booking start time.
- **payment-service** consumes the booking event and creates a pending payment.
- **Kafka** is used to publish the `BookingCreated` event.

### Implementation

The main logic is implemented in `BookingCommandService`.

When `POST /api/bookings` is called, booking-service first gathers the required data from the other services. It then validates the booking rules inside the command service. If one rule fails, the booking is rejected with the matching HTTP error, for example `400` for invalid age or `409` for duplicate bookings and closed booking windows.

After a valid booking is saved, booking-service creates a `BookingCreated` event. This event is not sent directly to Kafka from the command logic. Instead, it is written to the `outbox_events` table in the same transaction as the booking. A scheduled outbox relay later publishes the event to Kafka and marks it as processed after Kafka confirms the send.

This implements the transactional outbox pattern: the booking and the event record are committed together, so a Kafka problem does not silently lose the event.

The payment-service listens to `holiday-planner.booking.created`. For confirmed bookings, it creates a `PENDING` payment. The event payload contains the required payment information, including booking id, amount, organization id, parent email, and event name.

### Tests

The implementation is covered by focused tests for:

- booking rule validation
- duplicate booking rejection
- booking window validation
- age validation
- controller behavior
- event creation through the outbox
- outbox serialization
- outbox relay success and retry behavior
- payment-relevant event payload data

---

## Story 7 - Family Member Veto

Story 7 prevents a parent from deleting a family member while that family member still has active bookings.

An active booking means:

- `CONFIRMED`
- `WAITLISTED`

Cancelled bookings do not block deletion.

### Services involved

- **identity-service** owns users and family members.
- **booking-service** owns bookings and knows whether a family member still has active bookings.

### Implementation

The delete operation starts in identity-service through:

```http
DELETE /api/identity/family-members/{memberId}
```

Before deleting the family member, identity-service calls booking-service:

```http
GET /api/bookings/family-member/{memberId}/has-active
```

booking-service checks its booking repository for active bookings of that family member. If at least one active booking exists, booking-service returns `true`.

If identity-service receives `true`, it rejects the deletion with `409 Conflict`. The family member remains in the system.

If booking-service returns `false`, identity-service deletes the family member.

If booking-service is unavailable or the check cannot be completed, identity-service rejects the deletion. This is intentional fail-safe behavior because deleting the family member without checking bookings could leave inconsistent data.

### Tests

The implementation is covered by focused tests for:

- detecting active bookings in booking-service
- returning the active-booking state through the booking REST endpoint
- rejecting family member deletion when active bookings exist
- allowing deletion when only cancelled or no bookings exist
- failing safely when booking-service is unavailable

---

## Demo scripts

Two scripts exist to recreate both stories end-to-end:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\demo-story-1-create-booking.ps1
powershell -ExecutionPolicy Bypass -File scripts\demo-story-7-family-member-veto.ps1
```

They are mainly used as automated proof that the implemented flows work across the involved services.

