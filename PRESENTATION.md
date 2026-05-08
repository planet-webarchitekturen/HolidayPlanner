# Holiday Planner — Project Overview

## What We Built

A backend for a municipal holiday programme platform where parents register children for holiday events. The system is built as a **Java 21 / Spring Boot 3.2 microservices monorepo** deployed via Docker Compose, with PostgreSQL for persistence and Apache Kafka for asynchronous messaging.

---

## Architecture

### Services

| Service | Port | Responsibility |
|---|---|---|
| **identity-service** | 8083 | User accounts, login, JWT issuance, family members, caregivers |
| **event-service** | 8081 | Events (templates) and event terms (occurrences), caregiver assignment |
| **booking-service** | 8082 | Bookings, waitlist, cancellation, capacity tracking |
| **organization-service** | 8084 | Municipalities/organisations, team members, sponsors |
| **payment-service** | 8085 | Payment records (PENDING → PAID → REFUNDED) |
| **notification-service** | 8090 | Email notifications triggered by Kafka events |
| **booklet-service** | 8087 | Generates participant booklets from booking and event data |

A **shared Maven module** (`shared/`) contains domain models, Kafka payload DTOs, and security utilities shared across all services. No service duplicates these classes.

### Infrastructure

- **PostgreSQL 15** — one database per service (isolated schemas), initialised by `docker/init-databases.sh`
- **Apache Kafka 4.2 (KRaft mode)** — no ZooKeeper; runs as a single broker with both broker and controller roles
- **Redpanda Console** — Kafka UI at port 5001 for inspecting topics and messages during development

---

## Domain Model

The core design decision is the **Event / EventTerm split**:

- An **Event** is a template — it holds everything that does not change between runs: title, location, age limits, price, payment method.
- An **EventTerm** is a concrete occurrence of that event with specific start/end times, min/max participants, and a lifecycle status (`DRAFT` → `ACTIVE` → `CANCELLED`).

This allows an organisation to create one Event and schedule it across multiple weeks without duplicating content.

**Other key entities:** `Organization`, `TeamMember`, `Sponsor`, `User`, `FamilyMember`, `Caregiver`, `Booking`, `Payment`.

---

## Patterns Implemented

### 1. CQRS (Command Query Responsibility Segregation)

Applied in **booking-service**, **organization-service**, and **identity-service**. Each service splits its logic into:

- `*CommandService` — all writes, state changes, Kafka publishing
- `*QueryService` — all reads, composition queries
- The controller routes `POST`/`DELETE`/`PATCH` to commands and `GET` to queries

This prevents Kafka producers from accidentally firing in read paths and makes the services independently testable.

### 2. Composition Queries (API Gateway Pattern, service-local)

Rather than forcing the frontend to call multiple services, certain GET endpoints assemble data from several sources internally and return one enriched response.

Examples:
- `GET /api/bookings/family-member/{id}/details` — booking rows from booking-service DB + event name, location, dates from event-service
- `GET /api/bookings/event-term/{id}/summary` — confirmed/waitlisted counts from booking-service + capacity from event-service, with `availableSpots` and `isFull` computed on the fly
- `GET /api/organizations/{id}/overview` — organisation + all team members enriched with user details from identity-service

Graceful degradation is built in: if the downstream service is unavailable, the response still returns what is local and fills remote fields with `null` rather than failing.

### 3. Event-Driven Communication via Kafka

Services communicate asynchronously through domain events. Every message uses a typed envelope:

```json
{
  "eventType": "BookingCreated",
  "version": "1",
  "timestamp": "2026-05-01T10:00:00",
  "source": "booking-service",
  "payload": { ... }
}
```

Key flows:
- `booking.created` → notification-service sends confirmation email
- `booking.cancelled` → notification-service notifies parent
- `event.term-cancelled` → booking-service cancels all bookings for that term and triggers refunds
- `event.capacity-increased` → booking-service promotes waitlisted bookings
- `organization.created` → event-service registers the new organisation
- `payment.refunded` → notification-service notifies parent

Topic naming convention: `holiday-planner.<service>.<event-name>`

### 4. JWT Authentication (Symmetric, Shared Secret)

All services validate tokens independently using a shared HMAC secret (`HS384`). The identity-service is the sole issuer.

Token claims: `sub` (userId), `organizationId`, `roles`, `email`, `iat`, `exp` (24 h).

The `JwtAuthenticationFilter` (in the shared module) is a Spring Security `OncePerRequestFilter` registered in every service's `SecurityFilterChain`. It reads `Authorization: Bearer <token>`, validates the signature, and populates the `SecurityContextHolder`. Invalid tokens return 401 immediately; missing tokens pass through to the authorisation layer.

### 5. Role-Based Access Control (RBAC)

Five roles enforced via Spring Security's `@PreAuthorize`:

| Role | Capabilities |
|---|---|
| `ADMIN` | Create/delete organisations, manage the full platform |
| `EVENT_OWNER` | Create and manage events and event terms |
| `ORGANIZATION_TEAM_MEMBER` | Manage team, sponsors, day-to-day operations |
| `ACCOUNTANT` | Process and refund payments |
| `USER` | Book events for family members |

### 6. Organisation-Scoped Access Control

Every write operation checks that the JWT's `organizationId` matches the resource being modified. For example:

- A booking can only be created if the event term's organisation matches the user's organisation.
- An event can only be updated by someone in the same organisation that owns it.
- A payment can only be marked paid/refunded by someone in the organisation that raised it.

This is enforced in the command service layer via `SecurityUtils.getCurrentOrganizationId()`, not at the HTTP layer, so it applies regardless of which endpoint is called.

### 7. Service-to-Service Authentication (X-Service-Secret)

Some operations have no user context — for example, when organisation-service deletes an organisation it must instruct event-service to delete all associated events. There is no user JWT to pass.

Solution: a shared secret header `X-Service-Secret`. The calling service includes the header; the receiving service's `ServiceAuthenticationFilter` (also in the shared module) validates it and grants `ROLE_SERVICE` authority. Internal endpoints are protected with `@PreAuthorize("hasRole('SERVICE')")` and are unreachable without the correct secret.

This keeps service-to-service calls simple without introducing OAuth2 client credentials or a service mesh.

---

## Security Summary

```
Request → ServiceAuthenticationFilter (X-Service-Secret?)
        → JwtAuthenticationFilter (Bearer token?)
        → Spring Security AuthorizationFilter (path rules)
        → @PreAuthorize (role check)
        → Service method (org-scope check)
```

Public endpoints (health, login, register) are explicitly `permitAll()` before the filter chain. Everything else requires at minimum a valid JWT.

---

## Inter-Process Communication Patterns Used

| Pattern | Where | Why |
|---|---|---|
| **Synchronous REST (RestClient)** | booking-service → event-service (event term lookup) | Need immediate response to decide booking status |
| **Synchronous REST** | organization-service → event-service (delete cascade) | Transactional cleanup must complete before org deletion returns |
| **Async Kafka** | booking/event/payment → notification-service | Fire-and-forget; email delivery does not block the user response |
| **Async Kafka** | event-service → booking-service (term cancelled) | Decouples event ownership from booking side-effects |

---

## Team & Ownership

| Team | Services |
|---|---|
| Büsra Aydemir + Denise Müller | identity-service |
| Amir Hodzic + Samir Hodzic | event-service |
| Muhammed Güzel + Tarik Pasalic | booking-service |
| Jan Burtscher + Aleksander Lukis | organization-service, payment-service |
| Fabian Türtscher | notification-service, booklet-service |
