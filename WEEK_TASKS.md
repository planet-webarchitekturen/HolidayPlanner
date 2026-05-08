# This Week — Security & Inter-Service Communication

## Overview

This week's focus was securing the entire platform. Previously all endpoints were open. We added end-to-end authentication, role-based access control, organisation-scoped data isolation, and a mechanism for services to call each other internally without a user token.

---

## 1. JWT Authentication

**What:** Every service now validates requests using JSON Web Tokens. The identity-service is the only issuer; all other services validate independently using a shared HMAC secret (HS384).

**How it works:**
- User logs in at `POST /api/identity/auth/login` → gets a signed JWT valid for 24 hours
- Every subsequent request includes `Authorization: Bearer <token>`
- A shared `JwtAuthenticationFilter` (in the `shared` module) validates the signature and populates the security context
- Invalid or expired tokens → 401 immediately
- Missing token on a protected endpoint → 401

**Token payload:**
```json
{
  "sub": "<userId>",
  "organizationId": "<orgId>",
  "roles": ["EVENT_OWNER"],
  "email": "user@example.com"
}
```

**Design choice — symmetric secret over asymmetric keys:** We use one shared secret rather than RSA public/private keys. For a university project this avoids running a key distribution service while still being cryptographically sound. All services read `JWT_SECRET` from their environment.

---

## 2. Role-Based Access Control (RBAC)

**What:** Five roles enforced via Spring Security's `@PreAuthorize` on individual endpoints.

| Role | Who | What they can do |
|---|---|---|
| `ADMIN` | Platform administrator | Create/delete organisations |
| `EVENT_OWNER` | Event manager in an org | Create and manage events and terms |
| `ORGANIZATION_TEAM_MEMBER` | Operations staff | Manage team members and sponsors |
| `ACCOUNTANT` | Finance staff | Process and refund payments |
| `USER` | Parent | Book events for family members |

**Example:**
```java
@PostMapping
@PreAuthorize("hasAnyRole('EVENT_OWNER', 'ORGANIZATION_TEAM_MEMBER', 'ADMIN')")
public ResponseEntity<EventResponse> createEvent(...) { ... }

@PostMapping("/{bookingId}/pay")
@PreAuthorize("hasRole('ACCOUNTANT')")
public ResponseEntity<Payment> markAsPaid(...) { ... }
```

Health endpoints and login/register remain public (`permitAll`) — no token required.

---

## 3. Organisation-Scoped Access Control

**What:** Even a user with the right role cannot touch resources that belong to a different organisation. The JWT carries the user's `organizationId`; every write operation checks it against the resource being modified.

**Why this matters:** Without this, an `EVENT_OWNER` from Organisation A could edit or delete events from Organisation B. RBAC alone does not prevent this because the role check does not know which organisation owns the resource.

**How it is enforced** — in the service layer, not the HTTP layer:
```java
UUID currentOrgId = SecurityUtils.getCurrentOrganizationId();
if (currentOrgId != null && !currentOrgId.equals(event.getOrganizationId())) {
    throw new AccessDeniedException("Event belongs to a different organisation");
}
```

Applied to:
- **event-service** — create and update events check the JWT org matches the event's org
- **booking-service** — creating a booking checks the event term's org matches the user's org
- **payment-service** — marking paid or refunding checks the payment's org matches the user's org

---

## 4. Service-to-Service Authentication (X-Service-Secret)

**What:** Some operations have no user — for example, when an organisation is deleted, the organisation-service must tell the event-service to delete all associated events. There is no user JWT to pass along.

**Solution:** A shared secret header `X-Service-Secret`. The calling service includes the header; the receiving service validates it and grants `ROLE_SERVICE` authority. Internal-only endpoints are guarded with `@PreAuthorize("hasRole('SERVICE')")`.

**Flow:**
```
OrganizationService.deleteOrganization()
  → EventServiceClient.deleteEventsByOrganization()
      → DELETE /api/events/organization/{id}
          Header: X-Service-Secret: <secret>
  → ServiceAuthenticationFilter validates header
  → grants ROLE_SERVICE
  → @PreAuthorize("hasRole('SERVICE')") passes
  → events deleted
```

**Security rules of the filter:**
- Header absent → pass through (normal user JWT flow continues)
- Header present, wrong value → 401 `{"error":"Invalid service secret"}`
- Header present, correct value → sets `ROLE_SERVICE` in security context

**Filter order in the chain:**
```
ServiceAuthenticationFilter  ← runs first
JwtAuthenticationFilter
Spring AuthorizationFilter
```

The service secret is injected via environment variable `SERVICE_SECRET`, with the same default across all services so local development works without configuration.

---

## Shared Module

All security code lives in `shared/src/main/java/com/holidayplanner/shared/security/`:

| Class | Purpose |
|---|---|
| `JwtAuthenticationFilter` | Validates Bearer tokens, populates SecurityContext |
| `ServiceAuthenticationFilter` | Validates X-Service-Secret header, grants ROLE_SERVICE |
| `JwtClaims` | Typed holder for userId, organizationId, roles, email |
| `SecurityUtils` | Static helpers: `getCurrentOrganizationId()`, `getCurrentUser()` |

Each service instantiates these in its own `SecurityConfig` — no auto-wiring from the shared module, keeping each service's security configuration explicit and visible.

---

## Findings

**Filter order matters more than expected.**
`ServiceAuthenticationFilter` must run before `JwtAuthenticationFilter`. If reversed, a service-to-service request carrying only `X-Service-Secret` would hit the JWT filter first, find no `Authorization` header, and pass through unauthenticated — then fail the `@PreAuthorize("hasRole('SERVICE')")` check. The order makes the two auth paths mutually exclusive per request.

**Org-scope cannot live at the HTTP layer.**
We initially considered enforcing org-scope in the `SecurityFilterChain` via path matchers. This does not work because the org ID of the resource being modified is only known after the database is queried — it is not in the URL. The check must happen inside the service method, after the record is loaded, which is why it sits in `*CommandService` rather than in security config.

**`@Component` on a filter causes it to run twice.**
An earlier version of the identity-service had its local `JwtAuthenticationFilter` annotated with `@Component`. Spring Boot auto-registers all `OncePerRequestFilter` beans as servlet filters, and the `SecurityConfig` also added it to the security chain — resulting in the filter running twice per request. The fix is to never annotate security filters with `@Component`; instead instantiate them explicitly in `SecurityConfig` as `@Bean`.

**A stale Docker image silently breaks security rules.**
When a service image is rebuilt but not pushed, or pushed but not re-pulled, the running container has old code. We discovered this when `permitAll()` rules added for the login/register endpoints were not taking effect in the running container — the image predated the commit that added those rules. Rule: always rebuild, push, and recreate the container after any `SecurityConfig` change.

**Symmetric JWT secret is a single point of failure.**
Because every service shares one secret, a leak of `JWT_SECRET` compromises the entire platform simultaneously. With asymmetric keys, each service would only hold a public key; the private key stays in the identity-service alone.

---

## Open Questions

**Should org-scope move to an API gateway?**
Currently each service re-implements the same org-scope check independently. A dedicated gateway (e.g. Spring Cloud Gateway) could extract the `organizationId` from the JWT and enforce scoping centrally before requests reach services. This would remove duplication but add another infrastructure component and a single point of failure.

**When should the symmetric JWT secret become asymmetric?**
HS384 with a shared secret is fine for a controlled environment where all services are owned by one team. If third-party services ever need to validate tokens without receiving the private key, we would need RS256/ES256. The jjwt library already supports this — the switch is a configuration change in identity-service plus distributing the public key to other services.

**How should token revocation be handled?**
Currently a JWT is valid until it expires (24 hours). There is no way to invalidate a token immediately (e.g. after a user changes their password or is removed from an organisation). Options are: a token blocklist in Redis, short-lived tokens (e.g. 15 minutes) paired with a refresh token, or switching to opaque tokens with a central introspection endpoint.

**Should `ROLE_SERVICE` be finer-grained?**
All internal service calls share one `ROLE_SERVICE` authority. This means any service that knows `SERVICE_SECRET` can call any internal endpoint. A future improvement would be per-service secrets or a claims-based approach so that organisation-service can only call the specific delete endpoint on event-service, not any `SERVICE`-protected endpoint across the platform.
