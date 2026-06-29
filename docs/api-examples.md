# API Examples

Selected REST endpoints (OpenAPI-style) and domain event schemas for the Holiday Planner system.

---

## BookingService (port 8082)

### POST /api/bookings — Create a booking

```yaml
POST /api/bookings:
  summary: Book an event term for a family member
  description: >
    Inputs are sent as query or form parameters (application/x-www-form-urlencoded),
    NOT a JSON body. Requires a USER, EVENT_OWNER, ORGANIZATION_TEAM_MEMBER or ADMIN token.
  parameters:
    - { name: familyMemberId, in: query, required: true, schema: { type: string, format: uuid } }
    - { name: eventTermId,    in: query, required: true, schema: { type: string, format: uuid } }
  responses:
    '200':
      description: Booking created (CONFIRMED or WAITLISTED)
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/BookingResponse'
          example:
            id: "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed"
            familyMemberId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
            eventTermId: "7c9e6679-7425-40de-944b-e07fc1f90ae7"
            status: "CONFIRMED"
            bookedAt: "2026-06-01T10:00:00"
    '400':
      description: Family member is outside the event's age range (minimalAge/maximalAge)
    '404':
      description: Event term not found
    '409':
      description: >
        Term not active, booking window not yet open (before the organization's bookingStartTime),
        or a duplicate booking already exists for the same family member + term.
        (A full term is NOT a 409 — it returns 200 with status WAITLISTED.)
```

### DELETE /api/bookings/{bookingId} — Cancel a booking (by user)

```yaml
DELETE /api/bookings/{bookingId}:
  summary: Cancel a booking (by the parent, up to 3 days before event)
  parameters:
    - name: bookingId
      in: path
      required: true
      schema:
        type: string
        format: uuid
  responses:
    '200':
      description: Booking cancelled (returns the updated BookingResponse with status CANCELLED)
    '409':
      description: A USER cancelled less than 3 days before the event start (owners/admins bypass)
    '404':
      description: Booking not found
```

### GET /api/bookings/family-member/{familyMemberId}/has-active — Active-booking check (veto support)

```yaml
GET /api/bookings/family-member/{familyMemberId}/has-active:
  summary: True if the member has any CONFIRMED/WAITLISTED booking. Used by identity-service's
    family-member removal veto (inter-service, X-Service-Secret).
  responses:
    '200':
      description: Boolean (true = has active bookings)
      content:
        application/json:
          schema: { type: boolean }
          example: true
```

### GET /api/bookings/event-term/{eventTermId} — Get bookings for a term

```yaml
GET /api/bookings/event-term/{eventTermId}:
  summary: Get all bookings for an event term (Event Owner only)
  parameters:
    - name: eventTermId
      in: path
      required: true
      schema:
        type: string
        format: uuid
  responses:
    '200':
      description: List of bookings
      content:
        application/json:
          schema:
            type: array
            items:
              $ref: '#/components/schemas/BookingResponse'
```

---

## IdentityService (port 8083)

### POST /api/auth/register — Register a user

```yaml
POST /api/auth/register:
  summary: Register a new parent account
  description: >
    Also mapped at POST /api/identity/users/register. Fields are sent as query or form
    parameters (application/x-www-form-urlencoded), NOT a JSON body.
  parameters:
    - { name: email,          in: query, required: true, schema: { type: string } }
    - { name: password,       in: query, required: true, schema: { type: string } }
    - { name: phoneNumber,    in: query, required: true, schema: { type: string } }
    - { name: organizationId, in: query, required: true, schema: { type: string, format: uuid } }
  responses:
    '201':
      description: User registered
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/UserResponse'
    '409':
      description: Email already in use
```

### POST /api/auth/login — Login

```yaml
POST /api/auth/login:
  summary: Authenticate user and receive JWT
  requestBody:
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/LoginRequest'
        example:
          email: "parent@example.com"
          password: "s3cr3tP@ss"
  responses:
    '200':
      description: Authentication successful (returns both an access token and a refresh token)
      content:
        application/json:
          example:
            token: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
            refreshToken: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
            tokenType: "Bearer"
    '401':
      description: Invalid credentials
```

### POST /api/auth/refresh — Renew the access token (no credentials needed)

```yaml
POST /api/auth/refresh:
  summary: Exchange a valid refresh token for a fresh access token (+ rotated refresh token)
  requestBody:
    content:
      application/json:
        example:
          refreshToken: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  responses:
    '200':
      description: New access + refresh token (same LoginResponse shape)
    '500':
      description: Invalid or expired refresh token
```

### GET /api/identity/family-members/{memberId}/birth-date — Birth date (veto/age support)

```yaml
GET /api/identity/family-members/{memberId}/birth-date:
  summary: Family member's birth date, used by booking-service age verification
    (inter-service, X-Service-Secret or ORGANIZATION_TEAM_MEMBER/ADMIN/EVENT_OWNER).
  responses:
    '200':
      content:
        application/json:
          example:
            birthDate: "2018-03-15"
```

### POST /api/identity/users/{userId}/family-members — Add family member

```yaml
POST /api/identity/users/{userId}/family-members:
  summary: Add a family member (child) to a user's profile
  description: >
    firstName, lastName, birthDate and zip are sent as query or form parameters
    (application/x-www-form-urlencoded), NOT a JSON body.
  parameters:
    - name: userId
      in: path
      required: true
      schema:
        type: string
        format: uuid
    - { name: firstName, in: query, required: true, schema: { type: string } }
    - { name: lastName,  in: query, required: true, schema: { type: string } }
    - { name: birthDate, in: query, required: true, schema: { type: string, format: date } }
    - { name: zip,       in: query, required: true, schema: { type: string } }
  responses:
    '200':
      description: Family member added
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/FamilyMemberResponse'
```

---

## EventService (port 8081)

### GET /api/events?organizationId={id} — List events for main page

```yaml
GET /api/events:
  summary: Get all events for an organization (public, used on main page)
  parameters:
    - name: organizationId
      in: query
      required: true
      schema:
        type: string
        format: uuid
  responses:
    '200':
      description: List of events
      content:
        application/json:
          schema:
            type: array
            items:
              $ref: '#/components/schemas/EventResponse'
```

### POST /api/events/{eventId}/terms — Create an event term

```yaml
POST /api/events/{eventId}/terms:
  summary: Create a new event term (Event Owner only)
  parameters:
    - name: eventId
      in: path
      required: true
      schema:
        type: string
        format: uuid
  requestBody:
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/CreateEventTermRequest'
        example:
          startDateTime: "2026-07-15T09:00:00"
          endDateTime: "2026-07-15T17:00:00"
          minParticipants: 5
          maxParticipants: 20
  responses:
    '201':
      description: Event term created with DRAFT status
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/EventTermResponse'
```

### PATCH /api/events/terms/{eventTermId}/status — Change event term status

```yaml
PATCH /api/events/terms/{eventTermId}/status:
  summary: Change the status of an event term (DRAFT → ACTIVE or ACTIVE → CANCELLED)
  parameters:
    - name: eventTermId
      in: path
      required: true
      schema:
        type: string
        format: uuid
  requestBody:
    content:
      application/json:
        example:
          newStatus: "ACTIVE"
  responses:
    '200':
      description: Status updated
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/EventTermResponse'
    '400':
      description: Invalid status transition
```

---

## OrganizationService (port 8084)

### POST /api/organizations — Create organization (Admin only)

```yaml
POST /api/organizations:
  summary: Create a new municipality organization (ADMIN only)
  description: >
    name, bankAccount and bookingStartTime (optional) are sent as query or form parameters
    (application/x-www-form-urlencoded), NOT a JSON body.
  parameters:
    - { name: name,             in: query, required: true,  schema: { type: string } }
    - { name: bankAccount,      in: query, required: true,  schema: { type: string } }
    - { name: bookingStartTime, in: query, required: false, schema: { type: string, format: date-time } }
  responses:
    '200':
      description: Organization created (returns the Organization; teamMembers and sponsors are empty for a new org)
      content:
        application/json:
          example:
            id: "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
            name: "Gemeinde Bregenz"
            bankAccount: "AT12 3456 7890 1234 5678"
            bookingStartTime: "2026-06-01T08:00:00"
            teamMembers: []
            sponsors: []
    '409':
      description: Organization name already taken
```

### POST /api/organizations/{organizationId}/sponsors — Add sponsor

```yaml
POST /api/organizations/{organizationId}/sponsors:
  summary: Add a sponsor to an organization
  description: >
    name and amount (optional) are sent as query or form parameters
    (application/x-www-form-urlencoded), NOT a JSON body.
  parameters:
    - name: organizationId
      in: path
      required: true
      schema:
        type: string
        format: uuid
    - { name: name,   in: query, required: true,  schema: { type: string } }
    - { name: amount, in: query, required: false, schema: { type: number } }
  responses:
    '200':
      description: Sponsor added (returns the Sponsor)
      content:
        application/json:
          example:
            id: "d290f1ee-6c54-4b01-90e6-d701748f0851"
            name: "Raiffeisenbank Vorarlberg"
            amount: 500.00
```

---

## PaymentService (port 8085)

### PATCH /api/payments/{paymentId}/pay — Mark payment as paid

```yaml
PATCH /api/payments/{paymentId}/pay:
  summary: Mark a bank-transfer payment as received (Accountant only, org-scoped)
  parameters:
    - name: paymentId
      in: path
      required: true
      schema: { type: string, format: uuid }
    - name: note
      in: query
      required: false
      schema: { type: string }
      example: "Received 2026-07-10, bank reference #12345"
  responses:
    '200':
      description: Payment marked as PAID (returns the updated Payment)
      content:
        application/json:
          example:
            id: "f47ac10b-58cc-4372-a567-0e02b2c3d479"
            bookingId: "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed"
            organizationId: "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
            amount: 15.00
            status: "PAID"
            createdAt: "2026-07-01T09:00:00"
            paidAt: "2026-07-10T11:00:00"
            refundedAt: null
            note: "Received 2026-07-10, bank reference #12345"
            parentEmail: "parent@example.com"
            eventName: "Bicycle Tour"
    '409':
      description: Payment is not in PENDING status (e.g. already PAID or REFUNDED)
```

### PATCH /api/payments/{paymentId}/refund — Refund a payment

```yaml
PATCH /api/payments/{paymentId}/refund:
  summary: Refund a paid payment (Accountant only, org-scoped). Publishes PaymentRefunded.
  parameters:
    - name: paymentId
      in: path
      required: true
      schema: { type: string, format: uuid }
    - name: note
      in: query
      required: false
      schema: { type: string }
  responses:
    '200':
      description: >
        Payment marked REFUNDED. Idempotent — refunding an already-REFUNDED payment is a no-op
        and does NOT re-publish PaymentRefunded.
    '409':
      description: Payment is not in PAID status (cannot refund a PENDING payment)
```

### GET /api/payments/organization/{organizationId}/balance — Get balance

```yaml
GET /api/payments/organization/{organizationId}/balance:
  summary: Calculate the total confirmed income for an organization
  parameters:
    - name: organizationId
      in: path
      required: true
      schema:
        type: string
        format: uuid
  responses:
    '200':
      description: Balance returned as a bare decimal number (not an object)
      content:
        application/json:
          schema: { type: number, format: decimal }
          example: 2350.00
```

---

## BookletService (port 8087)

### GET /api/booklets/organizations/{organizationId} — Generate organization booklet

```yaml
GET /api/booklets/organizations/{organizationId}:
  summary: Generate a PDF booklet for an organization (ORGANIZATION_OWNER or ADMIN)
  description: >
    Takes no request body. The service gathers organization, team, sponsor and event data
    itself via its upstream service clients, then renders the PDF.
  parameters:
    - name: organizationId
      in: path
      required: true
      schema:
        type: string
        format: uuid
  responses:
    '200':
      description: PDF booklet returned as binary (Content-Disposition attachment)
      content:
        application/pdf:
          schema:
            type: string
            format: binary
```

---

## Domain Event Schemas

All events are published as JSON messages to **Apache Kafka**. The examples below show only the `payload`; in transit each payload is wrapped in the standard envelope (`eventType`, `eventId`, `version`, `timestamp`, `source`, `payload`). See [messaging-conventions.md](messaging-conventions.md) for the full envelope format and topic naming.

### BookingCreated

```json
{
  "eventType": "BookingCreated",
  "timestamp": "2026-06-01T10:00:00Z",
  "payload": {
    "bookingId": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
    "familyMemberId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "eventTermId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "status": "CONFIRMED",
    "parentEmail": "parent@example.com",
    "eventName": "Bicycle Tour",
    "termDate": "2026-07-15T09:00:00Z",
    "organizationId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
    "amount": 15.00
  }
}
```

### BookingCancelled

```json
{
  "eventType": "BookingCancelled",
  "timestamp": "2026-06-15T08:30:00Z",
  "payload": {
    "bookingId": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
    "familyMemberId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "eventTermId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "parentEmail": "parent@example.com",
    "eventName": "Bicycle Tour",
    "termDate": "2026-07-15T09:00:00Z",
    "cancelledBy": "USER"
  }
}
```

> `cancelledBy` is one of `USER | EVENT_OWNER | SYSTEM` (`SYSTEM` = a term cancellation / automated job; notification-service skips the per-booking email in that case because the term-cancellation flow already notifies).

### EventTermCancelled

```json
{
  "eventType": "EventTermCancelled",
  "timestamp": "2026-07-01T14:00:00Z",
  "payload": {
    "eventTermId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "eventName": "Bicycle Tour",
    "termDate": "2026-07-15T09:00:00Z",
    "organizationId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
    "caregiverIds": [
      "d290f1ee-6c54-4b01-90e6-d701748f0851"
    ],
    "cancelledBy": "EVENT_OWNER"
  }
}
```

### WaitlistPromoted

```json
{
  "eventType": "WaitlistPromoted",
  "timestamp": "2026-06-20T11:00:00Z",
  "payload": {
    "bookingId": "c56a4180-65aa-42ec-a945-5fd21dec0538",
    "familyMemberId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "eventTermId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "parentEmail": "another.parent@example.com",
    "eventName": "Bicycle Tour",
    "termDate": "2026-07-15T09:00:00Z"
  }
}
```

### ParticipantListRequested

```json
{
  "eventType": "ParticipantListRequested",
  "timestamp": "2026-07-14T02:00:00Z",
  "payload": {
    "eventTermId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "caregiverEmail": "caregiver@example.com",
    "eventName": "Bicycle Tour",
    "termDate": "2026-07-15T09:00:00Z",
    "participantNames": ["Anna Müller", "Max Muster", "Sofia Hofer"]
  }
}
```

### PaymentRefunded

```json
{
  "eventType": "PaymentRefunded",
  "timestamp": "2026-07-02T09:00:00Z",
  "payload": {
    "paymentId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "bookingId": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
    "organizationId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
    "parentEmail": "parent@example.com",
    "eventName": "Bicycle Tour",
    "amount": 15.00
  }
}
```

---

## Request & Response Shapes by Service

> **Naming/format convention.** Most write endpoints bind their inputs with Spring `@RequestParam`, so they are called with **query or form parameters** (`application/x-www-form-urlencoded`), not a JSON body. Only the endpoints listed below as "JSON body" take a `@RequestBody`. Some `*Request` DTO classes exist in the code but are not wired to a controller; the table below is derived from the actual controllers and is the source of truth.

| Service | Request style | Response shape |
|---|---|---|
| booking-service | Query/form params (`familyMemberId`, `eventTermId`); path params for reads | `BookingResponse` |
| identity-service | JSON body for `login` (`LoginRequest`), `refresh` (`RefreshRequest`), update user (`UpdateUserRequest`); **query/form params** for register, add family member, caregivers | `UserResponse`, `FamilyMemberResponse`, `CaregiverResponse`; `login`/`refresh` return `LoginResponse` |
| event-service | JSON body (`CreateEventRequest`, `UpdateEventRequest`, `CreateEventTermRequest`, `ChangeStatusRequest`, `CreateRemarkRequest`, `SendMessageRequest`, `UpdateCapacityRequest`) | `EventResponse`, `EventTermResponse`, `RemarkResponse` |
| organization-service | Query/form params for all writes | Reads → `OrganizationResponse`, `TeamMemberResponse`, `SponsorResponse`; create / add-sponsor return the raw `Organization` / `Sponsor` entity |
| payment-service | Query/form params (`note` is a query param) | Raw `Payment` entity; `…/balance` returns a bare `BigDecimal` |
| notification-service | Event-driven (Kafka); REST is health-only | — |
| booklet-service | UUID path variable only (no request body) | `byte[]` (`application/pdf`) |
