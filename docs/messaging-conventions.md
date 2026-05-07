## Messaging Conventions

Decisions agreed for inter-service communication via Kafka.

---

### Serialization

**Format: JSON** (`application/json`)

- Spring Boot uses Jackson by default — no extra dependencies needed
- Human-readable, easy to inspect in Kafka UI (Redpanda Console)
- UTF-8 encoded

---

### Topic Naming Convention

Pattern: `{domain}.{entity}.{event}`

| Part | Rule | Example |
|---|---|---|
| `domain` | service name without `-service` | `booking`, `event`, `identity` |
| `entity` | singular, lowercase | `booking`, `eventterm`, `user` |
| `event` | past tense, kebab-case | `created`, `cancelled`, `status-changed` |

**Examples:**

| Topic | Published by | Meaning |
|---|---|---|
| `booking.booking.created` | booking-service | A booking was confirmed or waitlisted |
| `booking.booking.cancelled` | booking-service | A booking was cancelled |
| `booking.booking.promoted` | booking-service | Waitlisted booking moved to confirmed |
| `event.eventterm.status-changed` | event-service | EventTerm status changed (ACTIVE, CANCELLED…) |
| `event.eventterm.capacity-updated` | event-service | Max participants changed (legacy name; runtime may use `holiday-planner.event.capacity-increased`) |
| `event.eventterm.capacity-increased` | event-service | Max participants increased (waitlist promotion signal) |
| `identity.user.registered` | identity-service | New user account created |
| `organization.organization.created` | organization-service | New organization registered |
| `payment.payment.completed` | payment-service | Payment successfully processed |
| `payment.payment.failed` | payment-service | Payment failed |

---

### Message Envelope

Every Kafka message uses this JSON structure:

```json
{
  "id":        "550e8400-e29b-41d4-a716-446655440000",
  "type":      "booking.booking.created",
  "source":    "booking-service",
  "timestamp": "2026-04-25T10:00:00Z",
  "version":   "1",
  "data": {
    ...event-specific payload...
  }
}
```

| Field | Type | Description |
|---|---|---|
| `id` | UUID string | Unique message ID (for deduplication) |
| `type` | string | Topic name repeated — makes the message self-describing |
| `source` | string | Service that published the event |
| `timestamp` | ISO-8601 UTC | When the event occurred (not ingested) |
| `version` | string | Schema version — increment when `data` shape changes |
| `data` | object | Event-specific payload |

#### Example: `booking.booking.created`

```json
{
  "id":        "a1b2c3d4-0000-0000-0000-000000000001",
  "type":      "booking.booking.created",
  "source":    "booking-service",
  "timestamp": "2026-04-25T10:00:00Z",
  "version":   "1",
  "data": {
    "bookingId":      "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "familyMemberId": "c06ac270-776e-4000-95df-fdec815af9c5",
    "eventTermId":    "d290f1ee-6c54-4b01-90e6-d701748f0851",
    "status":         "CONFIRMED",
    "bookedAt":       "2026-04-25T09:59:58Z"
  }
}
```

---

### Creating Topics

Use the provided script (requires Docker):

```bash
./create_topic.sh booking.booking.created
./create_topic.sh event.eventterm.status-changed
```

Or all at once:

```bash
for topic in \
  booking.booking.created \
  booking.booking.cancelled \
  booking.booking.promoted \
  event.eventterm.status-changed \
  event.eventterm.capacity-updated \
  identity.user.registered \
  organization.organization.created \
  payment.payment.completed \
  payment.payment.failed; do
  ./create_topic.sh "$topic"
done
```

### Testing with kcat

Consume messages from a topic:
```bash
./kcat -C -t booking.booking.created -o beginning
```

Produce a test message:
```bash
echo '{"id":"test","type":"booking.booking.created","source":"test","timestamp":"2026-04-25T10:00:00Z","version":"1","data":{}}' | \
  ./kcat -P -t booking.booking.created
```
