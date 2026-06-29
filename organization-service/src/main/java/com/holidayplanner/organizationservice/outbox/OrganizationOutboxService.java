package com.holidayplanner.organizationservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Records domain events into the transactional outbox. Callers invoke {@link #record} from within the
 * already-open transaction that mutated the aggregate, so the outbox row commits atomically with the
 * state change. Serialization failures are rethrown so the surrounding transaction rolls back rather
 * than persisting state without its event.
 */
@Service
@RequiredArgsConstructor
public class OrganizationOutboxService {

    private static final String SOURCE = "organization-service";
    private static final String SCHEMA_VERSION = "1";

    private final OrganizationOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void record(String aggregateType, String aggregateId,
                       String eventType, String topic, Object payload) {
        OrganizationOutboxEvent event = new OrganizationOutboxEvent();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setTopic(topic);
        event.setPartitionKey(aggregateId);
        event.setPayload(serialize(eventType, payload));
        event.setCreatedAt(Instant.now());
        event.setProcessed(false);
        outboxEventRepository.save(event);
    }

    private String serialize(String eventType, Object payload) {
        KafkaEnvelope<Object> envelope = new KafkaEnvelope<>(
                eventType, SCHEMA_VERSION, LocalDateTime.now().toString(), SOURCE, payload);
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload for " + eventType, e);
        }
    }
}
