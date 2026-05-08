# Debug Session Notes — 2026-05-08

## Issue 1: Docker VM Disk Full (Root Cause Found)

**Symptom:** All Docker write operations failed with `no space left on device`. Even image removal and container restarts were blocked. `docker system df` showed only ~2GB of tracked data, yet the filesystem was 100% full.

**Red herring:** The Docker VM's virtual disk limit (`diskSizeMiB`) was already set to 61035 MiB (~60GB) in `settings.json`, and `Docker.raw` was 60GB on disk. Initially suspected the partition resize hadn't applied — but that wasn't the issue.

**Actual root cause:** The identity-service Kafka consumer was throwing a `SerializationException` in a tight loop (timestamp nanosecond precision mismatch: `2026-04-30T17:29:50.222453924` — Jackson's `Instant` deserializer rejects nanoseconds). This filled `docker/containers/<id>/<id>-json.log` to **55.4GB** — completely invisible to `docker system df` because Docker only tracks image layers, volumes, and build cache, not log file growth.

**Fix:** Truncated the log file via `docker run --rm -v /var/lib/docker:/docker alpine truncate -s 0 ...`. Added `max-size: 50m / max-file: 3` log rotation to all services in `docker-compose.yml`.

**Open question:** The Kafka deserialization error itself is unresolved. The identity-service `DomainEvent` uses `java.time.Instant` but published events use `LocalDateTime.now()` which serializes with nanosecond precision. Either the `DomainEvent` field should use `String` for the timestamp, or the Kafka producer should format using `Instant` from the start. This will keep spamming logs (now just capped at 150MB).

---

## Issue 2: Identity-Service POST 401 (Root Cause Found)

**Symptom:** `POST /api/identity/users/register` and `POST /api/identity/auth/login` returned `{"error":"Unauthorized"}` while `GET /api/identity/health` returned 200.

**Investigation path:**
- Confirmed the running image contained the shared module's `JwtAuthenticationFilter` (not the old local one) because sending an *invalid* JWT returned `{"error":"Invalid or expired token"}` — that specific message only exists in the shared module's filter.
- Confirmed the filter is NOT the problem: it only rejects requests that *have* a token and it's invalid. Requests with no token pass straight through.
- The 401 therefore came from Spring Security's `AuthorizationFilter` — meaning the `permitAll()` rules were not matching.
- The image was built at `2026-05-08T00:10:30Z`, which is 11 minutes *before* commit `e1f6191` (00:21 UTC) that added `/api/identity/users/register` and `/api/identity/auth/login` to the `permitAll` list. The image was built from working-directory state that hadn't been committed yet.

**Actual root cause:** The `docker-compose pull` that ran after the Docker Desktop disk resize re-pulled the stale DockerHub image from before the SecurityConfig was fully updated. The running container was missing two `permitAll` rules.

**Fix:** Rebuilt `muhiguezel/identity-service:latest` from current source and pushed to DockerHub.

**Note on Maven:** Local Maven 3.9.11 was defaulting to Java 25 (Homebrew `openjdk`), causing `TypeTag :: UNKNOWN` compile errors. Must use `JAVA_HOME=$(/usr/libexec/java_home -v 21)` for all local Maven builds.

---

## Issue 3: Cross-Org Access Returns 401 Instead of 403

**Symptom:** When an authenticated user tries to update an event belonging to a different organization, the response is `{"error":"Unauthorized"}` (401) rather than `{"error":"Forbidden"}` (403).

**Expected behavior:** Spring Security's `ExceptionTranslationFilter` should distinguish:
- `AuthenticationException` or anonymous user → `authenticationEntryPoint` → 401
- `AccessDeniedException` from authenticated user → `accessDeniedHandler` → 403

**Why it's returning 401:** The SecurityConfig defines a custom `authenticationEntryPoint` but no custom `accessDeniedHandler`. In Spring Security 6, when `@EnableMethodSecurity` is active and a service method throws `AccessDeniedException`, the way the exception propagates through the AOP proxy and then through `ExceptionTranslationFilter` may treat the user as effectively unauthenticated in this context — or the default `accessDeniedHandler` is writing a plain 403 body that gets overridden somewhere.

**Status:** Access IS blocked (the event is not modified). The distinction between 401 and 403 is semantic but matters for proper REST API contracts. To fix: add `.exceptionHandling(e -> e.accessDeniedHandler((req, res, ex) -> { res.setStatus(403); res.setContentType("application/json"); res.getWriter().write("{\"error\":\"Forbidden\"}"); }))` to the SecurityConfig in all services.

---

## Remaining Open Questions

1. **Kafka timestamp mismatch** — Who publishes events with `LocalDateTime` nanoseconds? All services use `LocalDateTime.now()` for the `timestamp` field in `DomainEvent`. The identity-service (and likely others) deserialize it as `Instant`. Should standardize on one type across all services.

2. **Identity-service scanning all shared entities** — The main class has `@EntityScan("com.holidayplanner.shared.model")` which causes `identity_db` to contain tables for bookings, events, payments, etc. This is likely unintentional. The identity-service only needs `User`, `FamilyMember`, and `Caregiver`.

3. **401 vs 403 on cross-org access** — Described above. Affects all services (booking, payment, event). Low security risk (access is blocked) but violates REST semantics.

4. **Identity-service Kafka consumer** — Why does identity-service consume `holiday-planner.booking.cancelled` and `holiday-planner.payment.refunded`? It can't act on them if deserialization fails. Either fix the deserialization or remove the consumer if it's not needed.
