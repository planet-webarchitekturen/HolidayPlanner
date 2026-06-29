package com.holidayplanner.bookingservice.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void publishPendingEventsMarksEventProcessedAfterKafkaAck() {
        OutboxEvent event = event("BookingCreated", "holiday-planner.booking.created");
        when(repository.findTop100ByProcessedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(null));

        new OutboxRelay(repository, kafkaTemplate).publishPendingEvents();

        verify(kafkaTemplate).send(event.getTopic(), event.getPartitionKey(), event.getPayload());
        assertThat(event.isProcessed()).isTrue();
        assertThat(event.getProcessedAt()).isNotNull();
    }

    @Test
    void publishPendingEventsStopsBatchAndKeepsEventPendingWhenKafkaFails() {
        OutboxEvent failed = event("BookingCreated", "holiday-planner.booking.created");
        OutboxEvent next = event("BookingCancelled", "holiday-planner.booking.cancelled");
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));

        when(repository.findTop100ByProcessedFalseOrderByCreatedAtAsc()).thenReturn(List.of(failed, next));
        when(kafkaTemplate.send(failed.getTopic(), failed.getPartitionKey(), failed.getPayload()))
                .thenReturn(failedFuture);

        new OutboxRelay(repository, kafkaTemplate).publishPendingEvents();

        assertThat(failed.isProcessed()).isFalse();
        assertThat(failed.getProcessedAt()).isNull();
        assertThat(next.isProcessed()).isFalse();
        verify(kafkaTemplate, never()).send(next.getTopic(), next.getPartitionKey(), next.getPayload());
    }

    private OutboxEvent event(String eventType, String topic) {
        UUID id = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setAggregateType("Booking");
        event.setAggregateId(id.toString());
        event.setEventType(eventType);
        event.setTopic(topic);
        event.setPartitionKey(id.toString());
        event.setPayload("{\"eventType\":\"" + eventType + "\"}");
        event.setCreatedAt(Instant.now());
        event.setProcessed(false);
        return event;
    }
}
