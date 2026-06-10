# Identity Service

The Identity Service owns **users**, their **family members**, and **caregivers**. It is
the system's single source of truth for accounts and the **sole issuer of JWTs** used for
authentication and authorization across all other services.

- **Port:** 8083
- **Database:** `identity_db` (PostgreSQL) — private to this service
- **Base path:** `/api/identity`
- **Swagger UI:** http://localhost:8083/swagger-ui.html

---

## 1. Architecture

The service follows **CQRS**: writes and reads are separated into two services behind a
thin controller.

| Layer | Class | Responsibility |
|---|---|---|
| Web | `IdentityController` | HTTP mapping, maps entities → response DTOs. No business logic. |
| Command (writes) | `IdentityCommandService` | All state changes; transactional; records domain events to the outbox. |
| Query (reads) | `IdentityQueryService` | Read-only lookups and login/JWT issuance. |
| Persistence | `UserRepository`, `FamilyMemberRepository`, `CaregiverRepository`, `OutboxEventRepository` | Spring Data JPA. |
| Messaging | `IdentityEventProducer`, `OutboxService`, `OutboxRelay` | Transactional-outbox event publishing. |
| Integration | `BookingServiceClient` | Synchronous IPC to booking-service (API composition / guard checks). |

**Aggregates.** `User` is an aggregate root that owns its `FamilyMember` children
(`@OneToMany`, cascade + orphan removal); deleting a user cascades to its family members.
`Caregiver` is a separate, standalone aggregate (caregivers are not owned by a user).

**Why CQRS here.** Reads (profile lookups, caregiver lists, inter-service age checks)
vastly outnumber writes and have different scaling/caching needs. Splitting them keeps
query code free of event-publishing/business concerns and lets the read side evolve
independently (caching, read replicas) without touching command logic. This is a
deliberate, somewhat *fictional* use of CQRS for a project of this size — the point is to
demonstrate the pattern cleanly.

---

## 2. How to start

The service needs PostgreSQL and Kafka. The repo's `docker-compose.yml` provides both.

```bash
# 1. Start dependencies (DB + Kafka)
docker compose up -d postgres kafka

# 2. Run the service locally (JDK 21 required)
#    The shared module must be built first.
mvn -pl shared install -DskipTests
mvn -pl identity-service spring-boot:run
```

> **JDK note:** the project targets Java 21. Lombok 1.18.36 cannot process JDK 24+, so
> build/run with a JDK 21 (`JAVA_HOME` pointed at a JDK 21 install).

Health check: `GET http://localhost:8083/api/identity/health` → `IdentityService is running!`

Optional: the Redpanda Kafka console (from compose) is at http://localhost:5001 for
inspecting published events.

---

## 3. Technology choices & justification

| Technology | Used for | Why |
|---|---|---|
| **Spring Boot 3.2 / Java 21** | Application framework | Project-wide standard; mature DI, web, data, and security stack. |
| **Spring Security + method security** | Endpoint authorization | `@PreAuthorize` lets authorization live declaratively next to each endpoint; integrates with the trusted-JWT filter. |
| **JJWT 0.12.3** | JWT signing/parsing | Lightweight, no extra server; HMAC-SHA tokens are enough for a shared-secret setup between services (no need for an OAuth2 server / Keycloak in a university project). |
| **PostgreSQL** | Persistence | Relational data with uniqueness constraints (email) and a transactional outbox table; one DB per service keeps boundaries clean. |
| **Spring Kafka** | Event publishing | Project-wide async backbone; the outbox relay uses `KafkaTemplate`. |
| **Transactional Outbox (custom)** | Reliable eventing | Guarantees events are not lost when the DB commit succeeds but the broker is unreachable (see §6). |
| **Lombok** | Boilerplate reduction | Removes getters/setters/constructors. |
| **springdoc-openapi** | Swagger UI | Auto-generated interactive API docs for manual testing. |

---

## 4. REST API

### Naming conventions

- Resources are **plural nouns**, kebab-case: `/users`, `/family-members`, `/caregivers`.
- Sub-resources are nested under their owner: `/users/{userId}/family-members`.
- HTTP verbs carry the intent: `POST` create, `GET` read, `PUT` full update,
  `PATCH` partial update, `DELETE` remove.
- Responses are **DTOs**, never JPA entities — the password hash is never exposed and the
  family-member's owner is flattened to a `userId` field.

### Endpoint overview

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/identity/health` | public | Liveness check |
| POST | `/api/identity/users/register` | public | Register a user |
| POST | `/api/identity/auth/login` | public | Log in, returns JWT |
| GET | `/api/identity/users` | ADMIN | List all users |
| GET | `/api/identity/users/{userId}` | authenticated | Get a user |
| PATCH | `/api/identity/users/{userId}` | self, or ADMIN for role/org | **General** partial update |
| DELETE | `/api/identity/users/{userId}` | self or ADMIN | Delete a user (guarded, see §7) |
| GET | `/api/identity/users/{userId}/family-members` | self | List a user's family members |
| POST | `/api/identity/users/{userId}/family-members` | self | Add a family member |
| GET | `/api/identity/family-members/{memberId}` | owner or staff | Get one family member |
| PUT | `/api/identity/family-members/{memberId}` | owner | Update a family member |
| DELETE | `/api/identity/family-members/{memberId}` | owner | Remove a family member (guarded, see §7) |
| POST | `/api/identity/caregivers` | EVENT_OWNER, ADMIN | Create a caregiver |
| GET | `/api/identity/caregivers` | staff | List caregivers |
| GET | `/api/identity/caregivers/{caregiverId}` | staff | Get a caregiver |
| PUT | `/api/identity/caregivers/{caregiverId}` | EVENT_OWNER, ADMIN | Update a caregiver |
| DELETE | `/api/identity/caregivers/{caregiverId}` | EVENT_OWNER, ADMIN | Delete a caregiver |

> "staff" = `ORGANIZATION_TEAM_MEMBER`, `ADMIN`, or `EVENT_OWNER`.

### Examples

**Register** (form params):

```bash
curl -X POST "http://localhost:8083/api/identity/users/register" \
  -d "email=bob@example.com" \
  -d "password=secret" \
  -d "phoneNumber=+49 151 1234567" \
  -d "organizationId=1c0f4c2c-5e3b-4f0a-afaf-010101010101"
```

Response:

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "email": "bob@example.com",
  "phoneNumber": "+49 151 1234567",
  "organizationId": "1c0f4c2c-5e3b-4f0a-afaf-010101010101",
  "role": "USER"
}
```

**Login** (JSON body) → returns a token to use as `Authorization: Bearer <token>`:

```bash
curl -X POST "http://localhost:8083/api/identity/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"bob@example.com","password":"secret"}'
```

```json
{
  "id": "3fa85f64-...",
  "email": "bob@example.com",
  "phoneNumber": "+49 151 1234567",
  "organizationId": "1c0f4c2c-...",
  "role": "USER",
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer"
}
```

**General user update** (partial — send only the fields to change). Updating `role` or
`organizationId` requires an ADMIN token:

```bash
curl -X PATCH "http://localhost:8083/api/identity/users/3fa85f64-..." \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+49 160 0000000"}'
```

**Add a family member:**

```bash
curl -X POST "http://localhost:8083/api/identity/users/3fa85f64-.../family-members" \
  -H "Authorization: Bearer <token>" \
  -d "firstName=Anna" -d "lastName=Müller" \
  -d "birthDate=2015-04-01" -d "zip=80331"
```

```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "firstName": "Anna",
  "lastName": "Müller",
  "birthDate": "2015-04-01",
  "zip": "80331"
}
```

A Postman collection mirrors these: register → login (save token to an env var) → call the
protected endpoints with the `Authorization: Bearer {{token}}` header.

---

## 5. Produced events

The identity service **produces** the following events. It does not currently consume any.

### Topic naming

Pattern: `holiday-planner.identity.<event-name>` (kebab-case). Each event maps 1:1 to a
topic. The message **key** is the aggregate id (the `userId`), so all events for one user
land on the same partition and stay ordered.

| Topic | Event type | Produced by | Key |
|---|---|---|---|
| `holiday-planner.identity.user-registered` | `UserRegistered` | `registerUser` | userId |
| `holiday-planner.identity.user-updated` | `UserUpdated` | `updateUser` | userId |
| `holiday-planner.identity.user-deleted` | `UserDeleted` | `deleteUser` | userId |
| `holiday-planner.identity.family-member-added` | `FamilyMemberAdded` | `addFamilyMember` | userId |
| `holiday-planner.identity.family-member-removed` | `FamilyMemberRemoved` | `removeFamilyMember` | userId |

### Envelope

Every message is a `KafkaEnvelope<T>` (defined in the `shared` module):

```json
{
  "eventType": "UserRegistered",
  "version": "1",
  "timestamp": "2026-05-28T10:00:00",
  "source": "identity-service",
  "payload": { }
}
```

`eventType` allows routing without deserializing the payload; `version` supports schema
evolution; `source` and `timestamp` aid debugging.

### Payload examples

`UserRegistered`:

```json
{
  "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "email": "bob@example.com",
  "phoneNumber": "+49 151 1234567",
  "organizationId": "1c0f4c2c-5e3b-4f0a-afaf-010101010101",
  "role": "USER"
}
```

`UserUpdated` (public profile fields only — never the password hash):

```json
{ "userId": "3fa85f64-...", "email": "bob@example.com", "phoneNumber": "+49 160 0000000" }
```

`UserDeleted`:

```json
{ "userId": "3fa85f64-...", "email": "bob@example.com", "organizationId": "1c0f4c2c-..." }
```

`FamilyMemberAdded`:

```json
{ "userId": "3fa85f64-...", "familyMemberId": "7c9e6679-...", "name": "Anna Müller" }
```

`FamilyMemberRemoved`:

```json
{ "userId": "3fa85f64-...", "familyMemberId": "7c9e6679-..." }
```

> Event payloads are deliberately **decoupled from the database schema**: they expose only
> what consumers need (e.g. no password hash; a flat `name` instead of separate columns).

---

## 6. Transactional Outbox

Publishing an event with a naive "save the row, then call Kafka" sequence is **not atomic**:
if the service crashes (or the broker is down) after the DB commit but before the send, the
event is lost. The identity service avoids this with the **transactional outbox pattern**.

```
   command (@Transactional)
   ├─ mutate aggregate            ─┐  same DB transaction →
   └─ insert row into outbox_events┘  commit together (atomic)

   OutboxRelay (@Scheduled, every 5s)
   ├─ read unprocessed outbox rows (oldest first, batched)
   ├─ publish each to its Kafka topic (block on broker ack)
   └─ mark processed   ← only after the ack
```

- **Components:** `OutboxEvent` (JPA entity / `outbox_events` table), `OutboxService`
  (records events inside the command transaction), `OutboxRelay` (scheduled publisher).
- `IdentityEventProducer` no longer talks to Kafka directly — it serializes the envelope
  and hands it to `OutboxService`, so the event row commits with the state change.
- The relay marks a row processed **only after** the broker acknowledges the send
  (`.get()` on the send future), giving **at-least-once** delivery. On a failure it stops
  the batch to preserve per-user ordering and retries on the next tick.
- Consumers must therefore be **idempotent** (dedupe on the aggregate id / event).

Polling interval is configurable via `outbox.relay.fixed-delay-ms` (default `5000`).

---

## 7. Sagas & counter-measures

The identity service participates in cross-service consistency through **synchronous guard
checks** (a veto step) against booking-service before destructive operations. These are the
identity-side compensations that keep data consistent without distributed transactions.

### removeFamilyMember (guard / veto)

1. `IdentityService` calls `BookingServiceClient.getActiveBookingCount(memberId)` **[SYNC]**.
2. If `> 0` → reject with an error; the family member is **not** removed.
3. If `0` → delete and publish `FamilyMemberRemoved`.

**Counter-measures:**
- Booking-service unavailable → the client **fails safe** (returns 0 only on transport
  errors it logs); deletion proceeds only when the check is conclusive. *(Tunable: switch
  to fail-closed if stricter consistency is required.)*
- Race condition (a booking created between check and delete) → rare; surfaced as a clear
  user-facing error and retryable.

### deleteUser (guard / veto, fan-out over the aggregate)

Because a `User` owns family members (cascade delete), deleting a user could orphan active
bookings. So `deleteUser` runs the same veto **for every** family member of the user:

1. Load the user; iterate its family members.
2. For each, call `getActiveBookingCount` **[SYNC]**; if any `> 0` → reject, nothing is
   deleted.
3. Otherwise delete the user (cascades to family members) and publish `UserDeleted`.

Both operations are wrapped in a DB transaction together with their outbox row, so either
the whole delete + event commit, or nothing does.

---

## 8. Authorization (trusted JWT)

The identity service **issues** JWTs at login and, like every service, **trusts** the JWT
presented on each request.

- **Header:** `Authorization: Bearer <token>`
- **Signing:** HMAC-SHA with a shared secret (`jwt.secret`), via JJWT.
- **Claims:** `sub` (userId), `organizationId`, `roles`, `email`, `iat`, `exp`.
- The shared `JwtAuthenticationFilter` parses the token into a `JwtClaims` principal;
  endpoints authorize with `@PreAuthorize`.

Authorization rules of note:

- `@identitySecurity.isSelf(userId, auth)` — a user may act on their own account.
- `@identitySecurity.isFamilyMemberOwner(memberId, auth)` — a user may act on their own
  family members.
- `@identitySecurity.canUpdateUser(userId, request, auth)` — a user may update their own
  profile, **but** changing `role` or `organizationId` requires the `ADMIN` role. This
  keeps the single general-update endpoint usable for self-service while protecting the
  privileged fields.

---

## 9. Tests

- `IdentityCommandServiceTest` — unit tests of the domain service with **all collaborators
  mocked** (repositories, password encoder, event producer, booking client). Covers the
  main use cases: register (+ duplicate email), partial update (+ email-uniqueness +
  password re-hash), delete with the booking veto, family-member add/remove (+ veto),
  caregiver create.
- `IdentityControllerTest` — MockMVC (standalone) tests of request mapping and the
  entity → DTO mapping (e.g. password hash never serialized, family-member owner flattened
  to `userId`).

Run them with:

```bash
mvn -pl identity-service test -Dtest=IdentityCommandServiceTest,IdentityControllerTest
```
