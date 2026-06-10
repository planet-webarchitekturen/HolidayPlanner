# Team User Stories — Cross-Service Events

This document splits the system into distributable user stories, each organized around a **domain event / cross-service flow** (saga). Every story lists the services it touches, what already works, the concrete tasks left, and acceptance criteria — so one team member can own a story end-to-end.

Source docs: `docs/system-operations.md`, `docs/kafka-decisions.md`, `docs/messaging-conventions.md`, `docs/open-questions.md`, `docs/delete-organization-saga.md`.

Status verified against codebase audit + test run (see [Test Run Results](#test-run-results) at the bottom).

---

## Story Index


| #   | Story (Event)                                                                | Services Involved                                             | Status        | Priority |
| --- | ---------------------------------------------------------------------------- | ------------------------------------------------------------- | ------------- | -------- |
| 1   | Create Booking (`BookingCreated`)                                            | booking, event, identity, organization, payment, notification | Partial       | High     |
| 2   | Cancel Booking & Waitlist Promotion (`BookingCancelled`, `WaitlistPromoted`) | booking, notification, payment                                | Partial       | High     |
| 3   | Refund a Payment (`PaymentRefunded`)                                         | payment, notification, booking                                | Partial       | High     |
| 4   | Event Term Cancellation (`EventTermCancelled`)                               | event, booking, notification, payment                         | Mostly done   | Medium   |
| 5   | Capacity Increase → Waitlist Promotion (`CapacityIncreased`)                 | event, booking, notification                                  | Producer only | High     |
| 6   | Day-Before Caregiver Notification (`ParticipantListRequested`)               | event, booking, notification, booklet                         | Partial       | Medium   |
| 7   | Remove Family Member (Guard Check)                                           | identity, booking                                             | Missing veto  | High     |
| 8   | Delete Organization (Saga)                                                   | organization, event, booking, payment                         | Sync-only     | Medium   |
| 9   | Authentication & Inter-Service Security (JWT)                                | identity, all services                                        | Partial       | Medium   |
| 10  | Real Email Delivery                                                          | notification                                                  | Stubbed       | Medium   |


---

## Story 1 — Create Booking (`BookingCreated`)

> *As a parent, I want to book an event term for my child so that they can participate — but only if they meet the age requirements and booking is open.*

**Topic:** `holiday-planner.booking.created`
**Flow:** booking-service → event-service (SYNC verify) → identity-service (SYNC age) → organization-service (SYNC booking window) → Kafka → payment-service + notification-service

### What works today

- `POST /api/bookings` with `familyMemberId` + `eventTermId` (`BookingCommandService.createBooking`)
- Term verification: calls `GET /api/events/terms/{id}` and checks `ACTIVE` client-side
- Capacity check → `CONFIRMED` or `WAITLISTED`
- Publishes `BookingCreated` with `amount`, `parentEmail`, `eventName`, `organizationId`
- payment-service consumes it (CONFIRMED-only, idempotent via `findByBookingId`)
- notification-service consumes it (confirmed + waitlisted emails — delivery stubbed, see Story 10)
- The old `maxParticipants` request-param bug is already fixed
- **E2E verified (2026-06-10):** `BookingWaitlistFlowIT` — CONFIRMED booking + PENDING payment + WAITLISTED second booking (see Test Run Results)

### Tasks

1. **Age verification** *(identity + booking)*
  - identity-service: add `GET /api/identity/family-members/{memberId}` returning at least `birthDate` (no such endpoint exists; spec calls it `getFamilyMember`)
  - booking-service: in `createBooking`, fetch the family member and validate against `Event.minimalAge`/`maximalAge` → `400` if not eligible
2. **Booking window check** *(booking + organization)*
  - booking-service: add an `OrganizationServiceClient`, read `Organization.bookingStartTime` (already exposed via `GET /api/organizations/{id}`), reject bookings before that time → `409`
3. **Duplicate booking guard** — same family member must not book the same term twice
4. **Decide & enforce payment policy** — `BookingCreated` is currently published for WAITLISTED too; payment-service guards it, but align with the documented decision (payment only on CONFIRMED)
5. **Tests** — unit tests for `BookingCommandService.createBooking` (current tests target the deprecated `BookingService`)

### Acceptance criteria

- Booking a 5-year-old onto an event with `minimalAge=8` returns `400`, no booking row, no Kafka event
- Booking before `bookingStartTime` returns `409`
- Second booking of the same member+term returns `409`

---

## Story 2 — Cancel Booking & Waitlist Promotion (`BookingCancelled`, `WaitlistPromoted`)

> *As a parent or event owner, I want to cancel a booking, so that the next waitlisted child automatically gets the spot.*

**Topics:** `holiday-planner.booking.cancelled`, `holiday-planner.booking.waitlist-promoted`
**Flow:** booking-service (cancel + internal promote) → Kafka → notification-service (+ payment-service, see Story 3)

### What works today

- `DELETE /api/bookings/{id}` cancels and internally calls `promoteFromWaitingList(eventTermId, 1)`
- Publishes `BookingCancelled` (with `cancelledBy`) and `WaitlistPromoted` per promotion
- notification-service consumes both and routes by `cancelledBy`
- **E2E verified (2026-06-10):** cancel Anna → Ben promoted from WAITLISTED to CONFIRMED (`BookingWaitlistFlowIT`)

### Tasks

1. **User self-cancellation rule** — spec defines `cancelBookingByUser` with a **3-days-before-event deadline**; today the same `cancelBooking` path is used with no deadline check. Add the deadline rule when the caller has role `USER`
2. **Deterministic waitlist order** — the waitlisted query has no `ORDER BY`; promote strictly FIFO by `bookedAt`
3. `**cancelledBy` completeness** — values are currently `"parent"` / `"term-cancelled"`; align with documented enum `USER | EVENT_OWNER | SYSTEM` (open-questions #7)
4. **Tests** — promotion ordering, deadline rule, `cancelledBy` propagation

### Acceptance criteria

- User cancelling 2 days before the event start gets `409`; event owner can still cancel
- After cancellation, the oldest WAITLISTED booking becomes CONFIRMED and a `WaitlistPromoted` event is published

---

## Story 3 — Refund a Payment (`PaymentRefunded`)

> *As an accountant, I want refunds to be issued when a paid booking is cancelled, so parents get their money back and are notified.*

**Topic:** `holiday-planner.payment.refunded`
**Flow (manual):** payment-service REST → Kafka → notification-service
**Flow (automatic):** booking-service publishes `BookingCancelled` → payment-service should refund PAID payments → Kafka → notification-service

### What works today

- Manual refund: `PATCH /api/payments/{id}/refund` → publishes `PaymentRefunded`
- notification-service consumes `payment.refunded` → `notifyRefund` (delivery stubbed)
- `markAsPaid`, balance, pending/by-org/by-booking queries all implemented

### Tasks

1. **Auto-refund consumer** — payment-service has **no** `@KafkaListener` for `holiday-planner.booking.cancelled`. Per `system-operations.md` saga #2 step 6, add a consumer: if a payment exists for the booking and is `PAID` → refund it (publishes `PaymentRefunded`). Note: `kafka-decisions.md` contradicts this (lists notification-service as the only consumer) — **make the decision explicit and update both docs**
2. **Status pre-conditions** — `markAsPaid` does not validate the payment is `PENDING`, and `refundPayment` does not validate it is `PAID`. A `REFUNDED` payment can currently be re-marked `PAID`. Add transition validation → `409`
3. **Tests** — refund pre-conditions, idempotent consumption of duplicate `BookingCancelled` messages

### Acceptance criteria

- Cancelling a booking with a PAID payment results in status `REFUNDED` and one `PaymentRefunded` event (idempotent on redelivery)
- Refunding a `PENDING` payment returns `409`; paying a `REFUNDED` payment returns `409`

---

## Story 4 — Event Term Cancellation (`EventTermCancelled`)

> *As an event owner (or the nightly scheduler), I want to cancel an event term so that all bookings are cancelled and everyone is informed.*

**Topic:** `holiday-planner.event.term-cancelled`
**Flow:** event-service (status → CANCELLED, `EventTermCancellationSaga`) → Kafka → booking-service (`cancelAllBookings`) + notification-service (caregivers); each `BookingCancelled` → notification-service (parents) (+ payment refund, Story 3)

### What works today

- `PATCH /api/events/terms/{id}/status` triggers the saga; caregiver emails resolved and included in the payload
- booking-service `EventTermCancelledConsumer` cancels all bookings and publishes `BookingCancelled` per booking
- notification-service consumes both topics
- **Auto-cancel scheduler** (`AutoCancelUnderfilledTermsJob`, 03:00 daily) cancels ACTIVE terms starting within 24h with confirmed < min — implemented, runs the same saga with actor `SYSTEM`

### Tasks

1. **Scheduler tests** — both scheduled jobs have zero test coverage; add unit tests for the underfilled-detection query and the cancellation trigger
2. **Timezone** — schedulers use the JVM default timezone; pin a `ZoneId` (e.g. `Europe/Vienna`)
3. **Kafka consumer/integration tests** — `EventTermCancelledConsumer` is untested
4. *(Optional)* idempotent re-processing: re-delivering `EventTermCancelled` should be safe for already-cancelled bookings (verify, then document)

### Acceptance criteria

- Cancelling a term with 3 bookings produces 3 `BookingCancelled` events and 1 caregiver notification call
- An ACTIVE term starting tomorrow with 1/5 min participants is cancelled by the job; a DRAFT term is not

---

## Story 5 — Capacity Increase → Waitlist Promotion (`CapacityIncreased`)

> *As an event owner, I want to raise the max participants of a term so that waitlisted children automatically get the freed spots.*

**Topic:** `holiday-planner.event.capacity-increased`
**Flow:** event-service (capacity update) → Kafka → booking-service (`promoteFromWaitingList(eventTermId, addedSlots)`) → `WaitlistPromoted` per promotion → notification-service

### What works today

- Producer: `EventTermCommandService.updateEventTermCapacity` validates max ≥ confirmed count (sync call to booking-service) and publishes `CapacityIncreased` with `addedSlots`
- `promoteFromWaitingList` exists in `BookingCommandService`
- notification-service already consumes `waitlist-promoted`

### Tasks — **this is the explicitly documented missing consumer**

1. booking-service: add `CapacityIncreasedConsumer` (`@KafkaListener` on `holiday-planner.event.capacity-increased`, group `booking-service`) that deserializes `CapacityIncreasedPayload` (already in `shared/`) and calls `promoteFromWaitingList(eventTermId, addedSlots)`
2. Make promotion idempotent / order-stable (depends on Story 2 task 2)
3. Tests: consumer unit test + e2e assertion in `BookingWaitlistFlowIT` (increase capacity → waitlisted booking becomes CONFIRMED)

### Acceptance criteria

- Raising max from 2→4 on a term with 3 waitlisted bookings promotes exactly 2 (FIFO) and publishes 2 `WaitlistPromoted` events

---

## Story 6 — Day-Before Caregiver Notification (`ParticipantListRequested`)

> *As a caregiver, I want to receive the participant list as a PDF the day before the event, so I know who is coming.*

**Topic:** `holiday-planner.event.participant-list-requested`
**Flow:** event-service scheduler → Kafka → notification-service → booklet-service (SYNC PDF) → email with attachment

### What works today

- Scheduler (`DayBeforeNotificationsJob`, 02:15 daily) finds terms starting tomorrow, fetches participant names from booking-service, publishes one event per term/caregiver
- notification-service consumes the topic → `notifyCaregiverWithParticipants`
- booklet-service `POST /api/booklets/participant-list` generates a **real** PDF (PDFBox)

### Tasks

1. **PDF integration** — notification-service currently sends a plain-text name list; it never calls booklet-service. Add a `BookletServiceClient` and attach the generated PDF (`MimeMessage` with attachment instead of `SimpleMailMessage`). Mind open-question #5 (PDF size/timeout — set a sane client timeout)
2. Depends on Story 10 (real email send) for the attachment to matter
3. Tests: consumer test + booklet client contract test; PDF generation itself has no tests in booklet-service either

### Acceptance criteria

- For a term starting tomorrow with caregiver assigned, the caregiver email contains a PDF attachment listing all CONFIRMED participants

---

## Story 7 — Remove Family Member (Guard Check)

> *As a parent, I want to remove a family member from my profile — unless they still have active bookings.*

**Flow:** identity-service → booking-service (SYNC veto) → delete or `409`

### What works today

- `DELETE /api/identity/family-members/{memberId}` exists but **deletes unconditionally** (the veto is a `TODO` comment in `IdentityCommandService.removeFamilyMember`)
- booking-service has the repository query `findActiveBookingsByFamilyMember` — but no service method and no endpoint

### Tasks

1. booking-service: expose `GET /api/bookings/family-member/{memberId}/has-active` (`hasActiveBookings`) using the existing repo query (CONFIRMED or WAITLISTED)
2. identity-service: call it in `removeFamilyMember` (the `BookingServiceClient` already exists); on `true` → `409 Conflict`; on booking-service **unavailable** → fail safe and reject the deletion
3. Tests on both sides (incl. the fail-safe path)

### Acceptance criteria

- Removing a member with a WAITLISTED booking returns `409` and the member still exists
- Removing a member with only CANCELLED bookings succeeds

---

## Story 8 — Delete Organization (Saga)

> *As an admin, I want to delete an organization so that all its events, terms, and dependent data are cleaned up consistently.*

**Documented design:** `docs/delete-organization-saga.md` (Kafka choreography: `OrganizationDeletionStarted` → services clean up → `OrganizationDeleted`; org states `DELETING`/`DELETED`)

### What works today

- `DELETE /api/organizations/{id}`: **synchronous** REST cascade — calls event-service `DELETE /api/events/organization/{id}`, then hard-deletes the org row. No Kafka, no states, no compensation, no booking/payment cleanup

### Tasks (decide scope first: keep sync cascade *documented as such*, or implement the saga)

If implementing the documented saga:

1. Add `status` (`ACTIVE`/`DELETING`/`DELETED`) to `Organization`; publish `OrganizationDeletionStarted`
2. event-service consumer: cancel/delete events & terms (reuses Story 4 cascade for active terms)
3. booking/payment: cascade via the resulting `EventTermCancelled`/`BookingCancelled` events
4. Completion event `OrganizationDeleted`; org marked `DELETED`
5. Alternatively (smaller): keep the sync cascade but update `delete-organization-saga.md` to match reality

### Acceptance criteria

- Deleting an org with active terms leaves no orphaned bookings/payments, and parents/caregivers are notified via the existing cancellation flows

---

## Story 9 — Authentication & Inter-Service Security (JWT)

> *As a user, I want to log in once and stay logged in; as the platform, services must authenticate each other consistently.*

### What works today

- `POST /api/auth/login` returns a JWT (24h, claims: userId, organizationId, roles, email); shared `JwtAuthenticationFilter` validates incoming tokens in all services
- Some services use `X-Service-Secret` (`ServiceAuthenticationFilter`) for machine-to-machine calls — but **not consistently** (identity-service doesn't register it)

### Tasks

1. **Token refresh** — no refresh token or `/refresh` endpoint exists; add refresh-token issuance + rotation (or document the 24h-expiry decision)
2. **Consistent inter-service auth** — pick one mechanism (service secret vs forwarded JWT) and apply it uniformly; register `ServiceAuthenticationFilter` in identity-service
3. **Ownership checks** — most identity endpoints accept any valid JWT for any `userId`/`memberId`; add `@PreAuthorize`/ownership validation; same for event-service term/remark endpoints
4. Move login out of the legacy `IdentityService` into the CQRS command service; delete dead duplicates (`IdentityEventProducer`, unused DTOs)
5. JWT tests (none exist)

### Acceptance criteria

- Expired token → `401` with a way to obtain a new token without re-entering credentials
- User A cannot read/modify user B's family members

---

## Story 10 — Real Email Delivery

> *As a parent or caregiver, I want to actually receive the notification emails.*

### What works today

- All 7 Kafka consumers and all notification methods exist and route correctly
- SMTP config + `JavaMailSender` are wired, **but `mailSender.send()` is never called** — emails are log-only ("Email delivery disabled"), and a unit test asserts exactly that

### Tasks

1. Enable real sending behind a config flag (e.g. `notification.email.enabled`), default off for tests, on in docker-compose (add a MailHog/Mailpit container for the demo)
2. Switch to `MimeMessage` to support the PDF attachment (Story 6)
3. Optional: use the already-included Thymeleaf dependency for HTML templates
4. Update `NotificationServiceTest` accordingly; add tests for the 4 untested consumers (`WaitlistPromoted`, `EventTermCancelled`, `ParticipantListRequested`, `PaymentRefunded`)

### Acceptance criteria

- With the flag on and Mailpit running, creating a booking produces a visible email in the Mailpit UI

---

## Cross-Cutting Backlog (not story-sized, distribute opportunistically)


| Item                                     | Where                                    | Notes                                                                                                                 |
| ---------------------------------------- | ---------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| Docker `DB_PORT` for org/payment         | docker-compose.yml                       | **Fixed** — added `DB_PORT: 5432`; was causing container crash on startup                                             |
| Tests target deprecated `BookingService` | booking-service                          | `BookingServiceIntegrationTest` autowires a class that is no longer a Spring bean — verify/fix (see test run results) |
| Contract test URL drift                  | booking-service                          | `EventServiceConsumerContractTest` stubs `/api/event-terms/{id}` but the client calls `/api/events/terms/{id}`        |
| Kafka publish failures swallowed         | all producers                            | No outbox/retry/DLQ — documented open question; at minimum log + metric                                               |
| Unused request DTOs                      | identity, organization, payment, booking | Controllers use `@RequestParam`; either adopt the DTOs or delete them                                                 |
| Generic `RuntimeException`s              | organization, identity                   | Replace with domain exceptions + `GlobalExceptionHandler` mappings                                                    |
| No coverage tooling                      | parent pom                               | Add JaCoCo to the parent `pom.xml`                                                                                    |
| booklet-request-service                  | standalone                               | In-memory only, no security, no tests — decide whether it stays a CQRS demo or gets persisted                         |


---

## Test Run Results

### How to reproduce

```bash
# 1. Start infrastructure + services (JDK 21 for Maven tests)
docker compose up -d

# 2. Unit/component tests (exclude e2e)
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home
mvn test -pl '!e2e-tests' -am --fail-at-end

# 3. End-to-end user-story test (requires all services healthy on 8081–8085)
mvn verify -pl e2e-tests -Pe2e
```

> **JDK:** Pin `JAVA_HOME` to JDK 21. Homebrew's default (JDK 25) breaks Mockito/ByteBuddy in unit tests.
>
> **E2E profile:** `e2e-tests` uses Failsafe, not Surefire — tests are skipped unless you pass `-Pe2e` (sets `skipITs=false`).

### Docker-compose fix applied (2026-06-10)

`organization-service` and `payment-service` were crashing inside Docker because they connected to `postgres:5433` (host-mapped port) instead of `postgres:5432` (internal network port). `booking-service`, `event-service`, and `identity-service` already had `DB_PORT: 5432`; org and payment did not.

**Fix:** added `DB_PORT: 5432` to both services in `[docker-compose.yml](../docker-compose.yml)`.

After the fix, all five core services respond `200` on their health endpoints. Kafka UI (`kafkaui` on port 5001) may still fail to start if another container already binds that port — it is not required for e2e.

---

### E2E run — `BookingWaitlistFlowIT` (PASS)

**Command:** `mvn verify -pl e2e-tests -Pe2e` with full docker stack running.
**Result:** 1 test, 0 failures (~6 s).

This is the only automated test that exercises a **cross-service user story** end-to-end. It runs against the **published Docker images** (`ghcr.io/planet-webarchitekturen/`*), not locally compiled code.

#### Flow exercised (maps to stories)


| Step                                                 | Services         | Story | Verified?                                                        |
| ---------------------------------------------------- | ---------------- | ----- | ---------------------------------------------------------------- |
| Create organization (`bookingStartTime` in the past) | organization     | 1     | Yes — org created; window not *rejected* (test uses open window) |
| Register user + login JWT                            | identity         | 9     | Yes — register + login returns token                             |
| Add two family members                               | identity         | —     | Yes                                                              |
| Create event + term, activate term                   | event            | —     | Yes                                                              |
| Book Anna → CONFIRMED                                | booking, event   | 1     | Yes                                                              |
| Kafka → payment created (PENDING)                    | payment, booking | 1     | Yes — async `BookingCreated` consumer works                      |
| Book Ben → WAITLISTED                                | booking          | 1     | Yes — capacity=1 enforced                                        |
| Cancel Anna's booking                                | booking          | 2     | Yes                                                              |
| Ben promoted CONFIRMED                               | booking          | 2     | Yes — internal `promoteFromWaitingList` on cancel                |
| Term booking list shows CANCELLED + CONFIRMED        | booking          | 2     | Yes                                                              |


#### What e2e does NOT cover (stories still open)


| Story                 | Gap — not tested by e2e                                               |
| --------------------- | --------------------------------------------------------------------- |
| 1 Create Booking      | Age verification, booking-window *rejection*, duplicate-booking guard |
| 2 Cancel Booking      | 3-day user deadline, `cancelledBy` enum alignment                     |
| 3 Refund Payment      | Auto-refund on cancel, status pre-conditions                          |
| 4 Term Cancellation   | `EventTermCancelled` saga, auto-cancel scheduler                      |
| 5 Capacity Increase   | `capacity-increased` Kafka consumer (not in this flow)                |
| 6 Caregiver PDF       | Scheduler + booklet-service integration                               |
| 7 Family Member veto  | `hasActiveBookings` guard                                             |
| 8 Delete Organization | Saga / cascade                                                        |
| 10 Email delivery     | Real SMTP (stubbed in notification-service)                           |


---

### Unit/component run — `mvn test -pl '!e2e-tests' -am` (partial)

First run: Postgres **down** → identity/org/payment `contextLoads` failed.
Second run: Postgres **up** on `localhost:5433` → identity, organization, payment `contextLoads` all **pass**.


| Module                  | Result                 | Tests | Passed | Failed/Error |
| ----------------------- | ---------------------- | ----- | ------ | ------------ |
| shared                  | PASS                   | —     | —      | —            |
| booking-service         | **FAIL**               | 57    | 16     | 41           |
| booklet-service         | PASS                   | 1     | 1      | 0            |
| booklet-request-service | PASS (no tests)        | 0     | 0      | 0            |
| event-service           | **FAIL**               | 16    | 12     | 4            |
| identity-service        | PASS *(with Postgres)* | 1     | 1      | 0            |
| notification-service    | PASS                   | 7     | 7      | 0            |
| organization-service    | PASS *(with Postgres)* | 3     | 3      | 0            |
| payment-service         | PASS *(with Postgres)* | 4     | 4      | 0            |


### Unit-test failure root causes (unchanged)

1. **Security refactor not reflected in tests — 20 failures (booking 16, event 4).** MockMvc tests get `401` without JWT. *Fix: test auth helper or `@WithMockUser`.*
2. **Deprecated `BookingService` still targeted — 15 errors.** Integration tests autowire a removed bean; unit tests NPE on missing `BookingEventProducer`. *Fix: rewrite against CQRS services.*
3. **Contract test URL drift — 7 failures/errors.** WireMock stubs `/api/event-terms/{id}`; client calls `/api/events/terms/{id}`.
4. **MockMvc URI-template bug — 2 errors** in cancel-booking tests.

### What tests verify vs. what e2e verifies


| Layer                                  | Confirms                                                                                            |
| -------------------------------------- | --------------------------------------------------------------------------------------------------- |
| **E2E (`BookingWaitlistFlowIT`)**      | Stories 1+2 happy path across 5 services: booking, waitlist, cancel→promote, Kafka payment creation |
| **event-service unit (12 pass)**       | Term transitions, cancellation saga publish, capacity-increase publish                              |
| **notification-service unit (7 pass)** | Kafka consumer routing; email delivery explicitly **disabled**                                      |
| **payment-service unit (4 pass)**      | Manual refund event, composition query; *not* auto-refund on cancel                                 |
| **booking-service unit (16 pass)**     | Legacy capacity/waitlist logic only — 41 other tests red                                            |


### Implication for the stories

- **E2E green** confirms the core booking/waitlist/payment choreography works in the deployed stack — good news for Stories 1 and 2 baseline behaviour.
- **E2E does not replace** the missing pieces listed above; those stories remain Partial/Missing per the audit.
- **booking-service unit tests** are still broken (41/57 red) — fix before relying on CI for Stories 1, 2, 5.
- **Run e2e in CI** with `mvn verify -pl e2e-tests -Pe2e` after `docker compose up -d`; document the `DB_PORT` fix so org/payment don't regress.

---

## Suggested Distribution


| Team member   | Stories                                                                             |
| ------------- | ----------------------------------------------------------------------------------- |
| Jan           | 1 (Create Booking integrity) + 7 (Family member veto) — both touch booking+identity |
| Samir, Amir   | 3 (Refunds) + 2 (Cancellation rules) — payment/booking pair                         |
| Muhi, Tarik   | 5 (Capacity consumer) + 4 (Scheduler tests) — Kafka-focused                         |
| Fabian, Aleks | 6 (PDF notification) + 10 (Email delivery) — notification/booklet pair              |
| Büsra, Denise | 9 (Auth/JWT) + 8 (Delete-org saga decision)                                         |


