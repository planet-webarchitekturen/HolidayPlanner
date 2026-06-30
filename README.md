# Holiday Planner

A web application that provides a platform where municipalities can offer events that can be booked by parents for their children during school holidays.

---

## Quick Start

```bash
git clone https://github.com/planet-webarchitekturen/HolidayPlanner
cd HolidayPlanner
docker compose up --build -d
```

The first `--build` compiles all 7 services in Docker (pinned JDK 21) and takes a few minutes — **no GitHub or registry login is required**, images are built locally from source. Later runs reuse the built images. Once the containers are healthy, verify:

```bash
curl http://localhost:8081/api/events/health   # event-service
curl http://localhost:8082/api/bookings/health # booking-service
curl http://localhost:8083/api/identity/health # identity-service
curl http://localhost:8085/api/payments/health # payment-service
```

Kafka UI: http://localhost:5001

---

## Team Members

All members worked as **backend developers** (Java 21 / Spring Boot) on a microservice they co-owned: domain modelling, REST API, Kafka events and tests for that service. The "Contribution" column summarises each service's scope.

| Name | Role | Service(s) | Contribution |
|---|---|---|---|
| Büsra Aydemir | Backend developer | `IdentityService` | User registration & authentication, JWT issuing, transactional outbox |
| Denise Müller | Backend developer | `IdentityService` | Family members & caregivers, authorization rules, identity events |
| Amir Hodzic | Backend developer | `EventService` | Events & event terms, status lifecycle, capacity handling |
| Samir Hodzic | Backend developer | `EventService` | Remarks, caregiver assignment, event-term cancellation saga |
| Muhammed Güzel | Backend developer | `BookingService` | Booking creation, waiting list, API composition across services |
| Tarik Pasalic | Backend developer | `BookingService` | Cancellations, capacity-increase & waitlist promotion |
| Jan Burtscher | Backend developer | `OrganizationService` + `PaymentService` | Organizations, team members, sponsors |
| Aleksander Lukic | Backend developer | `OrganizationService` + `PaymentService` | Payments, refunds, balance sheet, delete-organization saga |
| Fabian Türtscher | Backend developer | `NotificationService` + `BookletService` | Email notifications (event-driven consumers) and PDF booklet generation |

> _Roles above reflect service ownership; team members can refine their individual sub-roles (e.g. lead, testing, DevOps) as needed._

---

## Services

| Service | Responsibility |
|---|---|
| `IdentityService` | User auth, registration, sessions, caregiver management |
| `EventService` | Events, event terms, status lifecycle, remarks, caregiver assignment |
| `BookingService` | Bookings, waiting list, cancellations, participant management |
| `OrganizationService` | Organizations, team members, bank account, booking start time, sponsors |
| `PaymentService` | Payment tracking, refunds, sponsor payments, balance sheet |
| `NotificationService` | Email notifications (booking confirmed, term cancelled, bulk messaging) |
| `BookletService` | PDF generation of the event booklet per organization |

---

## Repository

**Main Repository:** [https://github.com/planet-webarchitekturen/HolidayPlanner](https://github.com/planet-webarchitekturen/HolidayPlanner)

The repository is organized as a Maven multi-module monorepo on `main`. Each service is its own Spring Boot application, and the shared model classes live in the `shared` module.

---

## Technology Decisions

### Language & Framework
All services are implemented using **Java 21** with **Spring Boot 3.2.4**.

**Reasons:**
- Strong ecosystem for REST APIs and microservices
- Built-in support for JPA/Hibernate for database access
- Easy Docker integration
- Familiar to all team members
- Well supported within the allowed languages (Java, JavaScript/TypeScript, C#, Kotlin, Python)

### Database
**PostgreSQL** — each service owns its own database schema (no shared DB between services).

### Build Tool
**Maven** — the repository uses a root aggregator/parent `pom.xml` with one module per service plus a shared library module.

### PDF Generation
**Apache PDFBox** (BookletService) — open source, no licensing restrictions.

### Email
**Spring Mail** (NotificationService) — built-in Spring Boot support, works with any SMTP server.

### Containerization
**Docker** — each service is shipped as a Docker image using a multi-stage build (Maven build stage + lightweight JRE run stage).

---

## Project Structure

```
HolidayPlanner/
├── pom.xml
├── README.md
├── docker-compose.yml          ← runs all services + Kafka + Postgres
├── create_topic.sh             ← create a Kafka topic
├── kcat                        ← kcat wrapper (produce/consume messages)
├── .github/workflows/
│   └── ci.yml                  ← builds & pushes images to GHCR on push to main
├── docker/
│   ├── init-databases.sh       ← creates all PostgreSQL databases
│   ├── application-event.yml
│   ├── application-booking.yml
│   ├── application-identity.yml
│   ├── application-organization.yml
│   └── application-payment.yml
├── docs/
│   ├── domain-model.md
│   ├── DomainModel.svg
│   ├── system-operations.md
│   ├── messaging-conventions.md ← serialization, topic naming, envelope format
│   └── testing-notes.md
├── scripts/                    ← shell demos for common scenarios
├── shared/
├── identity-service/
├── event-service/
├── booking-service/
├── organization-service/
├── payment-service/
├── notification-service/
└── booklet-service/
```

---

## How to Build

### Prerequisites
- **JDK 21 or newer** — the build targets Java 21 bytecode. You do **not** need Maven installed; the repo ships a Maven wrapper.
- Docker (for `docker compose`)
- PostgreSQL (only if running a service outside Docker)

> Use the bundled wrapper `./mvnw` (or `mvnw.cmd` on Windows) instead of a system `mvn`, so everyone builds with the same Maven (3.9.6). The build enforces JDK 21+ and Maven 3.9+ and **fails immediately with a clear message** if they're missing — no more cryptic Lombok errors.

### Demo scripts

The `scripts/` directory contains shell scripts that walk through common scenarios, for example bookings, waitlists, events, booklets, organization payments and story-specific flows. Start the stack first, then run one with `bash scripts/<script-name>.sh`.

### Build the full monorepo
```bash
./mvnw clean install
```

### Build a single service
```bash
./mvnw -pl <service-name> -am clean package -DskipTests
```

### Run a single service locally
```bash
./mvnw -pl <service-name> spring-boot:run
```

### Run tests
```bash
./mvnw test
```

### Troubleshooting
- **`java.lang.ExceptionInInitializerError: ... TypeTag :: UNKNOWN` during compile** means Maven is running on a JDK its Lombok version can't handle. Check the JDK Maven actually uses:
  ```bash
  ./mvnw -version    # look at the "Java version:" line — it can differ from `java -version`
  ```
  If it's wrong, point `JAVA_HOME` at a JDK 21 install and rebuild:
  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
  ./mvnw clean install
  ```

---

## Docker

### Run everything locally

```bash
docker compose up --build
```

This builds all 7 service images locally from their Dockerfiles and starts them + PostgreSQL + Kafka + Kafka UI (http://localhost:5001). No registry login is required.

PostgreSQL is published on **host port 5433** (`5433:5432` in `docker-compose.yml`) so it does not clash with a local Postgres on 5432. From your machine, set `DB_PORT=5433` when connecting to the compose database; containers still use `postgres:5432` on the Docker network.

Images are built locally from each service's `Dockerfile` — no GitHub login needed. (CI separately publishes images to `ghcr.io/planet-webarchitekturen/<service>:latest` on every push to `main`; those are used by the deployment pipeline, not required for local development.)

> **Troubleshooting:** if a service logs `database "<name>_db" does not exist`, the Postgres volume was created before the DB init script ran. Reset it with `docker compose down -v && docker compose up --build`.

### Build an image manually (from the repo root)

```bash
docker build -f booking-service/Dockerfile -t booking-service .
```

All Dockerfiles must be built from the repo root because they depend on the `shared` module.

### Publish images to GHCR

Images are published automatically via GitHub Actions on every push to `main`.  
To publish manually:

```bash
echo $GITHUB_TOKEN | docker login ghcr.io -u <github-username> --password-stdin
docker build -f booking-service/Dockerfile -t ghcr.io/planet-webarchitekturen/booking-service:latest .
docker push ghcr.io/planet-webarchitekturen/booking-service:latest
```

---

## Configuration

Each service is configured via environment variables. Defaults are set for local development in each module's `application.yml`.

### Common variables (all services except NotificationService and BookletService)

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `<service>_db` | Database name |
| `DB_USER` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |

### NotificationService additional variables

| Variable | Default | Description |
|---|---|---|
| `MAIL_HOST` | `smtp.gmail.com` | SMTP server host |
| `MAIL_PORT` | `587` | SMTP server port |
| `MAIL_USERNAME` | — | Email address to send from |
| `MAIL_PASSWORD` | — | Email password or app password |

---

## Kafka

Kafka runs on `localhost:9092`. UI at http://localhost:5001.

**Create a topic:**
```bash
./create_topic.sh booking.booking.created
```

**Consume messages:**
```bash
./kcat -C -t booking.booking.created -o beginning
```

See [docs/messaging-conventions.md](docs/messaging-conventions.md) for the full topic list and message envelope format.

---

## What Has Been Prepared

- [x] Team formed and service responsibilities assigned
- [x] GitHub repository created
- [x] Technology stack decided (Java 21 + Spring Boot 3.2.4)
- [x] Services bootstrapped
- [x] Maven multi-module parent/aggregator at the repository root
- [x] Dockerfiles fixed for multi-module build (run from repo root)
- [x] Images published to GHCR via GitHub Actions
- [x] `docker-compose.yml` with all services + Kafka + PostgreSQL
- [x] Messaging conventions defined (JSON, topic naming, envelope)
- [x] Database configuration per service (PostgreSQL via env variables)
- [x] Domain model documented in `docs/`
- [x] System operations documented in `docs/`

---

## Documentation

- [Domain Model](docs/domain-model.md)
- [System Operations](docs/system-operations.md)
- [Messaging Conventions](docs/messaging-conventions.md)
- [Testing Notes](docs/testing-notes.md)
- [Event Service design](docs/event-service-design.md)
- [Identity Service](docs/identity-service.md) — REST API, events, outbox, sagas, authorization
- [API Examples](docs/api-examples.md) — selected REST endpoints and domain event schemas
- [Postman Collection](docs/HolidayPlanner.postman_collection.json) — import into Postman; run **Identity → Login** to capture the JWT, then call the other services
