# Troubleshooting

## 401 Unauthorized

Meaning:

```text
No token, expired token, malformed token, or wrong JWT secret.
```

Fix:

1. Login again in Identity Service.
2. Copy the new token.
3. Authorize in the Swagger page of the service you are testing.

Usually use:

```text
Bearer eyJ...
```

## 403 Forbidden

Meaning:

```text
Token is valid, but role is not allowed.
```

Fix role in `identity_db`, then log in again:

```sql
UPDATE users
SET role = 'ADMIN'
WHERE email = 'demo@example.com';
```

For Booking creation, use `USER`, not `ADMIN`:

```sql
UPDATE users
SET role = 'USER'
WHERE email = 'demo@example.com';
```

## Kafka Connection Spam

If you see:

```text
Connection to node -1 (localhost/127.0.0.1:29092) could not be established
```

Use:

```text
local services: localhost:9092
docker services: kafka:29092
```

Kafka UI:

```text
http://localhost:5001
```

Kafka UI is optional. Kafka itself must run.

## Event Not Found

Check that you are using the correct ID:

```text
POST /api/events/{eventId}/terms       -> needs eventId
PATCH /api/events/terms/{eventTermId}  -> needs eventTermId
```

SQL:

```sql
\connect event_db
SELECT id, short_title FROM events;
SELECT id, event_id, status FROM event_terms;
```

## Payment Not Created

Payment creation after booking is async via Kafka.

Check:

```text
payment-service running
kafka running
booking-service published BookingCreated
payment-service consumed BookingCreated
```

Topic:

```text
holiday-planner.booking.created
```

## Seed Data

Run:

```bash
docker exec -i holidayplanner-db psql -U postgres < docker/seed-basic-data.sql
```

If tables do not exist yet, start the services once first. Hibernate creates the tables.

## Useful SQL

Identity:

```sql
\connect identity_db
SELECT id, email, organization_id, role FROM users;
```

Organization:

```sql
\connect organization_db
SELECT id, name FROM organizations;
```

Event:

```sql
\connect event_db
SELECT id, short_title, organization_id FROM events;
SELECT id, event_id, status FROM event_terms;
```

Booking:

```sql
\connect booking_db
SELECT id, family_member_id, event_term_id, status FROM bookings;
```

Payment:

```sql
\connect payment_db
SELECT id, booking_id, organization_id, status, amount FROM payments;
```
