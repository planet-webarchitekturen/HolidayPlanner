package com.holidayplanner.organizationservice.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional Outbox record. Domain events are written to this table inside the SAME database
 * transaction as the state change that produced them; {@link OrganizationOutboxRelay} later publishes
 * unprocessed rows to Kafka. This guarantees an event is never lost (the row is durable once the
 * business transaction commits) and never published for a rolled-back transaction — which the
 * previous "save then {@code kafkaTemplate.send}" approach could not promise.
 */
@Entity
@Table(name = "organization_outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OrganizationOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The aggregate that produced the event, e.g. "Organization". */
    @Column(nullable = false)
    private String aggregateType;

    /** The aggregate's identifier (also used as the Kafka message key). */
    @Column(nullable = false)
    private String aggregateId;

    /** The domain event type, e.g. "OrganizationCreated". */
    @Column(nullable = false)
    private String eventType;

    /** The Kafka topic this event is published to. */
    @Column(nullable = false)
    private String topic;

    /** The Kafka partition key (kept separate so the relay does not have to parse the payload). */
    @Column(nullable = false)
    private String partitionKey;

    /** The fully serialized {@code KafkaEnvelope} JSON, ready to publish as-is. */
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean processed;

    private Instant processedAt;
}
