# Swagger UI Usage Guide

## Prerequisites

Make sure Docker is running with the infrastructure containers:

```bash
docker compose up -d postgres kafka
```

---

## Swagger URLs

| Service                 | URL                                      | Port |
|-------------------------|------------------------------------------|------|
| Identity Service        | http://localhost:8083/swagger-ui.html    | 8083 |
| Booking Service         | http://localhost:8082/swagger-ui.html    | 8082 |
| Event Service           | http://localhost:8081/swagger-ui.html    | 8081 |
| Organization Service    | http://localhost:8084/swagger-ui.html    | 8084 |
| Payment Service         | http://localhost:8085/swagger-ui.html    | 8085 |
| Notification Service    | http://localhost:8090/swagger-ui.html    | 8090 |
| Booklet Service         | http://localhost:8087/swagger-ui.html    | 8087 |
| Booklet Request Service | http://localhost:8088/swagger-ui.html    | 8088 |

---

## Step 1 — Create an Account

Run: `docker exec -i holidayplanner-db psql -U postgres < docker/seed-basic-data.sql`
for basic data like organization

Open the **Identity Service** Swagger: http://localhost:8083/swagger-ui.html

1. Find `POST /api/auth/register`
2. Click **Try it out**
3. Fill in the query parameters:
   - `email` → your email
   - `password` → your password
   - `phoneNumber` → your phone number (e.g. `+4366012345678`)
   - `organizationId` → any valid UUID (e.g. `11111111-1111-1111-1111-111111111111`)
4. Click **Execute**

---

## Step 2 — Login and Get a JWT Token

1. Find `POST /api/auth/login`
2. Click **Try it out**
3. Enter the request body:
```json
{
  "email": "your@email.com",
  "password": "yourpassword"
}
```
4. Click **Execute**
5. Copy the `token` value from the response:
```json
{
  "id": "...",
  "email": "your@email.com",
  "token": "eyJhbGci...",
  "tokenType": "Bearer"
}
```

> The token is valid for **24 hours**. After it expires, login again to get a new one.

---

## Step 3 — Authorize in Swagger

This step is required for any protected endpoint. Do this on **each service** you want to test.

1. Click the **Authorize** button (lock icon) at the top right of the Swagger page
2. Paste only the token value (without `Bearer`):
```
eyJhbGciOiJIUzM4NCJ9...
```
3. Click **Authorize** → **Close**

All requests made from that Swagger page will now include your token automatically.

---

## Step 4 — Test an Endpoint

1. Find the endpoint you want to test
2. Click **Try it out**
3. Fill in any required parameters or request body
4. Click **Execute**
5. Check the response below

---

## Roles & Permissions

Different endpoints require different roles. Your account is created with the `USER` role by default.

| Role                       | Access                                              |
|----------------------------|-----------------------------------------------------|
| `USER`                     | Create/cancel own bookings                          |
| `EVENT_OWNER`              | Create and manage events                            |
| `ORGANIZATION_TEAM_MEMBER` | Manage team, create bookings                        |
| `ORGANIZATION_OWNER`       | Manage organization, sponsors, payments             |
| `ACCOUNTANT`               | Create and process payments                         |
| `ADMIN`                    | Full access to everything                           |

If you get a **403 Forbidden** response, the endpoint requires a higher role than your account has.

---

## Internal Service Calls (X-Service-Secret)

Some endpoints are meant for service-to-service communication only. Instead of a JWT token, they use a shared secret in the header:

```
X-Service-Secret: holidayplanner-internal-service-secret
```

In Swagger, add this as a header manually in the request parameters when testing these endpoints.

---

## Running Services Locally

To run a service locally instead of via Docker:

```bash
# Identity Service
./mvnw spring-boot:run -pl identity-service

# Booking Service
./mvnw spring-boot:run -pl booking-service

# Event Service
./mvnw spring-boot:run -pl event-service

# Organization Service
./mvnw spring-boot:run -pl organization-service

# Payment Service
./mvnw spring-boot:run -pl payment-service

# Notification Service
./mvnw spring-boot:run -pl notification-service

# Booklet Service
./mvnw spring-boot:run -pl booklet-service

# Booklet Request Service
./mvnw spring-boot:run -pl booklet-request-service
```

> Stop the matching Docker container first to avoid port conflicts:
> ```bash
> docker stop holidayplanner-<service-name>-1
> ```

---

## Common Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `401 Unauthorized` | Missing or expired token | Login again and re-authorize in Swagger |
| `403 Forbidden` | Token valid but role insufficient | Use an account with the required role |
| `Connection refused` | Service not running | Start the service or check Docker |
| `Could not connect to Postgres` | Docker not running | Run `docker compose up -d postgres kafka` |
