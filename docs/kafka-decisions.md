# Kafka Architecture Decisions — Holiday Planner

> Prepared for Session 4 lecture presentation

---

## Serialization: JSON

**Choice:** JSON via Jackson (Spring Boot's default ObjectMapper)

**Why:**
- Already a transitive dependency of `spring-boot-starter-web` — no extra setup
- Human-readable, easy to debug in Kafka UI (Redpanda Console at localhost:5001)
- Simple to evolve schemas without a schema registry
- Avro would require a separate Schema Registry container and extra tooling complexity, which is overkill for a university project

---

## Topic Naming Convention

Pattern: `holiday-planner.<service>.<event-name>`

| Topic | Description |
|---|---|
| `holiday-planner.booking.created` | A booking was created (CONFIRMED or WAITLISTED) |
| `holiday-planner.booking.cancelled` | A booking was cancelled |
| `holiday-planner.booking.waitlist-promoted` | A waitlisted booking was promoted to CONFIRMED |
| `holiday-planner.event.term-cancelled` | An event term was cancelled (event owner or auto-cancel job) |
| `holiday-planner.event.participant-list-requested` | Scheduler requests caregiver notification |
| `holiday-planner.event.capacity-increased` | Max participants increased; booking-service should promote waitlist |
| `holiday-planner.payment.refunded` | A payment was refunded |

**Why this pattern:**
- Service prefix makes it immediately clear which service owns the topic
- Event name in kebab-case matches REST URL convention used across the project
- Easy to apply Kafka ACLs by prefix if needed later

---

## Message Envelope

Every Kafka message is wrapped in a `KafkaEnvelope<T>`:

```json
{
  "eventType": "BookingCreated",
  "version": "1",
  "timestamp": "2026-04-25T10:00:00",
  "source": "booking-service",
  "payload": {
    "bookingId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "familyMemberId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "eventTermId": "a3bb189e-8bf9-3888-9912-ace4e6543002",
    "status": "CONFIRMED",
    "parentEmail": "parent@example.com",
    "eventName": "Bicycle Tour",
    "termDate": "2026-07-15T09:00:00",
    "organizationId": "1c0f4c2c-5e3b-4f0a-afaf-010101010101",
    "amount": 15.00
  }
}
```

**Why an envelope:**
- `eventType` lets consumers route or filter without deserializing the full payload
- `version` enables future schema evolution — consumers can check `"version": "2"` and handle new fields gracefully
- `source` is useful for debugging (which service published this?)
- `timestamp` provides an application-level time independent of Kafka broker clock

---

## Message Keys

| Producer | Key |
|---|---|
| booking-service | `bookingId` |
| event-service | `eventTermId` |
| payment-service | `paymentId` |

**Why these keys:**
- Kafka partitions messages by key, so all events for the same booking/term/payment land in the same partition → guaranteed ordering per entity
- Consumers can also use the key for idempotency checks (e.g. "have I already processed an event with this bookingId?")

---

## Producer → Consumer Mapping

| Topic | Producer | Consumers |
|---|---|---|
| `holiday-planner.booking.created` | booking-service | payment-service (creates payment if CONFIRMED), notification-service (emails parent) |
| `holiday-planner.booking.cancelled` | booking-service | notification-service (emails parent) |
| `holiday-planner.booking.waitlist-promoted` | booking-service | notification-service (emails parent — booking confirmed) |
| `holiday-planner.event.term-cancelled` | event-service | booking-service (cancels all bookings), notification-service (emails caregivers) |
| `holiday-planner.event.participant-list-requested` | event-service (scheduler) | notification-service (emails caregiver with participant list) |
| `holiday-planner.event.capacity-increased` | event-service | booking-service (should call `promoteFromWaitingList` — **consumer to be implemented**) |
| `holiday-planner.payment.refunded` | payment-service | notification-service (emails parent about refund) |

---

## Consumer Groups

| Service | Group ID |
|---|---|
| booking-service | `booking-service` |
| payment-service | `payment-service` |
| notification-service | `notification-service` |

Each service has its own consumer group, so every subscriber independently receives every message from a topic it is subscribed to.

---

## Idempotency

**payment-service:** Before creating a payment, the consumer checks `paymentRepository.findByBookingId(bookingId)`. If a payment already exists it is skipped. This prevents duplicate payments if the same `BookingCreated` message is delivered more than once.

**notification-service:** Email delivery is fire-and-forget. Duplicate emails are tolerated (low risk for this project).

---

## Error Handling in Consumers

All consumers follow the same pattern:
- Wrap the handler body in `try/catch`
- `log.error(...)` on failure
- **Never rethrow** — the consumer keeps running
- Dead-letter topics are not implemented yet (open question below)

---

## Kafka Infrastructure

| Component | Image | Port |
|---|---|---|
| Kafka broker (KRaft mode) | `public.ecr.aws/bitnami/kafka:4.2.0` | 9092 (host), 29092 (internal) |
| Kafka UI | `redpandadata/console:v3.7.1` | 5001 |

KRaft mode means **no Zookeeper** — Kafka manages its own metadata. This simplifies the docker-compose setup and is the recommended approach for Kafka 3.x+.

Topics are auto-created on first use (`KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE: "true"`).

---

## Open Questions for the Lecture

1. **Dead-letter topics** — should we add a `*.dlq` topic per consumer so failed messages are not silently dropped?
2. **Ordering guarantees** — we use entity ID as the message key, but within one partition messages are ordered. Is this sufficient for our use cases?
3. **Outbox pattern** — publishing after `repository.save()` is not atomic. If the service crashes between save and publish, the event is lost. Should we use an outbox table?
4. **Schema versioning** — the `version` field in `KafkaEnvelope` is a string today. Should we enforce breaking vs non-breaking changes with a proper policy?
5. **Authentication** — the Kafka broker runs with `ALLOW_PLAINTEXT_LISTENER=yes`. Is this acceptable for the demo, or do we need SASL for the final presentation?
