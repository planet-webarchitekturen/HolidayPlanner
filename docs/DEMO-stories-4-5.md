# Demo Runbook — Stories 4 & 5 (Muhi + Tarik)

Live presentation guide for the **waitlist lifecycle**:

- **Story 5 — Capacity Increase → Waitlist Promotion:** an owner raises a term's capacity and a waitlisted child is promoted automatically, across services, via Kafka.
- **Story 4 — Event Term Cancellation:** a term is cancelled and every booking is cancelled in a Kafka cascade (the nightly scheduler does the same with actor `SYSTEM`).

> Status: verified end-to-end on the live Docker stack. All unit/component tests green (booking-service 55, event-service 23). The scripted flow lives in [`scripts/demo-stories-4-5.sh`](../scripts/demo-stories-4-5.sh).

---

## One-liner to open with
> "We own the **waitlist lifecycle**. When a seat frees up — an owner raises the limit (Story 5) or a term is cancelled (Story 4) — the right children are promoted/cancelled automatically across services, through Kafka. No manual steps."

## Services & ports
| Service | Port | | Service | Port |
|---|---|---|---|---|
| event | 8081 | | payment | 8085 |
| booking | 8082 | | booklet | 8087 |
| identity | 8083 | | notification | 8090 |
| organization | 8084 | | **Kafka UI** | **5001** |

---

## 1. Prep (~10 min before)
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Free port 5001 (Kafka UI) + RAM — stop any OTHER docker projects:
docker stop $(docker ps -q --filter name=hotelreservation) \
            $(docker ps -q --filter name=hotel_reserv) \
            $(docker ps -q --filter name=gravix) 2>/dev/null

docker compose up -d                      # full Holiday Planner stack (Kafka first, then services)

# verify health (all must say "... is running!"):
for s in "8081 events" "8082 bookings" "8083 identity" "8084 organizations" "8085 payments"; do
  set -- $s; echo -n "$2: "; curl -s localhost:$1/api/$2/health; echo
done
open http://localhost:5001                 # Kafka UI on a second screen
```
**Keep Kafka UI visible** — it is your strongest visual. Watch topics fill in real time.

## 2. The live flow
Run the script and narrate, or do the curls manually:
```bash
bash scripts/demo-stories-4-5.sh           # use bash, NOT zsh ($UID/$USER are reserved in zsh)
```
Expected output:
```
Anna books  -> CONFIRMED
Ben books   -> WAITLISTED
── Story 5: raise capacity 1→2 ──
Ben now     -> CONFIRMED       (auto-promoted via Kafka)
── Story 4: cancel term ──
Anna CANCELLED / Ben CANCELLED (cascade via Kafka)
```

## 3. Show cause → effect in Kafka UI
This is the heart of the talk — point at each topic as it happens:

| Step | Topic | Messages | Meaning |
|---|---|---|---|
| Story 5 trigger | `holiday-planner.event.capacity-increased` | 1 | event-service published it; **expand the row** to show `payload.eventTermId`, `addedSlots`, `newMax` |
| Story 5 effect | `holiday-planner.booking.waitlist-promoted` | 1 | **your** `CapacityIncreasedConsumer` promoted Ben and emitted this |
| Story 4 trigger | `holiday-planner.event.term-cancelled` | 1 | the `EventTermCancellationSaga` published it |
| Story 4 effect | `holiday-planner.booking.cancelled` | 1 per booking | booking-service cancelled each booking |

Narrate: *"I never touched Ben's booking. event-service published `CapacityIncreased`; my consumer read it and promoted the oldest waitlisted child — FIFO — then published `WaitlistPromoted` for notifications."*

## 4. The schedulers (Story 4 — don't demo live)
Both jobs are cron-driven (auto-cancel 03:00, day-before 02:15, timezone `Europe/Vienna`), so explain + prove with tests instead:
```bash
mvn test -pl event-service -Dtest='AutoCancelUnderfilledTermsJobTest,DayBeforeNotificationsJobTest'
```
Say: *"The nightly job runs the same saga with actor `SYSTEM`. Timing logic depends on an injected `Clock` bean (pinned to Europe/Vienna), so it's deterministic and unit-tested with `Clock.fixed`."* Show the 7 green tests.

## 5. Safety nets
- **Full automated proof** (also a great opener) — exercises the exact flow incl. the capacity assertion:
  ```bash
  mvn verify -pl e2e-tests -Pe2e            # needs the full stack up
  ```
- **Pre-bake** section 2 before the talk; keep the IDs handy so on stage you only run the two triggers.
- Keep prior `mvn test` output on screen (55 + 23 green).

## 6. Who drives
- **Muhi (booking-service):** bookings, the promotion moment, the consumer + FIFO story.
- **Tarik (event-service):** capacity PATCH, term cancellation, the saga + scheduler/Clock testability + green tests.

## 7. Likely questions
- *Duplicate delivery?* Consumers are idempotent on status; re-promoting a CONFIRMED booking is a no-op (the query only returns WAITLISTED).
- *FIFO — how?* `findByEventTermIdAndStatusOrderByBookedAtAsc` + `.limit(addedSlots)`; oldest `bookedAt` wins.
- *Why a Clock bean?* JVM/container timezones differ; pinning `Europe/Vienna` makes "within 24h"/"tomorrow" deterministic and testable.
- *Kafka send fails?* Currently logged and swallowed — no outbox/DLQ yet (known backlog item).
