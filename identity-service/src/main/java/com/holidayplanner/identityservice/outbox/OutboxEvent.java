package com.holidayplanner.identityservice.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional Outbox record.
 *
 * Domain events are written to this table inside the SAME database transaction
 * as the state change that produced them. A separate relay ({@code OutboxRelay})
 * later reads unprocessed rows and publishes them to Kafka. This guarantees that
 * an event is never lost (and never published for a transaction that rolled back),
 * which a direct "save then publish" sequence cannot guarantee.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The aggregate that produced the event, e.g. "User" or "FamilyMember". */
    @Column(nullable = false)
    private String aggregateType;

    /** The aggregate's identifier (also used as the Kafka message key). */
    @Column(nullable = false)
    private String aggregateId;

    /** The domain event type, e.g. "UserRegistered". */
    @Column(nullable = false)
    private String eventType;

    /** The Kafka topic this event is published to. */
    @Column(nullable = false)
    private String topic;

    /** The Kafka partition key (kept separate so the relay does not parse the payload). */
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
