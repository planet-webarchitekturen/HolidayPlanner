# CQRS and Composition Queries — booking-service

## 1. Composition Query Candidates

A composition query combines data from multiple services so the client makes one call instead of many.

### Candidate 1 — Enriched bookings for a family member
`GET /api/bookings/family-member/{familyMemberId}/details`

- **What it combines**: booking rows (booking-service DB) + event details (event-service)
- **Why it's a composition candidate**: the raw query returns only IDs and status. A parent viewing their bookings needs event name, location, dates, and price — all owned by event-service. Without composition the frontend calls event-service once per booking. booking-service makes those calls internally and returns one complete response.

| Field | Source |
|---|---|
| `bookingId`, `status`, `bookedAt` | booking-service DB |
| `eventName`, `eventLocation`, `termStart`, `termEnd`, `price` | event-service `GET /api/events/terms/{id}` |

### Candidate 2 — Event term capacity summary
`GET /api/bookings/event-term/{eventTermId}/summary`

- **What it combines**: booking counts (booking-service DB) + capacity info (event-service)
- **Why it's a composition candidate**: event managers need confirmed/waitlisted counts alongside `maxParticipants` and whether the term is full. Booking counts are owned by booking-service; capacity (`maxParticipants`, `eventName`) is owned by event-service. Neither service alone has the full picture, and `availableSpots`/`isFull` must be computed from both.

| Field | Source |
|---|---|
| `confirmedCount`, `waitlistedCount` | booking-service DB |
| `eventName`, `termStart`, `maxParticipants` | event-service `GET /api/events/terms/{id}` |
| `availableSpots`, `isFull` | computed: `max(0, maxParticipants - confirmedCount)` |

Both queries apply graceful degradation: if event-service is unavailable, booking counts are always returned and event-level fields go null rather than returning a 503.

---

## 2. CQRS in booking-service

### Where CQRS applies

booking-service has a clear natural split:

- **Commands** (writes): `createBooking`, `cancelBooking`, `cancelAllBookings`, `promoteFromWaitingList`
  — modify DB state, trigger waitlist promotion, publish Kafka events, need transactional guarantees
- **Queries** (reads): `getBookingsForEventTerm`, `getBookingCount`, `getBookingsForFamilyMember`, both composition queries
  — called far more often than writes; must be fast and must never publish events or hold write locks

### Why CQRS benefits this service

- **Kafka isolation**: event publishing is confined to `BookingCommandService`. No read path can accidentally publish a domain event.
- **Independent optimization**: query paths can be cached or routed to a read replica without touching write logic.
- **Cleaner tests**: command tests need no query mocks; query tests need no Kafka/producer setup.
- **Clear data flow**: `EventTermCancelledConsumer` injects `BookingCommandService` — Kafka consumers trigger commands, never queries.

### Implementation

| Class | Role |
|---|---|
| `BookingCommandService` | All write operations + Kafka publishing |
| `BookingQueryService` | All read operations + composition queries |
| `BookingController` | Routes POST/DELETE → commands, GET → queries |
| `EventTermCancelledConsumer` | Kafka consumer → triggers commands only |

> `BookingService` (original) is kept as a transitional measure and can be removed once the split is confirmed stable. Both the new split and the old class share the same PostgreSQL DB — CQRS here is a class-level separation, not a separate read model.

---

## 3. Findings and Open Questions

**Findings**
- Both composition queries work and degrade gracefully when event-service is unavailable.
- The CQRS split cleanly isolates Kafka publishing and makes read/write responsibilities explicit without requiring a second database.

**Open questions**
- **N+1 in enriched family-member query**: event-service is called once per booking. Short-term mitigation: deduplicate by distinct `eventTermId` and batch in memory. Longer-term: add a batch endpoint to event-service.
- **Caching event term details**: a 5-min `@Cacheable` per `eventTermId` would reduce repeated calls. Risk: stale data if a term is updated or cancelled.
- **Full CQRS read model**: a separate read DB updated via `BookingCreated`/`BookingCancelled` Kafka events would be the natural next step if read and write scaling requirements diverge.
- **Command return value**: commands currently return `BookingResponse`. Strict CQRS returns only an acknowledgement; keeping the response is the pragmatic choice for this project.
