package com.holidayplanner.organizationservice.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the outbox relay: at-least-once delivery (mark processed only after the broker ack),
 * a no-op when nothing is pending, and stop-the-batch-on-failure so per-aggregate order is preserved
 * and unsent rows are retried on the next tick.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationOutboxRelayTest {

    @Mock private OrganizationOutboxEventRepository repository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks private OrganizationOutboxRelay relay;

    private OrganizationOutboxEvent pending(String topic, String key, String payload) {
        OrganizationOutboxEvent e = new OrganizationOutboxEvent();
        e.setId(UUID.randomUUID());
        e.setAggregateType("Organization");
        e.setAggregateId(key);
        e.setEventType("OrganizationCreated");
        e.setTopic(topic);
        e.setPartitionKey(key);
        e.setPayload(payload);
        e.setCreatedAt(Instant.now());
        e.setProcessed(false);
        return e;
    }

    @Test
    void publishPendingEvents_marksProcessedAfterBrokerAck() {
        OrganizationOutboxEvent a = pending("topic.a", "k1", "{\"a\":1}");
        OrganizationOutboxEvent b = pending("topic.b", "k2", "{\"b\":2}");
        when(repository.findTop100ByProcessedFalseOrderByCreatedAtAsc()).thenReturn(List.of(a, b));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        relay.publishPendingEvents();

        assertThat(a.isProcessed()).isTrue();
        assertThat(a.getProcessedAt()).isNotNull();
        assertThat(b.isProcessed()).isTrue();
        verify(kafkaTemplate).send("topic.a", "k1", "{\"a\":1}");
        verify(kafkaTemplate).send("topic.b", "k2", "{\"b\":2}");
    }

    @Test
    void publishPendingEvents_whenNothingPending_doesNotTouchKafka() {
        when(repository.findTop100ByProcessedFalseOrderByCreatedAtAsc()).thenReturn(List.of());

        relay.publishPendingEvents();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void publishPendingEvents_stopsBatchOnFirstFailureAndLeavesRemainingUnprocessed() {
        OrganizationOutboxEvent a = pending("topic.a", "k1", "{\"a\":1}");
        OrganizationOutboxEvent b = pending("topic.b", "k2", "{\"b\":2}");
        when(repository.findTop100ByProcessedFalseOrderByCreatedAtAsc()).thenReturn(List.of(a, b));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(eq("topic.a"), anyString(), anyString())).thenReturn(failed);

        relay.publishPendingEvents();

        assertThat(a.isProcessed()).isFalse();
        assertThat(b.isProcessed()).isFalse();
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
        verify(kafkaTemplate, never()).send(eq("topic.b"), anyString(), anyString());
    }
}
