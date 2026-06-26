# API Examples

Selected REST endpoints (OpenAPI-style) and domain event schemas for the Holiday Planner system.

---

## BookingService (port 8082)

### POST /api/bookings — Create a booking

```yaml
POST /api/bookings:
  summary: Book an event term for a family member
  requestBody:
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/CreateBookingRequest'
        example:
          familyMemberId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"
          eventTermId: "7c9e6679-7425-40de-944b-e07fc1f90ae7"
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
  requestBody:
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/RegisterUserRequest'
        example:
          email: "parent@example.com"
          password: "s3cr3tP@ss"
          phoneNumber: "+43664123456"
          organizationId: "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
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

### POST /api/users/{userId}/family-members — Add family member

```yaml
POST /api/users/{userId}/family-members:
  summary: Add a family member (child) to a user's profile
  parameters:
    - name: userId
      in: path
      required: true
      schema:
        type: string
        format: uuid
  requestBody:
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/AddFamilyMemberRequest'
        example:
          firstName: "Anna"
          lastName: "Müller"
          birthDate: "2018-03-15"
          zip: "6900"
  responses:
    '201':
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
  summary: Create a new municipality organization
  requestBody:
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/CreateOrganizationRequest'
        example:
          name: "Gemeinde Bregenz"
          bankAccount: "AT12 3456 7890 1234 5678"
          bookingStartTime: "2026-06-01T08:00:00"
  responses:
    '201':
      description: Organization created
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/OrganizationResponse'
    '409':
      description: Organization name already taken
```

### POST /api/organizations/{organizationId}/sponsors — Add sponsor

```yaml
POST /api/organizations/{organizationId}/sponsors:
  summary: Add a sponsor to an organization
  parameters:
    - name: organizationId
      in: path
      required: true
      schema:
        type: string
        format: uuid
  requestBody:
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/AddSponsorRequest'
        example:
          name: "Raiffeisenbank Vorarlberg"
          amount: 500.00
  responses:
    '201':
      description: Sponsor added
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/SponsorResponse'
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
      description: Payment marked as PAID
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/PaymentResponse'
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
      description: Balance returned
      content:
        application/json:
          example:
            organizationId: "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
            balance: 2350.00
```

---

## BookletService (port 8087)

### POST /api/booklets/generate — Generate organization booklet

```yaml
POST /api/booklets/generate:
  summary: Generate a PDF booklet for an organization (Org Team)
  requestBody:
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/GenerateBookletRequest'
        example:
          organizationName: "Gemeinde Bregenz"
          contactInfo: "Rathausplatz 1, 6900 Bregenz | info@bregenz.at"
          eventSummaries:
            - "Bicycle Tour — 15. Juli 2026, 9:00–17:00 — Bregenz Harbour"
            - "Swimming Course — 20. Juli 2026, 10:00–12:00 — Freibad Bregenz"
          sponsorNames:
            - "Raiffeisenbank Vorarlberg"
            - "Hypo Vorarlberg"
  responses:
    '200':
      description: PDF booklet returned as binary
      content:
        application/pdf:
          schema:
            type: string
            format: binary
```

---

## Domain Event Schemas

All events are published as JSON messages via an event bus (e.g. Spring ApplicationEvent or a message broker like RabbitMQ/Kafka).

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

## DTO Summary by Service

| Service | Request DTOs | Response DTOs |
|---|---|---|
| booking-service | `CreateBookingRequest` | `BookingResponse` |
| identity-service | `RegisterUserRequest`, `LoginRequest`, `AddFamilyMemberRequest`, `CreateCaregiverRequest` | `UserResponse`, `FamilyMemberResponse`, `CaregiverResponse` |
| event-service | `CreateEventRequest`, `UpdateEventRequest`, `CreateEventTermRequest`, `CreateRemarkRequest` | `EventResponse`, `EventTermResponse`, `RemarkResponse` |
| organization-service | `CreateOrganizationRequest`, `UpdateOrganizationRequest`, `AddTeamMemberRequest`, `AddSponsorRequest` | `OrganizationResponse`, `TeamMemberResponse`, `SponsorResponse` |
| payment-service | `CreatePaymentRequest`, `MarkAsPaidRequest`, `RefundPaymentRequest` | `PaymentResponse` |
| notification-service | `SendEmailRequest`, `SendBulkEmailRequest`, `NotifyBookingConfirmedRequest`, `NotifyTermCancelledRequest`, `NotifyCaregiverRequest` | — (all void) |
| booklet-service | `GenerateBookletRequest`, `GenerateParticipantListRequest` | — (returns `byte[]`) |
