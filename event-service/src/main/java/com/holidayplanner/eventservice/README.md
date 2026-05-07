# Event service — notes

## Reasonings

- **CQRS-light:** Commands (`command/`) change state and trigger side effects (Kafka, outbound HTTP); queries (`query/`) are read-only. Easier to test and reason about than one monolithic service class.
- **Ports (`port/`):** Application code depends on interfaces only; `client/` and `kafka/` implement them. Keeps domain rules and use cases independent of RestClient / `KafkaTemplate`.
- **Shared JPA entities:** `Event`, `EventTerm`, `Remark` live in the `shared` module (team convention). DTOs in `dto/` are used on the REST boundary so entities are not exposed as JSON contracts.

## Questions

1. **Schedulers:** Should “day before” and “auto-cancel” use a fixed `ZoneId` (e.g. `Europe/Vienna`) instead of the JVM default?

