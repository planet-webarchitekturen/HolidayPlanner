# Event Service — Design Notes (Clean Architecture)

## Reasoning

We refactored **event-service** toward a **hybrid clean architecture**:

1. **CQRS-light** (same idea as [booking-service](booking-service/)): writes live in `command/`, reads in `query/`, HTTP stays thin in `controller/`.
2. **Ports & adapters**: `command/` and `query/` depend only on **`port/`** interfaces (`EventTermEventPublisher`, `BookingServicePort`, `IdentityServicePort`, `NotificationPort`). Kafka producers and `RestClient` adapters live in `kafka/` and `client/` so the application core does not import Spring Kafka or HTTP client APIs.

**Why not a full hexagonal package tree (`application` / `infrastructure`)?**  
The team already uses a flat per-service layout; we kept that for consistency while still enforcing dependency direction (inbound → application → ports ← adapters).

**Domain package** (`domain/`): pure rules (`EventTermStatusTransitions`) and typed exceptions. JPA entities remain in `shared/` (repo-wide convention).

---

## Findings (before → after)

| Issue | Resolution |
|--------|------------|
| `createEvent` existed in code but had **no REST endpoint** | `POST /api/events` with `CreateEventRequest` body |
| Controller used **`@RequestParam`** for large payloads | JSON bodies (`UpdateEventRequest`, `CreateEventTermRequest`, `ChangeStatusRequest`, …) per [api-examples.md](api-examples.md) |
| **No status transition rules** | `EventTermStatusTransitions`: `DRAFT→ACTIVE`, `DRAFT→CANCELLED`, `ACTIVE→CANCELLED` only |
| **Capacity** could be set below confirmed bookings | `updateCapacity` calls `BookingServicePort.getConfirmedBookingCount` and validates |
| **Waitlist promotion** after max increase was a comment only | Publishes `holiday-planner.event.capacity-increased` with `CapacityIncreasedPayload` (booking team adds consumer) |
| `RuntimeException` for not-found | `EventNotFoundException` / `EventTermNotFoundException` + `GlobalExceptionHandler` → 404 |
| **Schedulers** missing | `AutoCancelUnderfilledTermsJob`, `DayBeforeNotificationsJob` (`@EnableScheduling`, cron in `application.yml`) |
| `sendMessageToParticipants` missing | `POST /api/events/terms/{id}/messages` + `NotificationPort` → `POST /api/notifications/email/bulk` |
| `cancelledBy` in Kafka payload was lowercase | Values aligned with domain docs: `EVENT_OWNER`, `SYSTEM` |

---

## Inter-service contracts used by event-service

| Dependency | Endpoint / mechanism | Notes |
|-------------|----------------------|--------|
| booking-service | `GET /api/bookings/event-term/{id}/count` | Implemented |
| booking-service | `GET /api/bookings/event-term/{id}/emails` | **Not implemented yet** — client returns `[]` on 404 |
| booking-service | `GET /api/bookings/event-term/{id}/participant-names` | **Not implemented yet** — day-before job publishes empty names until added |
| booking-service | Consume `holiday-planner.event.capacity-increased` | **To be implemented** — should call `promoteFromWaitingList` |
| identity-service | `GET /api/identity/caregivers/{id}` | Used for caregiver email in scheduler |
| notification-service | `POST /api/notifications/email/bulk` | Used for `sendMessageToParticipants` |

---

## Open questions for the team

1. **Booking HTTP API**: Add `emails` and `participant-names` (or one aggregated DTO) so messaging and caregiver PDF flow are complete without empty payloads.
2. **Kafka consumer** on booking-service for `holiday-planner.event.capacity-increased` — idempotent handler keyed by `eventTermId`.
3. **Scheduler timezone**: jobs use server default `LocalDate` / `LocalDateTime`; production may need an explicit `ZoneId` (e.g. Europe/Vienna).
4. **Transactional outbox**: today DB commit and Kafka publish are not atomic; acceptable for coursework or add outbox later.
5. **Auth**: no JWT between services yet — same as rest of monorepo.

---

## Tests added

- **Unit**: `EventTermCommandServiceUnitTest` — transitions, capacity Kafka publish, send-message guard.
- **Component**: `EventControllerComponentTest` — HTTP stack + H2 (`application-test.yml`).
- **Contract (consumer)**: `BookingServiceConsumerContractTest` — WireMock stubs for booking client.
- **Contract (provider)**: `EventProviderContractTest` — stable JSON field names for `GET /api/events/terms/{id}`.
