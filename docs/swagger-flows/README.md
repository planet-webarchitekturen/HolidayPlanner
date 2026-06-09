# Swagger Flow Index

Use these files as step-by-step demo scripts for Swagger.

## Before You Start

Start infrastructure and services:

```bash
docker compose up -d postgres kafka
docker compose up -d
```

Optional Kafka UI:

```text
http://localhost:5001
```

Optional seed data:

```bash
docker exec -i holidayplanner-db psql -U postgres < docker/seed-basic-data.sql
```

Known seeded IDs:

```text
organizationId = 11111111-1111-1111-1111-111111111111
eventId        = 33333333-3333-3333-3333-333333333333
eventTermId    = 55555555-5555-5555-5555-555555555555
```

## Swagger URLs

| Service | URL |
| --- | --- |
| Identity | http://localhost:8083/swagger-ui.html |
| Organization | http://localhost:8084/swagger-ui.html |
| Event | http://localhost:8081/swagger-ui.html |
| Booking | http://localhost:8082/swagger-ui.html |
| Payment | http://localhost:8085/swagger-ui.html |
| Notification | http://localhost:8090/swagger-ui.html |
| Booklet | http://localhost:8087/swagger-ui.html |
| Booklet Request | http://localhost:8088/swagger-ui.html |

## Flows

1. [Setup, Register, Login, Roles](./00-setup-auth-and-roles.md)
2. [Organization, Sponsors, Team Members](./01-organization-management.md)
3. [Identity, Family Members, Caregivers](./02-identity-family-caregiver.md)
4. [Event Creation And Event Term Setup](./03-event-creation-and-term-setup.md)
5. [Booking And Automatic Payment Creation](./04-booking-and-payment.md)
6. [Event Term Cancellation Saga](./05-event-term-cancellation-saga.md)
7. [Capacity Increase And Waitlist Promotion](./06-capacity-and-waitlist.md)
8. [Participant Messaging And Remarks](./07-participant-messaging-and-remarks.md)
9. [Manual Payment Operations](./08-manual-payment-operations.md)
10. [Notification Service Direct Calls](./09-notification-direct-calls.md)
11. [Booklet PDF Generation](./10-booklet-generation.md)
12. [Booklet Request Workflow](./11-booklet-request-workflow.md)
13. [Troubleshooting](./99-troubleshooting.md)
