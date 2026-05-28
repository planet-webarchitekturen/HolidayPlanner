package com.holidayplanner.identityservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayplanner.shared.kafka.KafkaEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Records domain events into the transactional outbox.
 *
 * Callers invoke {@link #record} from within an already-open transaction (the
 * command that mutated the aggregate). The outbox row therefore commits atomically
 * with the state change. Serialization failures are rethrown so the surrounding
 * transaction rolls back rather than persisting state without its event.
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private static final String SOURCE = "identity-service";
    private static final String SCHEMA_VERSION = "1";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void record(String aggregateType, String aggregateId,
                       String eventType, String topic, Object payload) {
        OutboxEvent event = new OutboxEvent();
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
